package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class QuickAddTest {
    // A Wednesday morning.
    private val now = LocalDateTime.of(2030, 3, 13, 10, 0)
    private fun p(text: String) = QuickAdd.parse(text, now)

    @Test fun weekdayAndTime() {
        val r = p("Jantar com Ana sexta 20h")
        assertEquals("Jantar com Ana", r.title)
        assertEquals(LocalDate.of(2030, 3, 15), r.date)
        assertEquals(LocalTime.of(20, 0), r.start)
        assertFalse(r.allDay)
        assertTrue(r.foundWhen)
    }

    @Test fun tomorrowWithMinutes() {
        val r = p("Dentista amanhã às 9h30")
        assertEquals("Dentista", r.title)
        assertEquals(LocalDate.of(2030, 3, 14), r.date)
        assertEquals(LocalTime.of(9, 30), r.start)
    }

    @Test fun dayOfMonthWithRange() {
        val r = p("Reunião dia 15 das 10h às 11h30")
        assertEquals("Reunião", r.title)
        assertEquals(LocalDate.of(2030, 3, 15), r.date)
        assertEquals(LocalTime.of(10, 0), r.start)
        assertEquals(LocalTime.of(11, 30), r.end)
    }

    @Test fun dayThatPassedIsNextMonth() {
        assertEquals(LocalDate.of(2030, 4, 2), p("Pagar renda dia 2").date)
    }

    @Test fun monthNameMakesAnAllDayEvent() {
        val r = p("Aniversário da Rita 3 de maio")
        assertEquals("Aniversário da Rita", r.title)
        assertEquals(LocalDate.of(2030, 5, 3), r.date)
        assertTrue(r.allDay)
        assertNull(r.start)
    }

    @Test fun pastDateIsNextYear() {
        assertEquals(LocalDate.of(2031, 1, 10), p("Seguro 10/01").date)
        assertEquals(LocalDate.of(2030, 12, 25), p("Natal 25/12/2030").date)
    }

    @Test fun linkingWordsGo() {
        val r = p("Treino às 19h na quinta-feira")
        assertEquals("Treino", r.title)
        assertEquals(LocalDate.of(2030, 3, 14), r.date)
        assertEquals(LocalTime.of(19, 0), r.start)
    }

    @Test fun bareHourAfterAs() {
        val r = p("Café com o Pedro às 4")
        assertEquals("Café com o Pedro", r.title)
        assertEquals(LocalTime.of(4, 0), r.start)
        // 04:00 has passed today, so it is tomorrow.
        assertEquals(LocalDate.of(2030, 3, 14), r.date)
    }

    @Test fun todayLaterAndDashRange() {
        val r = p("Ginásio hoje 18:00-19:30")
        assertEquals("Ginásio", r.title)
        assertEquals(LocalDate.of(2030, 3, 13), r.date)
        assertEquals(LocalTime.of(18, 0), r.start)
        assertEquals(LocalTime.of(19, 30), r.end)
    }

    @Test fun sameWeekdayAlreadyPassedIsNextWeek() {
        assertEquals(LocalDate.of(2030, 3, 20), p("Aula quarta 8h").date)
        assertEquals(LocalDate.of(2030, 3, 13), p("Aula quarta 18h").date)
    }

    @Test fun allDayAndNoon() {
        val a = p("Férias todo o dia sábado")
        assertTrue(a.allDay)
        assertEquals("Férias", a.title)
        assertEquals(LocalDate.of(2030, 3, 16), a.date)
        assertEquals(LocalTime.NOON, p("Almoço amanhã ao meio-dia").start)
    }

    @Test fun plainTitleKeepsEverything() {
        val r = p("Comprar 2 bilhetes")
        assertEquals("Comprar 2 bilhetes", r.title)
        assertFalse(r.foundWhen)
        assertEquals(LocalDate.of(2030, 3, 13), r.date)
    }
}
