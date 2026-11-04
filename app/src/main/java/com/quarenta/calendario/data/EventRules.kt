package com.quarenta.calendario.data

import kotlin.math.roundToLong

/** Small rules for writing events to the phone's calendar store, kept apart so they can be tested. */
object EventRules {
    private const val DAY_MS = 86_400_000L

    /** RFC 5545 length of a repeating event: whole days for all-day events, seconds otherwise. */
    fun duration(lengthMs: Long, allDay: Boolean): String =
        if (allDay) "P${maxOf(1L, (lengthMs.toDouble() / DAY_MS).roundToLong())}D"
        else "PT${maxOf(60L, lengthMs / 1000)}S"

    /**
     * Where a repeating series starts after one occurrence was moved by [shiftMs].
     * All-day series stay on UTC midnight, so the shift is rounded to whole days
     * (a summer-time change makes a "day" 23 or 25 hours long).
     */
    fun seriesStart(dtStart: Long, shiftMs: Long, allDay: Boolean): Long =
        if (allDay) dtStart + (shiftMs.toDouble() / DAY_MS).roundToLong() * DAY_MS
        else dtStart + shiftMs
}
