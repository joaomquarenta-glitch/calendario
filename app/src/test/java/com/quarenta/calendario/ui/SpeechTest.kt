package com.quarenta.calendario.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SpeechTest {
    private val today = LocalDate.of(2026, 11, 17) // a Tuesday

    @Test fun todaySaysHojeTheWholeDateAndTheEvents() {
        assertEquals("Hoje, terça-feira, 17 de novembro, 2 compromissos", Speech.day(today, today, 2))
    }

    @Test fun tomorrowAndYesterdayAreNamed() {
        assertEquals("Amanhã, quarta-feira, 18 de novembro, 1 compromisso", Speech.day(today.plusDays(1), today, 1))
        assertEquals("Ontem, segunda-feira, 16 de novembro, sem compromissos", Speech.day(today.minusDays(1), today, 0))
    }

    @Test fun anotherYearSaysTheYear() {
        assertEquals("terça-feira, 5 de janeiro de 2027, sem compromissos", Speech.day(LocalDate.of(2027, 1, 5), today, 0))
    }

    @Test fun taskStateInPortuguese() {
        assertEquals("Feita", Speech.taskState(true))
        assertEquals("Por fazer", Speech.taskState(false))
    }
}
