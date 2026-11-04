package com.quarenta.calendario.reminders

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderPlanTest {

    private val min = 60_000L

    private fun alert(id: Long, at: Long, begin: Long = at + 15 * min, end: Long = begin + 60 * min) =
        ReminderAlert(id, "Evento $id", begin, end, false, null, 0, at)

    @Test
    fun showsRemindersThatAreDueOnce() {
        val now = 1_000 * min
        val alerts = listOf(alert(1, now - min), alert(1, now - min), alert(2, now + min), alert(3, now - 5 * min))
        val due = ReminderPlan.due(alerts, lastHandledMs = now - 2 * min, nowMs = now)
        assertEquals(listOf(1L), due.map { it.eventId })
    }

    @Test
    fun catchesUpAfterTheShortGraceButNotOldOnes() {
        val now = 1_000 * min
        val alerts = listOf(alert(1, now - 10 * min), alert(2, now - 40 * min, begin = now + 10 * min))
        val due = ReminderPlan.due(alerts, lastHandledMs = 0, nowMs = now)
        assertEquals(listOf(1L), due.map { it.eventId })
    }

    @Test
    fun skipsEventsThatAlreadyEnded() {
        val now = 1_000 * min
        val ended = alert(1, now - 5 * min, begin = now - 70 * min, end = now - min)
        assertEquals(emptyList<ReminderAlert>(), ReminderPlan.due(listOf(ended), 0, now))
    }

    @Test
    fun findsTheNextWakeUp() {
        val alerts = listOf(alert(1, 50), alert(2, 30), alert(3, 10))
        assertEquals(30L, ReminderPlan.next(alerts, afterMs = 10))
        assertNull(ReminderPlan.next(alerts, afterMs = 50))
    }

    @Test
    fun describesWhenItStarts() {
        val now = 1_000 * min
        assertEquals("Agora", ReminderPlan.whenText(now, now, false))
        assertEquals("Daqui a 15 min", ReminderPlan.whenText(now + 15 * min, now, false))
        assertEquals("Daqui a 2 h", ReminderPlan.whenText(now + 120 * min, now, false))
        assertEquals("Daqui a 1 h 30 min", ReminderPlan.whenText(now + 90 * min, now, false))
        assertEquals("Começou há 10 min", ReminderPlan.whenText(now - 10 * min, now, false))
        assertEquals("Hoje, todo o dia", ReminderPlan.whenText(now, now, true))
    }
}

class PersonMatchTest {

    private val people = listOf(
        1L to "Ana Sousa",
        2L to "João Pedro Marques",
        3L to "Rita",
        4L to "Ana Sousa Lopes",
        5L to "Tiago Fonseca",
        6L to "Tiago Fonseca",
    )

    @Test
    fun matchesTheFullNameIgnoringAccentsAndCase() {
        assertEquals(1L, PersonMatch.inTitle("Jantar com ana sousa", people))
        assertEquals(2L, PersonMatch.inTitle("Café com Joao Pedro Marques!", people))
    }

    @Test
    fun matchesFirstAndLastName() {
        assertEquals(2L, PersonMatch.inTitle("Reunião João Marques", people))
    }

    @Test
    fun prefersTheLongestName() {
        assertEquals(4L, PersonMatch.inTitle("Almoço Ana Sousa Lopes", people))
    }

    @Test
    fun ignoresFirstNamesAlonePartialWordsAndTies() {
        assertNull(PersonMatch.inTitle("Ligar à Rita", people))
        assertNull(PersonMatch.inTitle("Banana Sousada", people))
        assertNull(PersonMatch.inTitle("Treino Tiago Fonseca", people))
    }
}
