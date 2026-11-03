package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class DedupTest {

    private val zone = ZoneId.of("Europe/Lisbon")

    private fun event(
        id: Long,
        title: String,
        start: String,
        allDay: Boolean = false,
        calendar: String = "Google",
        canEdit: Boolean = true,
        location: String? = null,
    ) = CalendarEvent(
        id = id, title = title, start = Instant.parse(start), end = Instant.parse(start).plusSeconds(3600),
        allDay = allDay, location = location, description = null, color = 0, calendarId = id,
        calendarName = calendar, accountName = "conta", canEdit = canEdit,
    )

    @Test
    fun normalizeRemovesAccentsAndBirthdayWords() {
        assertEquals("barbara magalhaes", Dedup.normalize("Bárbara Magalhães - Aniversário"))
        assertEquals("barbara magalhaes", Dedup.normalize("Bárbara Magalhães"))
        assertEquals("consulta dentista", Dedup.normalize("Consulta no Dentista!"))
    }

    @Test
    fun birthdayFromTwoCalendarsBecomesOne() {
        val merged = Dedup.merge(
            listOf(
                event(1, "Bárbara Magalhães", "2026-11-05T00:00:00Z", allDay = true, calendar = "Contactos", canEdit = false),
                event(2, "Bárbara Magalhães - Aniversário", "2026-11-05T00:00:00Z", allDay = true, calendar = "Google"),
            ),
            zone,
        )
        assertEquals(1, merged.size)
        assertEquals(2L, merged[0].id) // the editable copy wins
        assertEquals(listOf("Contactos", "Google"), merged[0].sources)
        assertEquals(listOf(1L), merged[0].duplicateIds)
    }

    @Test
    fun sameAppointmentInGoogleAndOutlookIsMergedAndKeepsDetails() {
        val merged = Dedup.merge(
            listOf(
                event(10, "Dentista", "2026-11-04T16:00:00Z", calendar = "Google"),
                event(11, "Dentista", "2026-11-04T16:00:00Z", calendar = "Outlook", canEdit = false, location = "Clínica Sorriso"),
            ),
            zone,
        )
        assertEquals(1, merged.size)
        assertEquals("Clínica Sorriso", merged[0].location)
        assertEquals(listOf("Google", "Outlook"), merged[0].sources)
    }

    @Test
    fun differentTimesOrTitlesStaySeparate() {
        val merged = Dedup.merge(
            listOf(
                event(1, "Dentista", "2026-11-04T16:00:00Z"),
                event(2, "Dentista", "2026-11-04T17:00:00Z"),
                event(3, "Ginásio", "2026-11-04T16:00:00Z"),
                event(4, "Dentista", "2026-11-04T00:00:00Z", allDay = true),
            ),
            zone,
        )
        assertEquals(4, merged.size)
    }

    @Test
    fun shortTitlesAreNotMatchedInsideLongerOnes() {
        val merged = Dedup.merge(
            listOf(
                event(1, "Ana", "2026-11-04T12:00:00Z"),
                event(2, "Almoço Ana e Rui", "2026-11-04T12:00:00Z"),
            ),
            zone,
        )
        assertEquals(2, merged.size)
    }

    @Test
    fun sameEventIdIsNeverMergedWithItself() {
        val e = event(7, "Reunião", "2026-11-04T09:00:00Z")
        val merged = Dedup.merge(listOf(e, e.copy(start = e.start.plusSeconds(86_400))), zone)
        assertEquals(2, merged.size)
        assertTrue(merged.all { it.duplicateIds.isEmpty() })
    }
}
