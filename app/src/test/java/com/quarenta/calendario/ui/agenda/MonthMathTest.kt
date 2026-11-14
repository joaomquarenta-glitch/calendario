package com.quarenta.calendario.ui.agenda

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthMathTest {
    @Test fun gridStartsOnMondayAndCoversTheMonth() {
        val month = YearMonth.of(2030, 3) // 1 March 2030 is a Friday
        val days = MonthMath.days(month)
        assertEquals(42, days.size)
        assertEquals(LocalDate.of(2030, 2, 25), days.first())
        assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
        assertTrue(days.contains(LocalDate.of(2030, 3, 1)))
        assertTrue(days.contains(LocalDate.of(2030, 3, 31)))
    }

    @Test fun monthStartingOnMondayStartsOnTheFirst() {
        assertEquals(LocalDate.of(2030, 4, 1), MonthMath.gridStart(YearMonth.of(2030, 4)))
    }

    @Test fun selectionKeepsDayNumberWhenItExists() {
        val today = LocalDate.of(2030, 3, 13)
        assertEquals(LocalDate.of(2030, 5, 20), MonthMath.moveSelection(LocalDate.of(2030, 4, 20), YearMonth.of(2030, 5), today))
    }

    @Test fun selectionClampsToShortMonths() {
        val today = LocalDate.of(2030, 3, 13)
        assertEquals(LocalDate.of(2031, 2, 28), MonthMath.moveSelection(LocalDate.of(2031, 1, 31), YearMonth.of(2031, 2), today))
    }

    @Test fun selectionJumpsToTodayInTodaysMonth() {
        val today = LocalDate.of(2030, 3, 13)
        assertEquals(today, MonthMath.moveSelection(LocalDate.of(2030, 4, 2), YearMonth.of(2030, 3), today))
    }
}
