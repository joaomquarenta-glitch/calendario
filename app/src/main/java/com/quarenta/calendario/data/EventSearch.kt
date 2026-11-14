package com.quarenta.calendario.data

import java.text.Normalizer
import java.time.Instant

/**
 * Finds appointments by any word in the title, place, notes or calendar name, ignoring
 * accents and capitals: "sao joao" finds "São João", "dent" finds "Dentista".
 */
object EventSearch {

    data class Result(val upcoming: List<CalendarEvent>, val past: List<CalendarEvent>) {
        val isEmpty get() = upcoming.isEmpty() && past.isEmpty()
    }

    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9@.]+".toRegex(), " ")
            .trim()

    fun matches(event: CalendarEvent, query: String): Boolean {
        val words = fold(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return false
        val haystack = " " + fold(
            listOfNotNull(event.title, event.location, event.description, event.sources.joinToString(" ")).joinToString(" "),
        ) + " "
        // Every word must appear, each at the start of a word, so "ana" finds "Ana" but not "banana".
        return words.all { haystack.contains(" $it") }
    }

    /** Upcoming soonest first, past most recent first; at most [limit] of each. */
    fun search(events: List<CalendarEvent>, query: String, now: Instant, limit: Int = 60): Result {
        val hits = events.filter { matches(it, query) }
        val upcoming = hits.filter { it.end > now }.sortedBy { it.start }.take(limit)
        val past = hits.filter { it.end <= now }.sortedByDescending { it.start }.take(limit)
        return Result(upcoming, past)
    }
}
