package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class TaskRulesTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val today = LocalDate.of(2026, 3, 10)

    @Test
    fun repeatsMoveToTheNextDay() {
        assertEquals(LocalDate.of(2026, 3, 11), TaskRules.nextDue(today, "daily"))
        assertEquals(LocalDate.of(2026, 3, 17), TaskRules.nextDue(today, "weekly"))
        assertEquals(LocalDate.of(2026, 2, 28), TaskRules.nextDue(LocalDate.of(2026, 1, 31), "monthly"))
        assertEquals(LocalDate.of(2027, 3, 10), TaskRules.nextDue(today, "yearly"))
    }

    @Test
    fun tickingARepeatingTaskMovesItInsteadOfFinishingIt() {
        val weekly = Task(id = 1, title = "Regar as plantas", dueEpochDay = today.toEpochDay(), repeat = "weekly")
        val next = TaskRules.complete(weekly, today)
        assertFalse(next.done)
        assertEquals(LocalDate.of(2026, 3, 17).toEpochDay(), next.dueEpochDay)

        // Forgotten for three weeks: it comes back once, on its next day from today.
        val late = weekly.copy(dueEpochDay = today.minusWeeks(3).toEpochDay())
        assertEquals(LocalDate.of(2026, 3, 17).toEpochDay(), TaskRules.complete(late, today).dueEpochDay)
    }

    @Test
    fun tickingAPlainTaskFinishesItAndTickingAgainReopens() {
        val once = Task(id = 2, title = "Pagar a luz", dueEpochDay = today.toEpochDay())
        val done = TaskRules.complete(once, today)
        assertTrue(done.done)
        assertFalse(TaskRules.complete(done, today).done)
        assertTrue(TaskRules.complete(Task(id = 3, title = "Sem data", repeat = "daily"), today).done)
    }

    @Test
    fun reminderTimes() {
        val t = Task(id = 4, title = "Dentista", dueEpochDay = today.toEpochDay(), timeMinutes = 14 * 60 + 30, remindBefore = 60)
        assertEquals(LocalDateTime.of(2026, 3, 10, 13, 30).atZone(zone).toInstant(), TaskRules.reminderAt(t, zone))
        val noTime = t.copy(timeMinutes = null, remindBefore = 0)
        assertEquals(LocalDateTime.of(2026, 3, 10, 9, 0).atZone(zone).toInstant(), TaskRules.reminderAt(noTime, zone))
        assertNull(TaskRules.reminderAt(t.copy(remindBefore = null), zone))
        assertNull(TaskRules.reminderAt(t.copy(dueEpochDay = null), zone))
        assertNull(TaskRules.reminderAt(t.copy(done = true), zone))
    }

    @Test
    fun textsAndProgress() {
        assertEquals("09:05", TaskRules.timeText(9 * 60 + 5))
        assertEquals("Todas as semanas", TaskRules.repeatLabel("weekly"))
        assertNull(TaskRules.repeatLabel(null))
        assertEquals("1 hora antes", TaskRules.reminderLabel(60))
        assertNull(TaskRules.progress(emptyList()))
        assertEquals("1/2", TaskRules.progress(listOf(Task(title = "a", done = true), Task(title = "b"))))
    }
}
