package com.quarenta.calendario.ui.agenda

import com.quarenta.calendario.ui.components.asHeading
import com.quarenta.calendario.ui.Speech
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.quarenta.calendario.ui.components.taskToggle
import com.quarenta.calendario.ui.components.LocalBars
import android.content.Intent
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.quarenta.calendario.ui.event.icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ViewCarousel
import android.graphics.Bitmap
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.ui.weekdayLong
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.quarenta.calendario.ui.monthLong
import com.quarenta.calendario.ui.startsIn
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.timeRange
import com.quarenta.calendario.ui.weekdayShort
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import java.time.YearMonth
import androidx.compose.material.icons.rounded.Search
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
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onNewEvent: () -> Unit,
    people: List<Person> = emptyList(),
    photoFor: suspend (Person) -> Bitmap? = { null },
    birthYearFor: suspend (Person) -> Int? = { null },
    onSearch: () -> Unit = {},
    weather: Map<LocalDate, com.quarenta.calendario.data.DayWeather> = emptyMap(),
    weatherAsk: Boolean = false,
    onAllowWeather: () -> Unit = {},
    onDeclineWeather: () -> Unit = {},
    onMoveEvent: (CalendarEvent, Int) -> Unit = { _, _ -> },
    onNewAt: (LocalDate, java.time.LocalTime) -> Unit = { _, _ -> },
) {
    // First the big cards; "Ver todos os compromissos" opens the full list.
    var showAll by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showAll) { showAll = false }
    if (!showAll) {
        HighlightsPage(
            events = events, people = people, photoFor = photoFor, birthYearFor = birthYearFor, hasCalendarAccess = hasCalendarAccess,
            onRequestAccess = onRequestAccess, onOpenEvent = onOpenEvent, onNewEvent = onNewEvent,
            onShowAll = { showAll = true },
            onSearch = onSearch,
        )
        return
    }
    val context = LocalContext.current
    val today = LocalDate.now()
    val listState = rememberLazyListState()
    // The list of days, or one day hour by hour.
    var hours by rememberSaveable { mutableStateOf(false) }
    // Where the hour-by-hour day sits in the list: title, week, then the optional cards.
    val timelineIndex = 3 + (if (!hasCalendarAccess) 1 else 0) + (if (weatherAsk) 1 else 0)
    val hourPx = with(androidx.compose.ui.platform.LocalDensity.current) { HourHeight.roundToPx() }
    LaunchedEffect(selectedDate, hours) {
        if (!hours) { listState.scrollToItem(0); return@LaunchedEffect }
        // Open the day around now (today) or the first appointment, a little before it.
        val first = events.filter { !it.allDay && selectedDate in it.days() }.minOfOrNull { it.start }
        val hour = when {
            selectedDate == today -> java.time.LocalTime.now().hour
            first != null -> first.atZone(java.time.ZoneId.systemDefault()).let { if (it.toLocalDate() == selectedDate) it.hour else 0 }
            else -> 8
        }
        listState.scrollToItem(timelineIndex, ((hour - 1).coerceAtLeast(0)) * hourPx)
    }
    val byDay = events.flatMap { e -> e.days().map { it to e } }.groupBy({ it.first }, { it.second })
    val tasksByDay = tasks.filter { it.dueEpochDay != null }.groupBy { LocalDate.ofEpochDay(it.dueEpochDay!!) }

    val rows = buildList {
        for (offset in 0L until 21L) {
            val date = selectedDate.plusDays(offset)
            val dayEvents = byDay[date].orEmpty().sortedWith(compareBy({ !it.allDay }, { it.start }))
            val dayTasks = tasksByDay[date].orEmpty()
            if (dayEvents.isEmpty() && dayTasks.isEmpty() && offset > 1) continue
            add(AgendaRow.Header(date))
            if (dayEvents.isEmpty() && dayTasks.isEmpty()) add(AgendaRow.Empty(date))
            dayEvents.forEach { add(AgendaRow.Event(it, date)) }
            dayTasks.forEach { add(AgendaRow.TaskRow(it)) }
        }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = LocalBars.current.calculateTopPadding(),
            bottom = 96.dp + LocalBars.current.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Agenda", style = MaterialTheme.typography.displaySmall, modifier = Modifier.asHeading().weight(1f))
                IconButton(onClick = onSearch) {
                    Icon(Icons.Rounded.Search, contentDescription = "Procurar compromissos", tint = Bento.Blue)
                }
                IconButton(onClick = { hours = !hours }) {
                    Icon(
                        if (hours) Icons.Rounded.ViewAgenda else Icons.Rounded.Schedule,
                        contentDescription = if (hours) "Ver lista de dias" else "Ver o dia por horas", tint = Bento.Blue,
                    )
                }
                IconButton(onClick = { showAll = false }) {
                    Icon(Icons.Rounded.ViewCarousel, contentDescription = "Ver em destaque", tint = Bento.Blue)
                }
                if (selectedDate != today) {
                    TextButton(onClick = { onSelectDate(today) }) { Text("Hoje", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
                IconButton(onClick = { shareDay(context, selectedDate, byDay[selectedDate].orEmpty()) }) {
                    Icon(Icons.Rounded.Share, contentDescription = "Partilhar este dia", tint = Bento.Blue)
                }
            }
        }
        item { WeekStrip(today, selectedDate, byDay, onSelectDate) }
        if (!hasCalendarAccess) item { AccessCard(onRequestAccess) }
        if (weatherAsk) item { WeatherAskCard(onAllowWeather, onDeclineWeather) }

        if (hours) {
            item(key = "timeline-head") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    SectionLabel(dayHeading(selectedDate, today), Modifier.weight(1f))
                    weather[selectedDate]?.let { Text(it.short, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary, modifier = Modifier.padding(end = 6.dp, bottom = 2.dp)) }
                }
            }
            item(key = "timeline") {
                DayTimeline(
                    day = selectedDate,
                    events = byDay[selectedDate].orEmpty(),
                    onOpenEvent = onOpenEvent,
                    onMoveEvent = onMoveEvent,
                    onNewAt = { t -> onNewAt(selectedDate, t) },
                )
            }
            item(key = "timeline-hint") {
                Text(
                    "Toca numa hora vazia para marcar. Carrega sem largar num compromisso e arrasta para mudar a hora.",
                    fontSize = 13.sp, color = Bento.Secondary, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            return@LazyColumn
        }

        items(rows, key = { row ->
            when (row) {
                is AgendaRow.Header -> "h${row.date}"
                is AgendaRow.Event -> "e${row.event.id}-${row.event.start}-${row.date}"
                is AgendaRow.TaskRow -> "t${row.task.id}"
                is AgendaRow.Empty -> "x${row.date}"
            }
        }) { row ->
            when (row) {
                is AgendaRow.Header -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    SectionLabel(dayHeading(row.date, today), Modifier.weight(1f))
                    weather[row.date]?.let { w ->
                        Text(
                            w.short, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary,
                            modifier = Modifier.padding(end = 6.dp, bottom = 2.dp),
                        )
                    }
                }
                is AgendaRow.Empty -> com.quarenta.calendario.ui.components.EmptyState(com.quarenta.calendario.ui.components.EmptyArt.FreeDay, "Dia livre", "Nada marcado. Aproveita.", compact = true)
                is AgendaRow.Event -> EventCard(row.event, row.date == today) { onOpenEvent(row.event) }
                is AgendaRow.TaskRow -> TaskCard(row.task) { onToggleTask(row.task) }
            }
        }
    }
    FloatingActionButton(
        onClick = onNewEvent,
        containerColor = Bento.Blue,
        contentColor = Bento.OnBlue,
        shape = CircleShape,
        modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = LocalBars.current.calculateBottomPadding()).padding(20.dp).size(60.dp),
    ) { Icon(Icons.Rounded.Add, contentDescription = "Novo compromisso", modifier = Modifier.size(30.dp)) }
    }
}

/** Offers, once, to show the weather beside each day. "Agora não" hides it for good. */
@Composable
private fun WeatherAskCard(onAllow: () -> Unit, onDecline: () -> Unit) {
    com.quarenta.calendario.ui.components.BentoTile(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("⛅", fontSize = 34.sp)
            Column(Modifier.weight(1f)) {
                Text("Ver o tempo de cada dia", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
                Text("Só a zona aproximada onde estás, para a previsão.", fontSize = 13.sp, color = Bento.Secondary)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDecline) { Text("Agora não", color = Bento.Secondary) }
            TextButton(onClick = onAllow) { Text("Mostrar", fontWeight = FontWeight.Bold, color = Bento.BlueText) }
        }
    }
}

@Composable
private fun WeekStrip(
    today: LocalDate,
    selected: LocalDate,
    byDay: Map<LocalDate, List<CalendarEvent>>,
    onSelect: (LocalDate) -> Unit,
) {
    // Tap the month name to open the whole month, tap it again to fold back to the week. Swipe to move.
    var monthOpen by rememberSaveable { mutableStateOf(false) }
    val monday = selected.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val shownMonth = if (monthOpen) YearMonth.from(selected) else YearMonth.from(monday)
    fun step(forward: Boolean) {
        if (monthOpen) {
            val to = if (forward) shownMonth.plusMonths(1) else shownMonth.minusMonths(1)
            onSelect(MonthMath.moveSelection(selected, to, today))
        } else {
            onSelect(if (forward) selected.plusWeeks(1) else selected.minusWeeks(1))
        }
    }
    var drag by remember { mutableStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(spring(dampingRatio = 0.85f, stiffness = 500f))
            .pointerInput(monthOpen, selected) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (kotlin.math.abs(drag) > 120f) step(forward = drag < 0) },
                    onHorizontalDrag = { _, amount -> drag += amount },
                )
            },
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { step(forward = false) }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = if (monthOpen) "Mês anterior" else "Semana anterior", tint = Bento.Blue)
            }
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { monthOpen = !monthOpen }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${monthLong(shownMonth.atDay(1)).replaceFirstChar { it.uppercase() }} ${shownMonth.year}",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Bento.Ink,
                )
                Icon(
                    if (monthOpen) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (monthOpen) "Ver só a semana" else "Ver o mês inteiro",
                    tint = Bento.Blue, modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = { step(forward = true) }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = if (monthOpen) "Mês seguinte" else "Semana seguinte", tint = Bento.Blue)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                val d = monday.plusDays(i.toLong())
                Text(
                    weekdayShort(d), modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = if (d.dayOfWeek == today.dayOfWeek) Bento.RedText else Bento.Secondary,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val weeks = if (monthOpen) MonthMath.days(shownMonth).chunked(7) else listOf(List(7) { monday.plusDays(it.toLong()) })
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth().padding(bottom = if (monthOpen) 2.dp else 6.dp)) {
                week.forEach { d ->
                    DayCell(
                        date = d, today = today, selected = selected,
                        outside = monthOpen && YearMonth.from(d) != shownMonth,
                        compact = monthOpen,
                        events = byDay[d].orEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { onSelect(d) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    today: LocalDate,
    selected: LocalDate,
    outside: Boolean,
    compact: Boolean,
    events: List<CalendarEvent>,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val isSel = date == selected
    val isToday = date == today
    val circle = if (compact) 36.dp else 40.dp
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
            // TalkBack hears the whole date and how many events, not just "17".
            .clearAndSetSemantics {
                contentDescription = Speech.day(date, today, events.size)
                this.selected = isSel
            }
            .padding(vertical = if (compact) 2.dp else 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier.size(circle).clip(CircleShape).background(
                when {
                    isSel && isToday -> Bento.Red
                    isSel -> Bento.Ink
                    compact -> Color.Transparent
                    else -> Bento.Tile
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}", fontSize = if (compact) 16.sp else 18.sp,
                fontWeight = if (compact && !isSel && !isToday) FontWeight.SemiBold else FontWeight.ExtraBold,
                color = when {
                    isSel -> Color.White
                    isToday -> Bento.RedText
                    outside -> Bento.Secondary.copy(alpha = 0.5f)
                    else -> Bento.Ink
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
            events.take(3).forEach {
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (outside) it.composeColor().copy(alpha = 0.4f) else it.composeColor()))
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
            // The calendar's colour glows in from the left and fades into the card.
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to color.copy(alpha = 0.26f), 0.55f to color.copy(alpha = 0.06f), 1f to androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val kind = remember(event.title, event.sources) { com.quarenta.calendario.data.EventKinds.of(event.title, event.sources) }
            IconTile(kind.icon(), color, 52.dp, 28.dp)
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
            Text(it, fontSize = 14.sp, color = Bento.Secondary, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
        }
        Text(event.sources.joinToString(" · "), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Bento.Secondary)
    }
}

@Composable
private fun TaskCard(task: Task, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Bento.Tile)
            .taskToggle(task.done, onToggle = onToggle)
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

@Composable
private fun HighlightsPage(
    events: List<CalendarEvent>,
    people: List<Person>,
    photoFor: suspend (Person) -> Bitmap?,
    birthYearFor: suspend (Person) -> Int?,
    hasCalendarAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onNewEvent: () -> Unit,
    onShowAll: () -> Unit,
    onSearch: () -> Unit,
) {
    val now = System.currentTimeMillis()
    val upcoming = remember(events) {
        Highlights.upcoming(events, now, endMs = { it.end.toEpochMilli() }, startMs = { it.start.toEpochMilli() })
    }
    val today = LocalDate.now()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(top = LocalBars.current.calculateTopPadding(), bottom = 24.dp + LocalBars.current.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(weekdayLong(today).uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bento.RedText, letterSpacing = 0.8.sp)
                Text("Agenda", style = MaterialTheme.typography.displaySmall, modifier = Modifier.asHeading())
            }
            IconButton(
                onClick = onSearch,
                modifier = Modifier.padding(end = 8.dp).size(44.dp).clip(CircleShape).background(Bento.Tile),
            ) { Icon(Icons.Rounded.Search, contentDescription = "Procurar compromissos", tint = Bento.Blue) }
            IconButton(
                onClick = onNewEvent,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Blue),
            ) { Icon(Icons.Rounded.Add, contentDescription = "Novo compromisso", tint = Bento.OnBlue) }
        }
        if (!hasCalendarAccess) Box(Modifier.padding(horizontal = 16.dp)) { AccessCard(onRequestAccess) }

        if (upcoming.isEmpty()) {
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(Bento.Tile).padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.quarenta.calendario.ui.components.EmptyState(com.quarenta.calendario.ui.components.EmptyArt.NoEvents, "Sem compromissos marcados", "Toca em + para marcar um.")
            }
        } else {
            AgendaHighlights(upcoming, people, photoFor, onOpenEvent, Modifier.fillMaxWidth(), birthYearFor)
        }

        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(58.dp).clip(RoundedCornerShape(20.dp))
                .background(Bento.Tile).clickable(onClick = onShowAll),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ViewAgenda, contentDescription = null, tint = Bento.Blue)
            Spacer(Modifier.size(10.dp))
            Text("Ver todos os compromissos", color = Bento.BlueText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
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
