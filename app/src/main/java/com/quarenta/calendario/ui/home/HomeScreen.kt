package com.quarenta.calendario.ui.home

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import com.quarenta.calendario.data.ContactFormat
import com.quarenta.calendario.reminders.PersonMatch
import com.quarenta.calendario.ui.agenda.Highlights
import com.quarenta.calendario.ui.agenda.withoutName
import com.quarenta.calendario.ui.localDate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.ui.AccessCard
import com.quarenta.calendario.ui.bento.Avatar
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.composeColor
import com.quarenta.calendario.ui.days
import com.quarenta.calendario.ui.localTime
import com.quarenta.calendario.ui.monthLong
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.weekdayLong
import java.time.LocalDate

@Composable
fun HomeScreen(
    events: List<CalendarEvent>,
    tasks: List<Task>,
    hasCalendarAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenBento: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit = {},
    people: List<Person> = emptyList(),
    onOpenPerson: (Person) -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenReminders: () -> Unit = {},
    photoFor: suspend (Person) -> Bitmap? = { null },
    birthYearFor: suspend (Person) -> Int? = { null },
) {
    val today = LocalDate.now()
    val nowMs = System.currentTimeMillis()
    val next = remember(events) {
        Highlights.upcoming(events, nowMs, endMs = { it.end.toEpochMilli() }, startMs = { it.start.toEpochMilli() }, max = 1).firstOrNull()
    }
    val person = remember(next, people) {
        next?.let { e -> PersonMatch.inTitle(e.title, people.map { it.id to it.name })?.let { id -> people.firstOrNull { it.id == id } } }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(weekdayLong(today).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bento.RedText, letterSpacing = 0.8.sp)
                Text("${today.dayOfMonth} de ${monthLong(today)}", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, color = Bento.Ink)
            }
            // Full-screen reminders: on/off, permissions, colour.
            androidx.compose.material3.IconButton(
                onClick = onOpenReminders,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile),
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.NotificationsActive, contentDescription = "Lembretes", tint = Bento.Orange,
                )
            }
            Spacer(Modifier.width(8.dp))
            // Colours of the app: system, light, black or pastel.
            androidx.compose.material3.IconButton(
                onClick = onOpenAppearance,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile),
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Rounded.Palette, contentDescription = "Cores da app", tint = Bento.Purple,
                )
            }
        }

        if (!hasCalendarAccess) AccessCard(onRequestAccess)

        // Four big blocks, like Apple's widgets: the next event with the person's photo,
        // the month, the favourites and the people.
        NextBlock(next, person, photoFor, birthYearFor, today) { if (next != null) onOpenEvent(next) else onOpenAgenda() }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoTile(Modifier.weight(1f).aspectRatio(1f), onClick = onOpenAgenda) {
                MiniMonth(today, events)
            }
            val favourites = people.filter { it.starred }
            // One favourite opens straight to their card; several open the Pessoas tab, where they sit on top.
            BentoTile(
                Modifier.weight(1f).aspectRatio(1f),
                onClick = { favourites.singleOrNull()?.let(onOpenPerson) ?: onOpenBento() },
            ) {
                Text("FAVORITOS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Orange, letterSpacing = 0.8.sp)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when (favourites.size) {
                        0 -> IconTile(Icons.Rounded.Star, Bento.Yellow, 64.dp, 36.dp)
                        1 -> Avatar(favourites.first(), 96.dp, full = true)
                        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            favourites.take(4).chunked(2).forEach { pair ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { pair.forEach { Avatar(it, 50.dp) } }
                            }
                        }
                    }
                }
            }
        }

        BentoTile(Modifier.fillMaxWidth().height(116.dp), onClick = onOpenBento) {
            Text("PESSOAS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.BlueText, letterSpacing = 0.8.sp)
            Spacer(Modifier.weight(1f))
            if (people.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Rounded.Groups, Bento.Orange)
                    Text("Fichas de quem é importante", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                // A row of faces, the starred ones first.
                val faces = people.sortedByDescending { it.starred }.take(6)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(52.dp)) {
                        faces.forEachIndexed { i, p ->
                            Box(
                                Modifier.padding(start = (i * 38).dp).size(52.dp).clip(CircleShape)
                                    .background(Bento.Tile).padding(2.dp),
                            ) { Avatar(p, 48.dp) }
                        }
                    }
                    Text("${people.size}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Ink)
                }
            }
        }
    }
}

/** The next thing on the calendar, as one big card: the person's photo at full resolution when there is one. */
@Composable
private fun NextBlock(
    next: CalendarEvent?,
    person: Person?,
    photoFor: suspend (Person) -> Bitmap?,
    birthYearFor: suspend (Person) -> Int?,
    today: LocalDate,
    onOpen: () -> Unit,
) {
    val color = next?.composeColor() ?: Bento.Blue
    val photo by produceState<Bitmap?>(null, person?.id) { value = person?.let { photoFor(it) } }
    val birthday = next != null && Highlights.isBirthday(next.title, next.sources)
    val birthYear by produceState<Int?>(null, person?.id, birthday) {
        value = if (birthday && person != null) birthYearFor(person) else null
    }
    Box(
        Modifier.fillMaxWidth().aspectRatio(1.05f).clip(RoundedCornerShape(30.dp)).clickable(onClick = onOpen),
    ) {
        val bitmap = photo
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(), contentDescription = person?.name,
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(lerp(color, Color.White, 0.25f), color, lerp(color, Color.Black, 0.45f))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                val initials = person?.name?.let(ContactFormat::initials).orEmpty()
                Box(
                    Modifier.padding(bottom = 70.dp).size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (initials.isNotEmpty()) {
                        Text(initials, color = Color.White, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Icon(
                            if (birthday) Icons.Rounded.Cake else Icons.Rounded.CalendarMonth,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(60.dp),
                        )
                    }
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (next == null) {
                Text("Sem compromissos", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Toca para abrir a agenda", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            } else {
                val day = next.start.localDate()
                val whenText = (Highlights.shortDay(day, today) +
                    (if (next.allDay) "" else " · ${next.start.localTime()}")).replaceFirstChar { it.uppercase() }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        if (birthday) {
                            Text(
                                Highlights.birthdayLabel(birthYear, day, today).replaceFirstChar { it.uppercase() },
                                color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold,
                            )
                        } else {
                            Text(
                                if (bitmap != null) withoutName(next.title, person) else next.title,
                                color = Color.White, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Text(whenText, color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Abrir", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.24f))
                            .clickable(onClick = onOpen).padding(horizontal = 18.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniMonth(today: LocalDate, events: List<CalendarEvent>) {
    val first = today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value - 1 // Monday first
    val busy = events.flatMap { it.days() }.toSet()
    Text(monthLong(today).uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.RedText, letterSpacing = 0.8.sp)
    Spacer(Modifier.height(6.dp))
    val cells = List(lead) { null } + (1..today.lengthOfMonth()).map { first.withDayOfMonth(it) }
    Row { listOf("S", "T", "Q", "Q", "S", "S", "D").forEach { Text(it, Modifier.weight(1f), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary, textAlign = TextAlign.Center) } }
    cells.chunked(7).forEach { week ->
        Row(Modifier.padding(top = 1.dp)) {
            for (i in 0 until 7) {
                val d = week.getOrNull(i)
                Box(Modifier.weight(1f).height(14.dp), contentAlignment = Alignment.Center) {
                    if (d != null) {
                        if (d == today) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(Bento.Red), contentAlignment = Alignment.Center) {
                                Text("${d.dayOfMonth}", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        } else {
                            Text(
                                "${d.dayOfMonth}", fontSize = 10.sp,
                                fontWeight = if (d in busy) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (d in busy) Bento.BlueText else Bento.Ink,
                            )
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.width(0.dp))
}
