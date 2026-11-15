package com.quarenta.calendario.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The rules for tasks with a time, a reminder, a repeat and steps. No Android here, so it is tested. */
object TaskRules {

    /** Repeats offered, as id to label. */
    val repeats = listOf("daily" to "Todos os dias", "weekly" to "Todas as semanas", "monthly" to "Todos os meses", "yearly" to "Todos os anos")

    /** Reminders offered, as minutes before to label. */
    val reminders = listOf(0 to "Na hora", 15 to "15 min antes", 60 to "1 hora antes", 24 * 60 to "1 dia antes")

    /** Without a time, a task's reminder goes off at nine in the morning. */
    const val DEFAULT_MINUTES = 9 * 60

    fun repeatLabel(id: String?): String? = repeats.firstOrNull { it.first == id }?.second

    fun reminderLabel(minutes: Int?): String? = reminders.firstOrNull { it.first == minutes }?.second

    /** The next day a repeating task is due after [due]; 31 January monthly gives 28 or 29 February. */
    fun nextDue(due: LocalDate, repeat: String?): LocalDate = when (repeat) {
        "daily" -> due.plusDays(1)
        "weekly" -> due.plusWeeks(1)
        "monthly" -> due.plusMonths(1)
        "yearly" -> due.plusYears(1)
        else -> due
    }

    /**
     * Ticking a task. A repeating one is not finished: it moves to its next day after today (never
     * to a day already gone or to today, so a task forgotten for a week comes back once, not seven times).
     */
    fun complete(task: Task, today: LocalDate): Task {
        if (task.done) return task.copy(done = false)
        val due = task.dueEpochDay?.let(LocalDate::ofEpochDay)
        if (task.repeat == null || due == null) return task.copy(done = true)
        var next = nextDue(due, task.repeat)
        while (!next.isAfter(today)) next = nextDue(next, task.repeat)
        if (!next.isAfter(due)) return task.copy(done = true)
        return task.copy(dueEpochDay = next.toEpochDay())
    }

    /** When the reminder should go off, or null when there is none (no reminder, no date, already done). */
    fun reminderAt(task: Task, zone: ZoneId): Instant? {
        val before = task.remindBefore ?: return null
        val day = task.dueEpochDay ?: return null
        if (task.done) return null
        val minutes = (task.timeMinutes ?: DEFAULT_MINUTES).coerceIn(0, 24 * 60 - 1)
        return LocalDate.ofEpochDay(day).atTime(LocalTime.of(minutes / 60, minutes % 60))
            .atZone(zone).toInstant().minusSeconds(before * 60L)
    }

    /** "09:30". */
    fun timeText(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)

    /** "2/3" for a task with three steps, two done; null without steps. */
    fun progress(steps: List<Task>): String? =
        if (steps.isEmpty()) null else "${steps.count { it.done }}/${steps.size}"
}
