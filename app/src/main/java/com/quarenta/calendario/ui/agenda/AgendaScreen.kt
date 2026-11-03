package com.quarenta.calendario.ui.agenda

import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.ui.AccessCard
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.components.SectionLabel
import com.quarenta.calendario.ui.composeColor
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.days
import com.quarenta.calendario.ui.startsIn
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.timeRange
import com.quarenta.calendario.ui.weekdayShort
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private sealed interface AgendaRow {
    data class Header(val date: LocalDate) : AgendaRow
    data class Event(val event: CalendarEvent, val date: LocalDate) : AgendaRow
    data class TaskRow(val task: Task) : AgendaRow
    data class Empty(val date: LocalDate) : AgendaRow
}

@Composable
fun AgendaScreen(
    events: List<CalendarEvent>,
    tasks: List<Task>,
    hasCalendarAccess: Boolean,
    onRequestAccess: () -> Unit,
    onToggleTask: (Task) -> Unit,
) {
    val context = LocalContext.current
    val today = LocalDate.now()
    val byDay = events.flatMap { e -> e.days().map { it to e } }.groupBy({ it.first }, { it.second })
    val tasksByDay = tasks.filter { it.dueEpochDay != null }.groupBy { LocalDate.ofEpochDay(it.dueEpochDay!!) }

    val rows = buildList {
        for (offset in 0L until 21L) {
            val date = today.plusDays(offset)
            val dayEvents = byDay[date].orEmpty().sortedWith(compareBy({ !it.allDay }, { it.start }))
            val dayTasks = tasksByDay[date].orEmpty()
            if (dayEvents.isEmpty() && dayTasks.isEmpty() && offset > 1) continue
            add(AgendaRow.Header(date))
            if (dayEvents.isEmpty() && dayTasks.isEmpty()) add(AgendaRow.Empty(date))
            dayEvents.forEach { add(AgendaRow.Event(it, date)) }
            dayTasks.forEach { add(AgendaRow.TaskRow(it)) }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Agenda", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { shareDay(context, today, byDay[today].orEmpty()) }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Partilhar o dia de hoje", tint = Bento.Blue)
                }
            }
        }
        item { WeekStrip(today, byDay) }
        if (!hasCalendarAccess) item { AccessCard(onRequestAccess) }

        items(rows, key = { row ->
            when (row) {
                is AgendaRow.Header -> "h${row.date}"
                is AgendaRow.Event -> "e${row.event.id}-${row.event.start}-${row.date}"
                is AgendaRow.TaskRow -> "t${row.task.id}"
                is AgendaRow.Empty -> "x${row.date}"
            }
        }) { row ->
            when (row) {
                is AgendaRow.Header -> SectionLabel(dayHeading(row.date, today))
                is AgendaRow.Empty -> Text("Dia livre", modifier = Modifier.padding(start = 4.dp), color = Bento.Secondary, fontSize = 14.sp)
                is AgendaRow.Event -> EventCard(row.event, row.date == today) {
                    val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, row.event.id)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                }
                is AgendaRow.TaskRow -> TaskCard(row.task) { onToggleTask(row.task) }
            }
        }
    }
}

@Composable
private fun WeekStrip(today: LocalDate, byDay: Map<LocalDate, List<CalendarEvent>>) {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        for (i in 0 until 7) {
            val d = monday.plusDays(i.toLong())
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(weekdayShort(d), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Bento.Secondary)
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(if (d == today) Bento.Red else Bento.Tile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${d.dayOfMonth}", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = if (d == today) Color.White else Bento.Ink)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
                    byDay[d].orEmpty().take(3).forEach {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(it.composeColor()))
                    }
                }
            }
        }
    }
}

@Composable
private fun EventCard(event: CalendarEvent, isToday: Boolean, onClick: () -> Unit) {
    val color = event.composeColor()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Bento.Tile)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Icons.Rounded.CalendarMonth, color, 52.dp, 28.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val soon = if (isToday) startsIn(event) else null
                Text(
                    listOfNotNull(event.timeRange(), soon).joinToString(" · "),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bento.BlueText,
                )
                Text(event.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                event.location?.let { Text(it, fontSize = 14.sp, color = Bento.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
        event.description?.let {
            Text(it, fontSize = 14.sp, color = Color(0xFF3A3A3C), maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        }
        Text(event.calendarName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Bento.Secondary)
    }
}

@Composable
private fun TaskCard(task: Task, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Bento.Tile)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TaskCheck(task.done)
        Column {
            Text(
                task.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) Bento.Secondary else Bento.Ink,
            )
            Text("Tarefa", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Bento.GreenText)
        }
    }
}

@Composable
fun TaskCheck(done: Boolean) {
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(if (done) Modifier.background(Bento.Green) else Modifier.border(2.5.dp, Bento.Green, CircleShape)),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

/** Shares the day as plain text, ready for WhatsApp, email or SMS. */
private fun shareDay(context: android.content.Context, day: LocalDate, events: List<CalendarEvent>) {
    val body = buildString {
        appendLine(dayHeading(day))
        if (events.isEmpty()) appendLine("Dia livre")
        events.sortedBy { it.start }.forEach { e ->
            append("• ").append(e.timeRange()).append("  ").append(e.title)
            e.location?.let { append(" (").append(it).append(")") }
            appendLine()
        }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
    context.startActivity(Intent.createChooser(send, "Partilhar agenda"))
}
