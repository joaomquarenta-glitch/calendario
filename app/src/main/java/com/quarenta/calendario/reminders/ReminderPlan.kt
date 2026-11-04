package com.quarenta.calendario.reminders

import java.text.Normalizer

/** One reminder for one occurrence of an event: "at [alertAt], remind me of [title]". */
data class ReminderAlert(
    val eventId: Long,
    val title: String,
    val beginMs: Long,
    val endMs: Long,
    val allDay: Boolean,
    val location: String?,
    val color: Int,
    val alertAtMs: Long,
) {
    /** Same event occurrence and same alert time: the phone sometimes lists a reminder twice. */
    val key: String get() = "$eventId@$beginMs@$alertAtMs"
}

/** Which reminders to show now, and when to wake up next. No Android code, so it can be tested. */
object ReminderPlan {

    /** Reminders missed while the phone was off are still shown if they are at most this late. */
    const val GRACE_MS = 30 * 60_000L

    /**
     * Reminders to show at [nowMs]: due after [lastHandledMs] and not more than [GRACE_MS] late,
     * and only while the event has not ended. Duplicates are shown once.
     */
    fun due(alerts: List<ReminderAlert>, lastHandledMs: Long, nowMs: Long): List<ReminderAlert> =
        alerts.filter {
            it.alertAtMs > lastHandledMs && it.alertAtMs <= nowMs &&
                nowMs - it.alertAtMs <= GRACE_MS && it.endMs > nowMs
        }.distinctBy { it.key }.sortedBy { it.alertAtMs }

    /** The time of the next reminder after [afterMs], or null when there is none. */
    fun next(alerts: List<ReminderAlert>, afterMs: Long): Long? =
        alerts.asSequence().map { it.alertAtMs }.filter { it > afterMs }.minOrNull()

    /** "Adiar" choices, in minutes. */
    val snoozeMinutes = listOf(5, 10, 15, 30, 60)

    fun snoozeLabel(minutes: Int): String = if (minutes < 60) "$minutes min" else "${minutes / 60} h"

    /** Ready-made messages to send to the person the event is with. */
    val quickMessages = listOf(
        "Já estou a caminho.",
        "Vou chegar uns minutos atrasado(a).",
        "Já cheguei, estou à tua espera.",
        "Não vou conseguir ir, desculpa. Podemos remarcar?",
    )

    /** "em 15 min", "agora", "começou há 5 min" for the reminder screen. */
    fun whenText(beginMs: Long, nowMs: Long, allDay: Boolean): String {
        if (allDay) return "Hoje, todo o dia"
        val mins = Math.floorDiv(beginMs - nowMs + 30_000, 60_000L)
        return when {
            mins in -1..1 -> "Agora"
            mins > 1 && mins < 60 -> "Daqui a $mins min"
            mins >= 60 && mins < 24 * 60 -> {
                val h = mins / 60
                val m = mins % 60
                if (m == 0L) "Daqui a $h h" else "Daqui a $h h $m min"
            }
            mins >= 24 * 60 -> "Daqui a ${mins / (24 * 60)} dia(s)"
            else -> "Começou há ${-mins} min"
        }
    }
}

/**
 * Finds which contact an event is with, from its title ("Jantar com Ana Sousa")
 * or from the e-mail of a guest. Matches whole words only, ignoring accents and case.
 */
object PersonMatch {

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    /**
     * The id of the contact whose name appears in [title]. The full name wins; otherwise
     * first + last name ("Ana Sousa" for "Ana Maria Sousa"). A first name alone is not enough,
     * because "Ana" would match too many people. With two equally good matches, nobody is chosen.
     */
    fun inTitle(title: String, people: List<Pair<Long, String>>): Long? {
        val t = " ${normalize(title)} "
        data class Hit(val id: Long, val length: Int)
        val hits = people.mapNotNull { (id, name) ->
            val words = normalize(name).split(' ').filter { it.isNotEmpty() }
            if (words.size < 2) return@mapNotNull null
            val full = words.joinToString(" ")
            val short = "${words.first()} ${words.last()}"
            when {
                t.contains(" $full ") -> Hit(id, full.length)
                t.contains(" $short ") -> Hit(id, short.length)
                else -> null
            }
        }
        val best = hits.maxOfOrNull { it.length } ?: return null
        val top = hits.filter { it.length == best }.map { it.id }.distinct()
        return top.singleOrNull()
    }
}
