package com.quarenta.calendario.reminders

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MorningTextTest {
    private fun at(h: Int, m: Int = 0) = LocalTime.of(h, m)

    @Test fun severalEventsNameTheFirst() {
        val t = MorningText.build(
            listOf(
                MorningText.Item("Reunião", false, at(14, 30)),
                MorningText.Item("Dentista", false, at(10)),
                MorningText.Item("Feriado", true, null),
            ),
            emptyList(), listOf("Aniversário da Rita"),
        )
        assertEquals("Hoje tens 3 compromissos", t.title)
        assertEquals("O primeiro é às 10h: Dentista. Amanhã: Aniversário da Rita.", t.body)
    }

    @Test fun oneTimedEvent() {
        val t = MorningText.build(listOf(MorningText.Item("Ginásio", false, at(18, 15))), emptyList(), emptyList())
        assertEquals("Hoje tens 1 compromisso", t.title)
        assertEquals("Às 18h15: Ginásio.", t.body)
    }

    @Test fun freeDay() {
        val t = MorningText.build(emptyList(), emptyList(), emptyList())
        assertEquals("Hoje tens o dia livre", t.title)
        assertEquals("Nada marcado. Bom dia!", t.body)
    }

    @Test fun onlyBirthdays() {
        val t = MorningText.build(emptyList(), listOf("Aniversário do Rui"), emptyList())
        assertEquals("Hoje há aniversários", t.title)
        assertEquals("Hoje: Aniversário do Rui.", t.body)
    }
}
