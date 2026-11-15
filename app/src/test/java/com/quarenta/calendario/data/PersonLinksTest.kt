package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonLinksTest {

    @Test
    fun findsTheNameInAnyForm() {
        assertTrue(PersonLinks.mentions("Rita Valente Moura", "Jantar com a Rita Moura"))
        assertTrue(PersonLinks.mentions("Rita Valente Moura", "Devolver livro — Rita Valente Moura"))
        assertTrue(PersonLinks.mentions("Tomás Faria", "ligar ao tomas faria"))
        assertFalse(PersonLinks.mentions("Rita Valente Moura", "Jantar com a Rita"))
        assertFalse(PersonLinks.mentions("", "qualquer coisa"))
    }

    @Test
    fun listsOpenTasksFirst() {
        val tasks = listOf(
            Task(id = 1, title = "Prenda Tomás Faria", done = true),
            Task(id = 2, title = "Comprar pão"),
            Task(id = 3, title = "Ligar ao Tomás Faria"),
        )
        assertEquals(listOf(3L, 1L), PersonLinks.tasksWith("Tomás Faria", tasks).map { it.id })
    }

    @Test
    fun addsTheNameOnlyWhenMissing() {
        assertEquals("Devolver livro — Tomás Faria", PersonLinks.taskTitle(" Devolver livro ", "Tomás Faria"))
        assertEquals("Ligar ao Tomás Faria", PersonLinks.taskTitle("Ligar ao Tomás Faria", "Tomás Faria"))
        assertEquals("", PersonLinks.taskTitle("  ", "Tomás Faria"))
    }

    @Test
    fun saysHowLongAgo() {
        assertEquals("hoje", PersonLinks.sinceText(0))
        assertEquals("ontem", PersonLinks.sinceText(1))
        assertEquals("há 5 dias", PersonLinks.sinceText(5))
        assertEquals("há 3 semanas", PersonLinks.sinceText(21))
        assertEquals("há 2 meses", PersonLinks.sinceText(70))
        assertEquals("há 1 ano", PersonLinks.sinceText(400))
        assertEquals("há 3 anos", PersonLinks.sinceText(1100))
    }
}
