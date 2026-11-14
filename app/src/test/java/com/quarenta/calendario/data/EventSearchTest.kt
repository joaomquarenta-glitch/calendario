package com.quarenta.calendario.data

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventSearchTest {
    private val now = Instant.parse("2030-03-13T10:00:00Z")

    private fun ev(id: Long, title: String, start: String, location: String? = null, notes: String? = null) = CalendarEvent(
        id = id, title = title, start = Instant.parse(start), end = Instant.parse(start).plusSeconds(3600),
        allDay = false, location = location, description = notes, color = 0, calendarId = 1,
        calendarName = "Pessoal", accountName = "", canEdit = true,
    )

    @Test fun ignoresAccentsAndCapitals() {
        assertTrue(EventSearch.matches(ev(1, "Festa de São João", "2030-06-23T20:00:00Z"), "sao joao"))
        assertTrue(EventSearch.matches(ev(1, "dentista", "2030-06-23T20:00:00Z"), "DENT"))
    }

    @Test fun matchesPlaceAndNotes() {
        assertTrue(EventSearch.matches(ev(1, "Jantar", "2030-03-20T20:00:00Z", location = "Restaurante Exemplo"), "exemplo"))
        assertTrue(EventSearch.matches(ev(1, "Reunião", "2030-03-20T20:00:00Z", notes = "levar o portátil"), "portatil"))
    }

    @Test fun everyWordMustMatchAtAWordStart() {
        val e = ev(1, "Jantar com a Ana", "2030-03-20T20:00:00Z")
        assertTrue(EventSearch.matches(e, "ana jantar"))
        assertFalse(EventSearch.matches(e, "ana cinema"))
        assertFalse(EventSearch.matches(ev(2, "Comprar banana", "2030-03-20T20:00:00Z"), "ana"))
    }

    @Test fun blankQueryFindsNothing() {
        assertFalse(EventSearch.matches(ev(1, "Jantar", "2030-03-20T20:00:00Z"), "   "))
    }

    @Test fun splitsUpcomingAndPastInTheRightOrder() {
        val events = listOf(
            ev(1, "Dentista", "2030-01-10T09:00:00Z"),
            ev(2, "Dentista", "2030-02-10T09:00:00Z"),
            ev(3, "Dentista", "2030-05-10T09:00:00Z"),
            ev(4, "Dentista", "2030-04-10T09:00:00Z"),
            ev(5, "Ginásio", "2030-04-11T09:00:00Z"),
        )
        val r = EventSearch.search(events, "dentista", now)
        assertEquals(listOf(4L, 3L), r.upcoming.map { it.id })
        assertEquals(listOf(2L, 1L), r.past.map { it.id })
    }
}
