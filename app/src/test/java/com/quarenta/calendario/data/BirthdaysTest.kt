package com.quarenta.calendario.data

import java.time.LocalDate
import java.time.MonthDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BirthdaysTest {
    private val today = LocalDate.of(2026, 11, 14)

    @Test fun todayCountsAsNext() {
        assertEquals(today, Birthdays.nextDate(MonthDay.of(11, 14), today))
    }

    @Test fun passedBirthdayMovesToNextYear() {
        assertEquals(LocalDate.of(2027, 3, 2), Birthdays.nextDate(MonthDay.of(3, 2), today))
    }

    @Test fun leapDayFallsOn28thInOtherYears() {
        assertEquals(LocalDate.of(2027, 2, 28), Birthdays.nextDate(MonthDay.of(2, 29), today))
        assertEquals(LocalDate.of(2028, 2, 29), Birthdays.nextDate(MonthDay.of(2, 29), LocalDate.of(2027, 3, 1)))
    }

    @Test fun upcomingIsSortedWithAge() {
        val list = Birthdays.upcoming(
            listOf(
                Triple(1L, "Bruno Exemplo", MonthDay.of(1, 5) to 1990),
                Triple(2L, "Ana Teste", MonthDay.of(11, 20) to null),
            ),
            today,
        )
        assertEquals(listOf(2L, 1L), list.map { it.personId })
        assertEquals(6, list[0].daysLeft)
        assertEquals(null, list[0].turns)
        assertEquals(37, list[1].turns)
    }

    @Test fun countdownWords() {
        assertEquals("Hoje!", Birthdays.countdown(0))
        assertEquals("Amanhã", Birthdays.countdown(1))
        assertEquals("Faltam 5 dias", Birthdays.countdown(5))
        assertEquals("Faltam 3 semanas", Birthdays.countdown(21))
        assertEquals("Faltam 4 meses", Birthdays.countdown(125))
    }

    @Test fun wishesUseNameAndAge() {
        val w = Birthdays.wishes("Ana", 30)
        assertTrue(w.all { "Ana" in it })
        assertTrue(w.any { "30 anos" in it })
        assertEquals(3, Birthdays.wishes("", null).size)
    }
}
