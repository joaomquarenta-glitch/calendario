package com.quarenta.calendario.widget

import com.quarenta.calendario.data.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WidgetPicksTest {
    private val today = LocalDate.of(2026, 11, 15)
    private fun task(id: Long, title: String, due: LocalDate? = null, done: Boolean = false, parent: Long? = null) =
        Task(id = id, title = title, done = done, dueEpochDay = due?.toEpochDay(), parentId = parent, createdAt = id)

    @Test
    fun lateAndTodayComeFirstAndDoneOrStepsAreLeftOut() {
        val all = listOf(
            task(1, "Sem data"),
            task(2, "Amanhã", today.plusDays(1)),
            task(3, "Atrasada", today.minusDays(2)),
            task(4, "Feita", today, done = true),
            task(5, "Passo", today, parent = 2),
            task(6, "Hoje", today),
        )
        assertEquals(listOf("Atrasada", "Hoje", "Amanhã", "Sem data"), WidgetPicks.tasks(all, today).map { it.title })
        assertEquals(2, WidgetPicks.dueCount(all, today))
    }

    @Test
    fun showsAtMostFiveRows() {
        val all = (1L..9L).map { task(it, "T$it", today) }
        assertEquals(5, WidgetPicks.tasks(all, today).size)
    }

    @Test
    fun saysWhenInWords() {
        assertEquals("Atrasada", WidgetPicks.taskWhen(task(1, "x", today.minusDays(1)), today))
        assertEquals("Hoje", WidgetPicks.taskWhen(task(1, "x", today), today))
        assertEquals("Amanhã", WidgetPicks.taskWhen(task(1, "x", today.plusDays(1)), today))
        assertEquals("", WidgetPicks.taskWhen(task(1, "x"), today))
    }

    @Test
    fun picksTheFirstEventWithSomebody() {
        val events = listOf("Dentista", "Jantar com Ana", "Café com Rui")
        val found = WidgetPicks.firstWithPerson(events) { e -> listOf("Ana", "Rui").firstOrNull { e.contains(it) } }
        assertEquals("Jantar com Ana" to "Ana", found)
        assertNull(WidgetPicks.firstWithPerson(listOf("Dentista")) { null as String? })
    }

    @Test
    fun namesTheMonth() {
        assertEquals("Novembro 2026", WidgetPicks.monthTitle(YearMonth.of(2026, 11)))
    }
}
