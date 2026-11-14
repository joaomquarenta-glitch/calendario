package com.quarenta.calendario.data

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reads a whole iCalendar feed (a subscribed link: school timetable, club fixtures, a shared
 * iCloud calendar). Every VEVENT becomes one [FeedEvent]; repeating events keep their rule,
 * and single changed occurrences (RECURRENCE-ID) replace the occurrence they change.
 */
object IcsFeed {

    data class FeedEvent(
        val uid: String,
        val title: String,
        val start: Instant,
        val end: Instant,
        val allDay: Boolean,
        val location: String,
        val description: String,
        /** RRULE value without the "RRULE:" prefix, or null for a one-off event. */
        val rrule: String? = null,
        /** Occurrences of [rrule] that do not happen (EXDATE, and those moved by an override). */
        val exdates: List<Instant> = emptyList(),
    )

    /** The calendar's own name (X-WR-CALNAME), to suggest a name for the subscription. */
    fun calendarName(text: String): String? =
        unfold(text).firstOrNull { it.uppercase().startsWith("X-WR-CALNAME") }
            ?.substringAfter(':')?.let { unescape(it).trim() }?.takeIf { it.isNotBlank() }

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<FeedEvent> {
        val masters = mutableListOf<FeedEvent>()
        val overrides = mutableListOf<Pair<FeedEvent, Instant>>()
        var props: MutableList<Triple<String, Map<String, String>, String>>? = null
        var depth = 0 // nested blocks inside a VEVENT (VALARM) are skipped
        for (line in unfold(text)) {
            val upper = line.uppercase()
            when {
                upper == "BEGIN:VEVENT" -> { props = mutableListOf(); depth = 0 }
                props != null && upper.startsWith("BEGIN:") -> depth++
                props != null && upper == "END:VEVENT" -> {
                    build(props, zone)?.let { (event, recurrenceId) ->
                        if (recurrenceId != null) overrides += event to recurrenceId else masters += event
                    }
                    props = null
                }
                props != null && upper.startsWith("END:") -> depth--
                props != null && depth == 0 -> {
                    val colon = line.indexOf(':').takeIf { it > 0 } ?: continue
                    val head = line.substring(0, colon).split(';')
                    val params = head.drop(1).mapNotNull { p ->
                        val eq = p.indexOf('=')
                        if (eq > 0) p.substring(0, eq).uppercase() to p.substring(eq + 1).trim('"') else null
                    }.toMap()
                    props.add(Triple(head[0].uppercase(), params, line.substring(colon + 1)))
                }
            }
        }
        if (overrides.isEmpty()) return masters
        val moved = overrides.groupBy({ it.first.uid }, { it.second })
        return masters.map { m ->
            val extra = moved[m.uid]
            if (m.rrule != null && extra != null) m.copy(exdates = (m.exdates + extra).distinct()) else m
        } + overrides.map { it.first }
    }

    private fun build(props: List<Triple<String, Map<String, String>, String>>, zone: ZoneId): Pair<FeedEvent, Instant?>? {
        fun first(name: String) = props.firstOrNull { it.first == name }
        val dtStart = first("DTSTART") ?: return null
        val allDay = dtStart.second["VALUE"].equals("DATE", true) || dtStart.third.trim().length == 8
        val start = date(dtStart.third, dtStart.second["TZID"], zone) ?: return null
        val end = first("DTEND")?.let { date(it.third, it.second["TZID"], zone) }
            ?: first("DURATION")?.let { runCatching { start.plus(Duration.parse(it.third.trim())) }.getOrNull() }
            ?: if (allDay) start.plus(Duration.ofDays(1)) else start
        if (first("STATUS")?.third?.trim().equals("CANCELLED", true)) return null
        val exdates = props.filter { it.first == "EXDATE" }.flatMap { p ->
            p.third.split(',').mapNotNull { date(it, p.second["TZID"], zone) }
        }
        val recurrenceId = first("RECURRENCE-ID")?.let { date(it.third, it.second["TZID"], zone) }
        val event = FeedEvent(
            uid = first("UID")?.third?.trim().orEmpty().ifBlank { "${first("SUMMARY")?.third}-$start" },
            title = unescape(first("SUMMARY")?.third.orEmpty()).trim().ifBlank { "(Sem título)" },
            start = start,
            end = if (end.isAfter(start)) end else if (allDay) start.plus(Duration.ofDays(1)) else start,
            allDay = allDay,
            location = unescape(first("LOCATION")?.third.orEmpty()).trim(),
            description = unescape(first("DESCRIPTION")?.third.orEmpty()).trim(),
            rrule = if (recurrenceId == null) first("RRULE")?.third?.trim()?.takeIf { it.isNotBlank() } else null,
            exdates = exdates,
        )
        return event to recurrenceId
    }

    private fun unfold(text: String): List<String> =
        text.replace("\r\n", "\n").replace("\r", "\n").replace("\n ", "").replace("\n\t", "")
            .split('\n').filter { it.isNotBlank() }

    private fun unescape(s: String) =
        s.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\;", ";").replace("\\\\", "\\")

    private val dateTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val dateOnly = DateTimeFormatter.ofPattern("yyyyMMdd")

    /** All-day dates are kept at midnight UTC, the way Android's calendar stores them. */
    fun date(raw: String, tzid: String?, zone: ZoneId): Instant? = runCatching {
        val v = raw.trim()
        val tz = tzid?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
        when {
            v.length == 8 -> LocalDate.parse(v, dateOnly).atStartOfDay(ZoneOffset.UTC).toInstant()
            v.endsWith("Z", ignoreCase = true) -> LocalDateTime.parse(v.dropLast(1).take(15), dateTime).toInstant(ZoneOffset.UTC)
            else -> LocalDateTime.parse(v.take(15), dateTime).atZone(tz).toInstant()
        }
    }.getOrNull()
}
