package com.quarenta.calendario.ui.home

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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.NotificationsActive
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
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.ui.AccessCard
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.components.ProgressBar
import com.quarenta.calendario.ui.composeColor
import com.quarenta.calendario.ui.days
import com.quarenta.calendario.ui.localTime
import com.quarenta.calendario.ui.monthLong
import com.quarenta.calendario.ui.startsIn
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.timeRange
import com.quarenta.calendario.ui.weekdayLong
import java.time.Instant
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
) {
    val today = LocalDate.now()
    val now = Instant.now()
    val todays = events.filter { today in it.days() }
    val next = todays.firstOrNull { !it.allDay && it.end.isAfter(now) }
        ?: events.firstOrNull { it.start.isAfter(now) }
    val others = todays.filter { it != next && !it.allDay && it.end.isAfter(now) }.take(3)

    val todayTasks = tasks.filter { it.dueEpochDay == today.toEpochDay() }
    val doneToday = todayTasks.count { it.done }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.padding(start = 4.dp, top = 20.dp, bottom = 4.dp)) {
            Text(weekdayLong(today).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bento.RedText, letterSpacing = 0.8.sp)
            Text("${today.dayOfMonth} de ${monthLong(today)}", style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
        }

        if (!hasCalendarAccess) AccessCard(onRequestAccess)

        // Next event: the biggest tile.
        BentoTile(Modifier.fillMaxWidth(), onClick = { if (next != null) onOpenEvent(next) else onOpenAgenda() }) {
            if (next == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Rounded.CalendarMonth, Bento.Blue, 48.dp, 26.dp)
                    Text("Sem mais compromissos hoje", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(Icons.Rounded.CalendarMonth, next.composeColor(), 48.dp, 26.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            listOfNotNull("Próximo", startsIn(next, now)).joinToString(" · "),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Bento.BlueText,
                        )
                        Text(next.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(next.timeRange(), fontSize = 14.sp, color = Bento.Secondary)
                        next.location?.let { Text(it, fontSize = 14.sp, color = Bento.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    if (!next.allDay) {
                        Text(next.start.localTime(), fontSize = 44.sp, fontWeight = FontWeight.ExtraBold, color = next.composeColor(), letterSpacing = (-1.2).sp)
                    }
                }
                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        others.forEach { e ->
                            Text(
                                "${e.start.localTime()} ${e.title}",
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(e.composeColor().copy(alpha = 0.14f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoTile(Modifier.weight(1f).height(170.dp), onClick = onOpenTasks) {
                IconTile(Icons.Rounded.Check, Bento.Green)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$doneToday", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                    Text(" / ${todayTasks.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary, modifier = Modifier.padding(bottom = 6.dp))
                }
                Text("Tarefas feitas hoje", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Bento.Secondary)
                Spacer(Modifier.weight(1f))
                ProgressBar(if (todayTasks.isEmpty()) 0f else doneToday / todayTasks.size.toFloat(), Bento.Green)
            }
            BentoTile(Modifier.weight(1f).height(170.dp), onClick = onOpenAgenda) {
                MiniMonth(today, events)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BentoTile(Modifier.weight(1f).height(140.dp), onClick = onOpenBento) {
                IconTile(Icons.Rounded.Groups, Bento.Orange)
                Spacer(Modifier.weight(1f))
                Text("Pessoas", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Fichas ao estilo Bento", fontSize = 12.sp, color = Bento.Secondary)
            }
            BentoTile(Modifier.weight(1f).height(140.dp), onClick = onOpenBento) {
                IconTile(Icons.Rounded.Folder, Bento.Purple)
                Spacer(Modifier.weight(1f))
                Text("Projetos", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Em breve", fontSize = 12.sp, color = Bento.Secondary)
            }
        }

        val pending = tasks.filter { !it.done && it.dueEpochDay != null && it.dueEpochDay <= today.toEpochDay() }
        BentoTile(Modifier.fillMaxWidth(), color = Bento.Indigo, onClick = onOpenTasks) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(Icons.Rounded.NotificationsActive, Color.White.copy(alpha = 0.22f))
                Column {
                    Text(
                        if (pending.isEmpty()) "Nada pendente" else "${pending.size} por fazer hoje",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    )
                    pending.firstOrNull()?.let {
                        Text("Próximo: ${it.title}", fontSize = 13.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
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
                Box(Modifier.weight(1f).height(15.dp), contentAlignment = Alignment.Center) {
                    if (d != null) {
                        if (d == today) {
                            Box(Modifier.size(15.dp).clip(CircleShape).background(Bento.Red), contentAlignment = Alignment.Center) {
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
