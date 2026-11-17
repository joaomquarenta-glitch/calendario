package com.quarenta.calendario.data

/**
 * Something just deleted that can still be brought back. Events and people are only really
 * deleted when the "Desfazer" moment passes; tasks are deleted at once and put back if asked.
 */
sealed interface Undoable {
    val text: String

    /** A whole event (every repetition), or one day of a repeating one when [instance] is set. */
    data class Event(val id: Long, val instance: Long?, val title: String) : Undoable {
        override val text get() = "Compromisso apagado"
    }

    data class Person(val id: Long, val name: String) : Undoable {
        override val text get() = "Contacto de $name apagado"
    }

    /** The task and its steps, exactly as they were, ready to be written back. */
    data class Tasks(val saved: List<Task>) : Undoable {
        override val text get() = "Tarefa apagada"
    }
}

object UndoRules {
    /** How long "Desfazer" stays on screen before the deletion is final. */
    const val WINDOW_MS = 6_000L

    /** Hides events waiting to be deleted, so the list never flickers back while it is pending. */
    fun <T> visibleEvents(list: List<T>, gone: Collection<Undoable.Event>, id: (T) -> Long, instance: (T) -> Long): List<T> =
        if (gone.isEmpty()) list
        else list.filterNot { e -> gone.any { g -> g.id == id(e) && (g.instance == null || g.instance == instance(e)) } }

    fun <T> visiblePeople(list: List<T>, gone: Collection<Undoable.Person>, id: (T) -> Long): List<T> =
        if (gone.isEmpty()) list else list.filterNot { p -> gone.any { it.id == id(p) } }

    /** A task with its steps, the way they must be saved to be put back. */
    fun taskWithSteps(task: Task, all: List<Task>): List<Task> = listOf(task) + all.filter { it.parentId == task.id }
}
