package com.quarenta.calendario.data

import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId

/**
 * Merges the same appointment coming from several calendars into one card.
 *
 * Two events are the same when they are on the same day, have the same kind
 * (all-day or timed, and for timed the same start minute) and their titles match
 * after cleaning: "Bárbara Magalhães" and "Bárbara Magalhães - Aniversário" are
 * one birthday; "Dentista" in Google and in Outlook at 16:00 are one appointment.
 */
object Dedup {

    private val noiseWords = setOf(
        "aniversario", "aniversário", "anos", "birthday", "bday", "parabens", "parabéns",
        "de", "do", "da", "dos", "das", "no", "na", "nos", "nas", "em", "com", "o", "a", "e",
    )

    fun normalize(title: String): String {
        val noAccents = Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return noAccents
            .replace("[^a-z0-9 ]".toRegex(), " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in noiseWords }
            .joinToString(" ")
    }

    private fun titlesMatch(a: String, b: String): Boolean {
        if (a.isEmpty() || b.isEmpty()) return false
        if (a == b) return true
        val (short, long) = if (a.length <= b.length) a to b else b to a
        // "barbara magalhaes" inside "barbara magalhaes jantar" counts; tiny words do not.
        return short.length >= 5 && (" $long ").contains(" $short ")
    }

    private fun sameSlot(a: CalendarEvent, b: CalendarEvent, zone: ZoneId): Boolean {
        if (a.allDay != b.allDay) return false
        return if (a.allDay) {
            a.start.atZone(zone).toLocalDate() == b.start.atZone(zone).toLocalDate()
        } else {
            minute(a.start) == minute(b.start)
        }
    }

    private fun minute(i: Instant) = i.epochSecond / 60

    fun merge(events: List<CalendarEvent>, zone: ZoneId = ZoneId.systemDefault()): List<CalendarEvent> {
        val result = mutableListOf<CalendarEvent>()
        val keys = mutableListOf<String>()
        for (e in events) {
            val key = normalize(e.title)
            val idx = result.indices.firstOrNull { i ->
                sameSlot(result[i], e, zone) && titlesMatch(keys[i], key) && result[i].id != e.id
            }
            if (idx == null) {
                result += e
                keys += key
            } else {
                val kept = result[idx]
                // Keep the richer copy as the main one (editable, longer title, more details).
                val (main, other) = if (score(e) > score(kept)) e to kept else kept to e
                result[idx] = main.copy(
                    location = main.location ?: other.location,
                    description = main.description ?: other.description,
                    sources = (kept.sources + e.sources).distinct(),
                    duplicateIds = (kept.duplicateIds + e.duplicateIds + other.id).distinct() - main.id,
                )
                keys[idx] = normalize(main.title)
            }
        }
        return result
    }

    private fun score(e: CalendarEvent): Int =
        (if (e.canEdit) 1000 else 0) + e.title.length +
            (if (e.location != null) 50 else 0) + (if (e.description != null) 50 else 0)
}
