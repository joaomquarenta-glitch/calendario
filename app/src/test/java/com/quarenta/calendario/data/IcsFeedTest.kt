package com.quarenta.calendario.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsFeedTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")

    private val feed = """
        BEGIN:VCALENDAR
        X-WR-CALNAME:Jogos do Clube
        BEGIN:VEVENT
        UID:a1
        SUMMARY:Jogo em casa
        DTSTART:20300316T150000Z
        DTEND:20300316T170000Z
        LOCATION:Estádio\, bancada norte
        BEGIN:VALARM
        TRIGGER:-PT30M
        DESCRIPTION:Não deve contar
        END:VALARM
        END:VEVENT
        BEGIN:VEVENT
        UID:f1
        SUMMARY:Feriado
        DTSTART;VALUE=DATE:20300425
        END:VEVENT
        BEGIN:VEVENT
        UID:r1
        SUMMARY:Treino
        DTSTART;TZID=Europe/Lisbon:20300318T190000
        DURATION:PT1H30M
        RRULE:FREQ=WEEKLY;BYDAY=MO
        EXDATE;TZID=Europe/Lisbon:20300401T190000
        END:VEVENT
        BEGIN:VEVENT
        UID:r1
        RECURRENCE-ID;TZID=Europe/Lisbon:20300325T190000
        SUMMARY:Treino (mudou)
        DTSTART;TZID=Europe/Lisbon:20300326T190000
        DTEND;TZID=Europe/Lisbon:20300326T203000
        END:VEVENT
        BEGIN:VEVENT
        UID:c1
        SUMMARY:Cancelado
        STATUS:CANCELLED
        DTSTART:20300320T100000Z
        END:VEVENT
        END:VCALENDAR
    """.trimIndent()

    @Test fun readsTheCalendarName() {
        assertEquals("Jogos do Clube", IcsFeed.calendarName(feed))
    }

    @Test fun readsEveryEventButCancelledOnes() {
        val events = IcsFeed.parse(feed, lisbon)
        assertEquals(listOf("Jogo em casa", "Feriado", "Treino", "Treino (mudou)"), events.map { it.title })
    }

    @Test fun timedEventKeepsPlaceAndIgnoresAlarms() {
        val e = IcsFeed.parse(feed, lisbon).first()
        assertEquals(Instant.parse("2030-03-16T15:00:00Z"), e.start)
        assertEquals(Instant.parse("2030-03-16T17:00:00Z"), e.end)
        assertEquals("Estádio, bancada norte", e.location)
        assertEquals("", e.description)
    }

    @Test fun allDayEventLastsOneDayAtMidnightUtc() {
        val e = IcsFeed.parse(feed, lisbon)[1]
        assertTrue(e.allDay)
        assertEquals(Instant.parse("2030-04-25T00:00:00Z"), e.start)
        assertEquals(Instant.parse("2030-04-26T00:00:00Z"), e.end)
    }

    @Test fun repeatingEventKeepsRuleDurationAndSkipsMovedOccurrence() {
        val e = IcsFeed.parse(feed, lisbon)[2]
        assertEquals("FREQ=WEEKLY;BYDAY=MO", e.rrule)
        assertEquals(Instant.parse("2030-03-18T19:00:00Z"), e.start) // Lisbon is UTC+0 in March
        assertEquals(Instant.parse("2030-03-18T20:30:00Z"), e.end)
        assertEquals(
            listOf(Instant.parse("2030-04-01T18:00:00Z"), Instant.parse("2030-03-25T19:00:00Z")),
            e.exdates,
        )
        assertNull(IcsFeed.parse(feed, lisbon)[3].rrule)
    }
}
