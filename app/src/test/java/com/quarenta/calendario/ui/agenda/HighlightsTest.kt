package com.quarenta.calendario.ui.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
