package com.quarenta.calendario.widget

import com.quarenta.calendario.data.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the new widgets show, kept apart from Android so it can be tested. */
object WidgetPicks {
    const val TASK_ROWS = 5

    /** The open tasks worth seeing first: late and today's, then the coming days, then those with no date. */
    fun tasks(all: List<Task>, today: LocalDate, max: Int = TASK_ROWS): List<Task> =
        all.asSequence()
            .filter { !it.done && it.parentId == null }
            .sortedWith(compareBy<Task>({ it.dueEpochDay == null }, { it.dueEpochDay ?: Long.MAX_VALUE }, { it.timeMinutes ?: Int.MAX_VALUE }, { it.createdAt }))
            .take(max)
            .toList()

    /** The small line under a task: "Atrasada", "Hoje", "Amanhã" or "sex, 20 nov". Empty with no date. */
    fun taskWhen(task: Task, today: LocalDate): String {
        val due = task.dueEpochDay?.let(LocalDate::ofEpochDay) ?: return ""
        return when {
            due.isBefore(today) -> "Atrasada"
            due == today -> "Hoje"
            due == today.plusDays(1) -> "Amanhã"
            else -> due.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale.forLanguageTag("pt-PT"))).replace(".", "")
        }
    }

    /** How many open tasks are late or due today: the number shown beside "TAREFAS". */
    fun dueCount(all: List<Task>, today: LocalDate): Int =
        all.count { !it.done && it.parentId == null && (it.dueEpochDay ?: Long.MAX_VALUE) <= today.toEpochDay() }

    /** The first event that is with somebody, as judged by [personOf]. */
    fun <E, P> firstWithPerson(events: List<E>, personOf: (E) -> P?): Pair<E, P>? {
        for (e in events) personOf(e)?.let { return e to it }
        return null
    }

    /** "Novembro 2026". */
    fun monthTitle(month: java.time.YearMonth): String =
        month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.forLanguageTag("pt-PT"))).replaceFirstChar { it.uppercase() }
}
