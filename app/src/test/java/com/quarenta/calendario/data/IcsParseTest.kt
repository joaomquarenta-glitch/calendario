package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class IcsParseTest {

    @Test
    fun readsAppointmentWithTimeZone() {
        val ics = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            UID:123@sams.pt
            DTSTART;TZID=Europe/Lisbon:20261112T093000
            DTEND;TZID=Europe/Lisbon:20261112T101500
            SUMMARY:Consulta de Cardiologia
            LOCATION:SAMS\, Rua Fialho de Almeida 18\, Lisboa
            DESCRIPTION:Traga o cartão de beneficiário.\nChegue 15 min antes.
            END:VEVENT
            END:VCALENDAR
        """.trimIndent().replace("\n", "\r\n")

        val d = IncomingEvent.parseIcs(ics)!!
        assertEquals("Consulta de Cardiologia", d.title)
        assertEquals(Instant.parse("2026-11-12T09:30:00Z"), d.start) // Lisbon = UTC in November
        assertEquals(Instant.parse("2026-11-12T10:15:00Z"), d.end)
        assertEquals("SAMS, Rua Fialho de Almeida 18, Lisboa", d.location)
        assertEquals("Traga o cartão de beneficiário.\nChegue 15 min antes.", d.description)
        assertFalse(d.allDay)
    }

    @Test
    fun readsUtcTimesAndFoldedLines() {
        val ics = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:20260701T140000Z\r\n" +
            "SUMMARY:Exame de\r\n  sangue\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val d = IncomingEvent.parseIcs(ics)!!
        assertEquals("Exame de sangue", d.title)
        assertEquals(Instant.parse("2026-07-01T14:00:00Z"), d.start)
        assertEquals(Instant.parse("2026-07-01T15:00:00Z"), d.end) // no DTEND: one hour
    }

    @Test
    fun readsAllDayEvents() {
        val ics = "BEGIN:VEVENT\nDTSTART;VALUE=DATE:20261225\nDTEND;VALUE=DATE:20261226\nSUMMARY:Natal\nEND:VEVENT\n"
        val d = IncomingEvent.parseIcs(ics)!!
        assertTrue(d.allDay)
        assertEquals(LocalDate.of(2026, 12, 25), d.start.atZone(ZoneId.systemDefault()).toLocalDate())
    }

    @Test
    fun usesOnlyTheFirstEvent() {
        val ics = "BEGIN:VEVENT\nDTSTART:20260101T100000Z\nSUMMARY:Primeiro\nEND:VEVENT\n" +
            "BEGIN:VEVENT\nDTSTART:20260102T100000Z\nSUMMARY:Segundo\nEND:VEVENT\n"
        assertEquals("Primeiro", IncomingEvent.parseIcs(ics)!!.title)
    }

    @Test
    fun rejectsTextWithoutEvent() {
        assertNull(IncomingEvent.parseIcs("BEGIN:VCALENDAR\nEND:VCALENDAR"))
        assertNull(IncomingEvent.parseIcs("BEGIN:VEVENT\nSUMMARY:Sem data\nEND:VEVENT"))
    }
}
