package com.quarenta.calendario.ui.agenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** The days a month grid shows: whole weeks from Monday to Sunday, always 6 rows so the grid never jumps. */
object MonthMath {
    const val CELLS = 42

    fun gridStart(month: YearMonth): LocalDate =
        month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun days(month: YearMonth): List<LocalDate> {
        val start = gridStart(month)
        return List(CELLS) { start.plusDays(it.toLong()) }
    }

    /**
     * Where the selection goes when the month changes: the same day number if that month has it
     * (31 Jan -> 28 Feb), or today when today falls in the new month.
     */
    fun moveSelection(selected: LocalDate, to: YearMonth, today: LocalDate): LocalDate =
        if (YearMonth.from(today) == to) today
        else to.atDay(minOf(selected.dayOfMonth, to.lengthOfMonth()))
}
