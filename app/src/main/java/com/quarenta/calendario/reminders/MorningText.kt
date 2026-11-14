package com.quarenta.calendario.reminders

import java.time.LocalTime

/** The words of the morning summary, kept free of Android code so they can be tested. */
object MorningText {

    data class Item(val title: String, val allDay: Boolean, val start: LocalTime?)

    data class Text(val title: String, val body: String)

    private fun hour(t: LocalTime): String = if (t.minute == 0) "${t.hour}h" else "${t.hour}h%02d".format(t.minute)

    /**
     * "Hoje tens 3 compromissos" + "O primeiro é às 10h: Dentista. Amanhã: Aniversário da Rita."
     * [birthdaysToday] and [birthdaysTomorrow] are event titles.
     */
    fun build(today: List<Item>, birthdaysToday: List<String>, birthdaysTomorrow: List<String>): Text {
        val timed = today.filter { !it.allDay && it.start != null }.sortedBy { it.start }
        val count = today.size
        val title = when {
            count == 0 && birthdaysToday.isEmpty() -> "Hoje tens o dia livre"
            count == 0 -> "Hoje há aniversários"
            count == 1 -> "Hoje tens 1 compromisso"
            else -> "Hoje tens $count compromissos"
        }
        val parts = mutableListOf<String>()
        timed.firstOrNull()?.let { first ->
            parts += if (timed.size == 1 && count == 1) "Às ${hour(first.start!!)}: ${first.title}."
            else "O primeiro é às ${hour(first.start!!)}: ${first.title}."
        } ?: today.firstOrNull()?.let { if (count == 1) parts += "${it.title}, todo o dia." }
        if (birthdaysToday.isNotEmpty()) parts += "Hoje: ${birthdaysToday.joinToString(", ")}."
        if (birthdaysTomorrow.isNotEmpty()) parts += "Amanhã: ${birthdaysTomorrow.joinToString(", ")}."
        if (parts.isEmpty()) parts += "Nada marcado. Bom dia!"
        return Text(title, parts.joinToString(" "))
    }
}
