package com.quarenta.calendario.ui.agenda

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The maths of the hour-by-hour day: where each appointment sits, and how dragging moves it. */
object DayLayout {
    const val DAY_MINUTES = 24 * 60
    const val SNAP = 15
    /** The shortest an appointment is drawn, so a 5-minute one can still be read and tapped. */
    const val MIN_DRAWN = 30

    /** Minutes after midnight on [day], clipped to that day (an appointment can start the day before). */
    data class Span(val start: Int, val end: Int)

    fun span(start: Instant, end: Instant, day: LocalDate, zone: ZoneId): Span? {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val s = start.toEpochMilli().coerceAtLeast(dayStart)
        val e = end.toEpochMilli().coerceAtMost(dayEnd)
        if (e <= s && start.toEpochMilli() != end.toEpochMilli()) return null
        if (start.toEpochMilli() >= dayEnd || end.toEpochMilli() < dayStart) return null
        val sm = ((s - dayStart) / 60_000).toInt()
        val em = ((e - dayStart) / 60_000).toInt().coerceAtLeast(sm)
        return Span(sm, em)
    }

    /** Where one appointment goes: column [column] of [columns] side by side. */
    data class Slot(val column: Int, val columns: Int)

    /**
     * Places overlapping appointments side by side, each group of overlaps sharing the width.
     * The result is in the same order as [spans].
     */
    fun place(spans: List<Span>): List<Slot> {
        if (spans.isEmpty()) return emptyList()
        val order = spans.indices.sortedWith(compareBy({ spans[it].start }, { -drawnEnd(spans[it]) }))
        val column = IntArray(spans.size)
        val columns = IntArray(spans.size)
        var group = mutableListOf<Int>()
        var groupEnd = -1
        val colEnds = mutableListOf<Int>()
        fun close() {
            val n = colEnds.size
            group.forEach { columns[it] = n }
            group = mutableListOf(); colEnds.clear()
        }
        for (i in order) {
            val s = spans[i]
            if (group.isNotEmpty() && s.start >= groupEnd) close()
            val free = colEnds.indexOfFirst { it <= s.start }
            val c = if (free >= 0) free else colEnds.size
            if (c == colEnds.size) colEnds += drawnEnd(s) else colEnds[c] = drawnEnd(s)
            column[i] = c
            group += i
            groupEnd = maxOf(groupEnd, drawnEnd(s))
        }
        close()
        return spans.indices.map { Slot(column[it], columns[it]) }
    }

    private fun drawnEnd(s: Span) = maxOf(s.end, s.start + MIN_DRAWN)

    /**
     * The new start after dragging by [deltaMinutes]: on a quarter of an hour, and never
     * pushed out of the day.
     */
    fun moved(startMinute: Int, durationMinutes: Int, deltaMinutes: Float): Int {
        val raw = startMinute + deltaMinutes
        val snapped = (Math.round(raw / SNAP) * SNAP)
        val latest = (DAY_MINUTES - durationMinutes.coerceIn(0, DAY_MINUTES)).coerceAtLeast(0)
        return snapped.coerceIn(0, latest - latest % SNAP)
    }

    fun label(minute: Int): String = "%02d:%02d".format(minute / 60, minute % 60)
}
