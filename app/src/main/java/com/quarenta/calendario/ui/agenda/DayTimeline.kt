package com.quarenta.calendario.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.ui.composeColor
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.timeRange
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Height of one hour on the hour-by-hour day. */
val HourHeight: Dp = 64.dp
private val LabelWidth = 48.dp

/**
 * The day from 0h to 24h, appointments drawn at their real time and length. A long press picks
 * one up; dragging it up or down moves it in quarter hours. Tapping an empty hour creates one there.
 */
@Composable
fun DayTimeline(
    day: LocalDate,
    events: List<CalendarEvent>,
    onOpenEvent: (CalendarEvent) -> Unit,
    onMoveEvent: (CalendarEvent, Int) -> Unit,
    onNewAt: (LocalTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val allDay = events.filter { it.allDay }
    val timed = events.filter { !it.allDay }.mapNotNull { e -> DayLayout.span(e.start, e.end, day, zone)?.let { e to it } }
    val slots = remember(timed) { DayLayout.place(timed.map { it.second }) }
    val minutePx = with(LocalDensity.current) { HourHeight.toPx() / 60f }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        allDay.forEach { e ->
            val color = e.composeColor()
            Row(
                Modifier.fillMaxWidth().padding(start = LabelWidth).clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.22f)).clickable { onOpenEvent(e) }.padding(horizontal = 10.dp, vertical = 6.dp),
            ) { Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }

        BoxWithConstraints(Modifier.fillMaxWidth().height(HourHeight * 24)) {
            val laneWidth = maxWidth - LabelWidth
            // The hour lines; tapping an empty hour starts a new appointment at that time.
            for (h in 0 until 24) {
                Row(Modifier.offset(y = HourHeight * h).fillMaxWidth().height(HourHeight)) {
                    Text(
                        DayLayout.label(h * 60), fontSize = 11.sp, color = Bento.Secondary,
                        modifier = Modifier.width(LabelWidth).offset(y = (-7).dp),
                    )
                    Box(
                        Modifier.weight(1f).height(HourHeight).clickable { onNewAt(LocalTime.of(h, 0)) }
                            .semantics { contentDescription = "Novo compromisso às ${h}h" },
                    ) { Box(Modifier.fillMaxWidth().height(1.dp).background(Bento.Hairline)) }
                }
            }

            timed.forEachIndexed { i, (event, span) ->
                val slot = slots[i]
                var drag by remember(event.id, event.instanceBegin) { mutableStateOf(0f) }
                var lifted by remember(event.id, event.instanceBegin) { mutableStateOf(false) }
                val duration = span.end - span.start
                val drawn = maxOf(duration, DayLayout.MIN_DRAWN)
                val preview = if (lifted) DayLayout.moved(span.start, duration, drag / minutePx) else span.start
                val width = laneWidth / slot.columns
                val color = event.composeColor()
                Column(
                    Modifier
                        .zIndex(if (lifted) 2f else 1f)
                        .offset(x = LabelWidth + width * slot.column, y = HourHeight * (preview / 60f))
                        .width(width).height(HourHeight * (drawn / 60f))
                        .padding(end = 2.dp, bottom = 1.dp)
                        .then(if (lifted) Modifier.shadow(10.dp, RoundedCornerShape(10.dp)) else Modifier)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Bento.Tile)
                        .background(color.copy(alpha = if (lifted) 0.45f else 0.28f))
                        .then(
                            if (event.canEdit) Modifier.pointerInput(event.id, event.instanceBegin) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { lifted = true; drag = 0f },
                                    onDragEnd = {
                                        val to = DayLayout.moved(span.start, duration, drag / minutePx)
                                        lifted = false; drag = 0f
                                        if (to != span.start) onMoveEvent(event, to - span.start)
                                    },
                                    onDragCancel = { lifted = false; drag = 0f },
                                    onDrag = { change, amount -> change.consume(); drag += amount.y },
                                )
                            } else Modifier,
                        )
                        .clickable { onOpenEvent(event) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (lifted) DayLayout.label(preview) + " – " + DayLayout.label(preview + duration) else event.timeRange(),
                        fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Bento.BlueText, maxLines = 1,
                    )
                    Text(event.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink, maxLines = if (drawn >= 60) 2 else 1, overflow = TextOverflow.Ellipsis)
                }
            }

            // Now: a red line across today.
            if (day == LocalDate.now()) {
                var now by remember { mutableStateOf(LocalTime.now()) }
                LaunchedEffect(Unit) { while (true) { delay(60_000); now = LocalTime.now() } }
                val y = HourHeight * ((now.hour * 60 + now.minute) / 60f)
                Box(Modifier.zIndex(3f).offset(x = LabelWidth - 4.dp, y = y - 4.dp).size(8.dp).clip(CircleShape).background(Bento.Red))
                Box(Modifier.zIndex(3f).offset(x = LabelWidth, y = y - 1.dp).width(laneWidth).height(2.dp).background(Bento.Red))
            }
        }
    }
}
