package com.quarenta.calendario.ui.agenda

import com.quarenta.calendario.ui.agenda.DayLayout.Slot
import com.quarenta.calendario.ui.agenda.DayLayout.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DayLayoutTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val day = LocalDate.of(2026, 3, 10)
    private fun at(d: LocalDate, h: Int, m: Int = 0) = LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant()

    @Test fun spanInsideTheDay() = assertEquals(Span(9 * 60 + 30, 11 * 60), DayLayout.span(at(day, 9, 30), at(day, 11), day, zone))

    @Test fun spanFromTheNightBeforeStartsAtMidnight() =
        assertEquals(Span(0, 2 * 60), DayLayout.span(at(day.minusDays(1), 22), at(day, 2), day, zone))

    @Test fun spanIntoTheNextDayStopsAtMidnight() =
        assertEquals(Span(23 * 60, 24 * 60), DayLayout.span(at(day, 23), at(day.plusDays(1), 1), day, zone))

    @Test fun otherDaysAreLeftOut() = assertNull(DayLayout.span(at(day.plusDays(1), 9), at(day.plusDays(1), 10), day, zone))

    @Test fun separateAppointmentsTakeTheWholeWidth() =
        assertEquals(listOf(Slot(0, 1), Slot(0, 1)), DayLayout.place(listOf(Span(540, 600), Span(600, 660))))

    @Test fun overlapsGoSideBySide() =
        assertEquals(listOf(Slot(0, 2), Slot(1, 2), Slot(0, 1)), DayLayout.place(listOf(Span(540, 660), Span(570, 600), Span(720, 780))))

    @Test fun aFreedColumnIsReused() =
        assertEquals(
            listOf(Slot(0, 2), Slot(1, 2), Slot(1, 2)),
            DayLayout.place(listOf(Span(540, 720), Span(540, 600), Span(600, 660))),
        )

    @Test fun shortAppointmentsStillOverlapByTheirDrawnSize() =
        assertEquals(listOf(Slot(0, 2), Slot(1, 2)), DayLayout.place(listOf(Span(540, 545), Span(555, 600))))

    @Test fun dragSnapsToQuarterHours() {
        assertEquals(9 * 60 + 45, DayLayout.moved(9 * 60, 60, 52f))
        assertEquals(10 * 60, DayLayout.moved(9 * 60, 60, 55f))
        assertEquals(9 * 60 + 15, DayLayout.moved(9 * 60, 60, 8f))
    }

    @Test fun dragStaysInsideTheDay() {
        assertEquals(0, DayLayout.moved(30, 60, -200f))
        assertEquals(23 * 60, DayLayout.moved(22 * 60, 60, 500f))
    }

    @Test fun labels() = assertEquals("07:05", DayLayout.label(7 * 60 + 5))
}
