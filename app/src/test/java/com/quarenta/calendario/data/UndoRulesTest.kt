package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UndoRulesTest {
    private data class Ev(val id: Long, val begin: Long)

    private val week = listOf(Ev(1, 100), Ev(1, 200), Ev(2, 100), Ev(3, 300))

    private fun visible(vararg gone: Undoable.Event) = UndoRules.visibleEvents(week, gone.toList(), { it.id }, { it.begin })

    @Test fun nothingPendingShowsEverything() = assertEquals(week, visible())

    @Test fun wholeSeriesHidesEveryDay() =
        assertEquals(listOf(Ev(2, 100), Ev(3, 300)), visible(Undoable.Event(1, null, "Ginásio")))

    @Test fun oneDayHidesOnlyThatDay() =
        assertEquals(listOf(Ev(1, 100), Ev(2, 100), Ev(3, 300)), visible(Undoable.Event(1, 200, "Ginásio")))

    @Test fun peopleWaitingAreHidden() {
        val people = listOf(7L, 8L, 9L)
        assertEquals(listOf(7L, 9L), UndoRules.visiblePeople(people, listOf(Undoable.Person(8, "Rita")), { it }))
    }

    @Test fun taskComesBackWithItsSteps() {
        val main = Task(id = 10, title = "Mudança")
        val all = listOf(main, Task(id = 11, title = "Caixas", parentId = 10), Task(id = 12, title = "Outra"), Task(id = 13, title = "Fita", parentId = 10))
        assertEquals(listOf(10L, 11L, 13L), UndoRules.taskWithSteps(main, all).map { it.id })
    }

    @Test fun messagesNameWhatWasDeleted() {
        assertEquals("Contacto de Rita apagado", Undoable.Person(1, "Rita").text)
        assertEquals("Tarefa apagada", Undoable.Tasks(emptyList()).text)
    }
}
