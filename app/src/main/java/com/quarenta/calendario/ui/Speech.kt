package com.quarenta.calendario.ui

import java.time.LocalDate

/**
 * What TalkBack says for things that show only a number or a shape on screen,
 * so a person who cannot see the screen hears the whole meaning.
 */
object Speech {
    /** A day in the calendar: "Hoje, terça-feira, 17 de novembro, 2 compromissos". */
    fun day(date: LocalDate, today: LocalDate, events: Int): String {
        val parts = mutableListOf<String>()
        when (date) {
            today -> parts += "Hoje"
            today.plusDays(1) -> parts += "Amanhã"
            today.minusDays(1) -> parts += "Ontem"
        }
        val year = if (date.year != today.year) " de ${date.year}" else ""
        parts += "${weekdayLong(date)}, ${date.dayOfMonth} de ${monthLong(date)}$year"
        parts += events(events)
        return parts.joinToString(", ")
    }

    fun events(count: Int): String = when (count) {
        0 -> "sem compromissos"
        1 -> "1 compromisso"
        else -> "$count compromissos"
    }

    /** The round tick next to a task. */
    fun taskState(done: Boolean): String = if (done) "Feita" else "Por fazer"
}
