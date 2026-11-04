package com.quarenta.calendario.ui.agenda

import com.quarenta.calendario.reminders.PersonMatch
import java.net.URLEncoder
import java.time.LocalDate

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

    private val monthsPt = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    /** "hoje", "amanhã", "dia 5", or "dia 5 dez" when it is not this month. */
    fun shortDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "hoje"
        today.plusDays(1) -> "amanhã"
        else -> if (date.month == today.month && date.year == today.year) "dia ${date.dayOfMonth}"
        else "dia ${date.dayOfMonth} ${monthsPt[date.monthValue - 1]}"
    }

    /** "34º aniversário · dia 5", or "Aniversário · dia 5" when the year of birth is unknown. */
    fun birthdayLabel(birthYear: Int?, date: LocalDate, today: LocalDate): String {
        val age = birthYear?.let { date.year - it }?.takeIf { it in 1..130 }
        return (if (age != null) "${age}º aniversário" else "Aniversário") + " · " + shortDay(date, today)
    }

    /** The key of an event's plan: one per occurrence, so a birthday gets a new plan each year. */
    fun planKey(eventId: Long, date: LocalDate): String = "$eventId@$date"

    private fun enc(text: String) = URLEncoder.encode(text.trim(), "UTF-8")

    /** Opens Waze and starts navigating to [place] (the website if Waze is not installed). */
    fun wazeUrl(place: String) = "https://waze.com/ul?q=${enc(place)}&navigate=yes"

    /** Google Maps directions to [place]. */
    fun mapsUrl(place: String) = "https://www.google.com/maps/dir/?api=1&destination=${enc(place)}"
}
