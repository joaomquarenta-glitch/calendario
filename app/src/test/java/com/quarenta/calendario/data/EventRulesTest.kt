package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EventRulesTest {
    private val day = 86_400_000L

    @Test fun allDayDurationInDays() {
        assertEquals("P1D", EventRules.duration(day, allDay = true))
        assertEquals("P2D", EventRules.duration(2 * day, allDay = true))
        assertEquals("P1D", EventRules.duration(0, allDay = true))
    }

    @Test fun timedDurationInSeconds() {
        assertEquals("PT3600S", EventRules.duration(3_600_000, allDay = false))
        assertEquals("PT60S", EventRules.duration(10_000, allDay = false))
    }

    @Test fun unchangedSeriesStaysPut() {
        assertEquals(1_000L, EventRules.seriesStart(1_000L, 0L, allDay = false))
        assertEquals(10 * day, EventRules.seriesStart(10 * day, 0L, allDay = true))
    }

    @Test fun allDayShiftRoundsToWholeDaysAcrossSummerTime() {
        // Moved one day later, but the local day was 23 hours long.
        assertEquals(11 * day, EventRules.seriesStart(10 * day, 23 * 3_600_000L, allDay = true))
        assertEquals(9 * day, EventRules.seriesStart(10 * day, -25 * 3_600_000L, allDay = true))
    }

    @Test fun timedShiftIsExact() {
        assertEquals(5_000L + 1_800_000L, EventRules.seriesStart(5_000L, 1_800_000L, allDay = false))
    }
}
