package com.quarenta.calendario.ui.agenda

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.data.ContactFormat
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.reminders.PersonMatch
import com.quarenta.calendario.ui.composeColor
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.localDate
import com.quarenta.calendario.ui.startsIn
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.timeRange
import java.time.LocalDate
import kotlin.math.absoluteValue

/**
 * Big cards, one per upcoming event, in the style of Apple's showcase pages:
 * the person's photo filling the card at full resolution, the day and the event on top,
 * and the next cards peeking at the sides.
 */
@Composable
fun AgendaHighlights(
    events: List<CalendarEvent>,
    people: List<Person>,
    photoFor: suspend (Person) -> Bitmap?,
    onOpenEvent: (CalendarEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val namesById = remember(people) { people.map { it.id to it.name } }
    val byId = remember(people) { people.associateBy { it.id } }
    val pager = rememberPagerState { events.size }

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Tall cards like a film poster.
            val side = 36.dp
            val cardWidth = maxWidth - side * 2
            val cardHeight = cardWidth * 1.45f
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = side),
                pageSpacing = 12.dp,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxWidth().height(cardHeight),
                key = { i -> "${events[i].id}-${events[i].start}" },
            ) { page ->
                val event = events[page]
                val person = remember(event.title, namesById) {
                    PersonMatch.inTitle(event.title, namesById)?.let { byId[it] }
                }
                // Cards at the side are a little smaller, like on Apple's pages.
                val offset = (pager.currentPage - page + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val scale = 1f - 0.06f * offset
                        scaleX = scale; scaleY = scale
                        alpha = 1f - 0.25f * offset
                    },
                ) {
                    HighlightCard(event, person, photoFor, today) { onOpenEvent(event) }
                }
            }
        }
        if (events.size > 1) {
            Spacer(Modifier.height(14.dp))
            Dots(count = events.size, current = pager.currentPage)
        }
    }
}

@Composable
private fun HighlightCard(
    event: CalendarEvent,
    person: Person?,
    photoFor: suspend (Person) -> Bitmap?,
    today: LocalDate,
    onOpen: () -> Unit,
) {
    val color = event.composeColor()
    val photo by produceState<Bitmap?>(null, person?.id) { value = person?.let { photoFor(it) } }
    val birthday = Highlights.isBirthday(event.title, event.sources)

    Box(
        Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).clickable(onClick = onOpen),
    ) {
        val bitmap = photo
        if (bitmap != null) {
            Image(
                bitmap.asImageBitmap(), contentDescription = person?.name,
                contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No photo: a rich gradient in the event's colour, with a big symbol.
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(listOf(lerp(color, Color.White, 0.25f), color, lerp(color, Color.Black, 0.45f))),
                ),
                contentAlignment = Alignment.Center,
            ) {
                val initials = person?.name?.let(ContactFormat::initials).orEmpty()
                Box(
                    Modifier.padding(bottom = 120.dp).size(150.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (initials.isNotEmpty()) {
                        Text(initials, color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
                    } else {
                        Icon(
                            if (birthday) Icons.Rounded.Cake else Icons.Rounded.CalendarMonth,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(76.dp),
                        )
                    }
                }
            }
        }
        // Darker towards the bottom so the white text is always readable.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f)),
            ),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val day = event.start.localDate()
            val soon = if (day == today) startsIn(event) else null
            Text(
                listOfNotNull(if (birthday) "Aniversário" else null, dayHeading(day, today), soon).joinToString(" · "),
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Text(
                event.title, color = Color.White, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
            Text(event.timeRange(), color = Color.White.copy(alpha = 0.9f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            event.location?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Abrir", color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White).clickable(onClick = onOpen)
                    .padding(horizontal = 34.dp, vertical = 12.dp),
            )
        }
    }
}

/** Page dots; the current one stretches into a pill, like on Apple's carousels. */
@Composable
private fun Dots(count: Int, current: Int) {
    val shown = minOf(count, 9)
    val window = (current - shown / 2).coerceIn(0, count - shown)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in window until window + shown) {
            val selected = i == current
            val w by animateFloatAsState(if (selected) 26f else 8f, label = "dot")
            Box(
                Modifier.height(8.dp).width(w.dp).clip(RoundedCornerShape(50))
                    .background(if (selected) Bento.Ink else Bento.Secondary.copy(alpha = 0.45f)),
            )
        }
    }
}
