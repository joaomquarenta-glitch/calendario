package com.quarenta.calendario.ui.agenda

import com.quarenta.calendario.reminders.PersonMatch

/** Text rules for the big agenda cards, kept free of Android code so they can be tested. */
object Highlights {

    /** Birthdays from the contacts' birthday calendar, or events called "... Aniversário" / "... anos". */
    fun isBirthday(title: String, sources: List<String>): Boolean {
        val t = PersonMatch.normalize(title)
        val fromBirthdayCalendar = sources.any {
            val s = PersonMatch.normalize(it)
            "birthday" in s || "aniversario" in s
        }
        return fromBirthdayCalendar || Regex("\\b(aniversario|birthday|parabens|faz \\d+ anos)\\b").containsMatchIn(t)
    }

    /**
     * The events to show as big cards: those still to come (or happening now) from [fromMs],
     * in order, at most [max]. All-day events count for their whole day.
     */
    fun <T> upcoming(items: List<T>, fromMs: Long, endMs: (T) -> Long, startMs: (T) -> Long, max: Int = 20): List<T> =
        items.filter { endMs(it) > fromMs }.sortedBy(startMs).take(max)
}
