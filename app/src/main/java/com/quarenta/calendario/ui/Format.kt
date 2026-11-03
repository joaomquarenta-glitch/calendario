package com.quarenta.calendario.ui

import androidx.compose.ui.graphics.Color
import com.quarenta.calendario.data.CalendarEvent
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val PT: Locale = Locale.forLanguageTag("pt-PT")
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", PT)

fun Instant.localTime(): String = timeFmt.format(atZone(ZoneId.systemDefault()))
fun Instant.localDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

fun CalendarEvent.timeRange(): String =
    if (allDay) "Todo o dia" else "${start.localTime()} – ${end.localTime()}"

fun CalendarEvent.composeColor(): Color = Color(color).copy(alpha = 1f)

/** Days an event covers, so multi-day events show on each of their days. */
fun CalendarEvent.days(): List<LocalDate> {
    val first = start.localDate()
    val lastInstant = if (end.isAfter(start)) end.minusMillis(1) else end
    val last = lastInstant.localDate()
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
}

fun weekdayLong(date: LocalDate): String = date.dayOfWeek.getDisplayName(TextStyle.FULL, PT)
fun weekdayShort(date: LocalDate): String =
    date.dayOfWeek.getDisplayName(TextStyle.SHORT, PT).replace(".", "").replaceFirstChar { it.uppercase() }
fun monthLong(date: LocalDate): String = date.month.getDisplayName(TextStyle.FULL_STANDALONE, PT)
fun monthShort(date: LocalDate): String = date.month.getDisplayName(TextStyle.SHORT, PT).replace(".", "")

fun dayHeading(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val base = "${weekdayLong(date)}, ${date.dayOfMonth} ${monthShort(date)}"
    return when (date) {
        today -> "Hoje · $base"
        today.plusDays(1) -> "Amanhã · $base"
        else -> base.replaceFirstChar { it.uppercase() }
    }
}

/** "em 25 min", "em 2 h", "a decorrer" */
fun startsIn(event: CalendarEvent, now: Instant = Instant.now()): String? {
    if (event.allDay) return null
    if (!now.isBefore(event.start) && now.isBefore(event.end)) return "a decorrer"
    if (now.isAfter(event.start)) return null
    val mins = Duration.between(now, event.start).toMinutes()
    return when {
        mins < 1 -> "agora"
        mins < 60 -> "em $mins min"
        mins < 60 * 24 -> "em ${mins / 60} h"
        else -> null
    }
}
