package com.quarenta.calendario.ui.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HighlightsTest {

    @Test
    fun recognisesBirthdays() {
        assertTrue(Highlights.isBirthday("Rui Costa - Aniversário", listOf("Calendar")))
        assertTrue(Highlights.isBirthday("Rui Costa", listOf("Birthday calendar")))
        assertTrue(Highlights.isBirthday("O Rui faz 40 anos", emptyList()))
        assertFalse(Highlights.isBirthday("Jantar de anos novos", emptyList()))
        assertFalse(Highlights.isBirthday("Acupuntura", listOf("Calendar")))
    }

    @Test
    fun keepsOnlyWhatIsStillToCome() {
        data class E(val name: String, val start: Long, val end: Long)
        val items = listOf(E("c", 30, 40), E("past", 0, 10), E("now", 5, 25), E("b", 20, 30))
        val out = Highlights.upcoming(items, fromMs = 15, endMs = { it.end }, startMs = { it.start }, max = 2)
        assertEquals(listOf("now", "b"), out.map { it.name })
    }

    @Test
    fun writesShortDaysAndAges() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals("hoje", Highlights.shortDay(today, today))
        assertEquals("amanhã", Highlights.shortDay(today.plusDays(1), today))
        assertEquals("dia 7", Highlights.shortDay(LocalDate.of(2026, 11, 7), today))
        assertEquals("dia 3 dez", Highlights.shortDay(LocalDate.of(2026, 12, 3), today))
        assertEquals("34º aniversário · dia 9", Highlights.birthdayLabel(1992, LocalDate.of(2026, 11, 9), today))
        assertEquals("Aniversário · amanhã", Highlights.birthdayLabel(null, LocalDate.of(2026, 11, 5), today))
    }

    @Test
    fun buildsNavigationLinks() {
        assertEquals("https://waze.com/ul?q=Rua+das+Flores+10%2C+Porto&navigate=yes", Highlights.wazeUrl(" Rua das Flores 10, Porto "))
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=Pra%C3%A7a+Central", Highlights.mapsUrl("Praça Central"))
        assertEquals("12@2026-11-05", Highlights.planKey(12, LocalDate.of(2026, 11, 5)))
    }
}
