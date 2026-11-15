package com.quarenta.calendario.data

/** What links a person to the rest of the app: appointments and tasks that mention their name. */
object PersonLinks {

    /** True when [text] names the person: the full name, first and last name, or both words anywhere. */
    fun mentions(name: String, text: String): Boolean {
        val tokens = Dedup.normalize(name).split(' ').filter { it.length >= 2 }
        if (tokens.isEmpty()) return false
        val full = tokens.joinToString(" ")
        val firstLast = if (tokens.size >= 2) "${tokens.first()} ${tokens.last()}" else full
        val t = " " + Dedup.normalize(text) + " "
        return t.contains(" $full ") || t.contains(" $firstLast ") ||
            (tokens.size >= 2 && t.contains(" ${tokens.first()} ") && t.contains(" ${tokens.last()} "))
    }

    /** Open tasks first, then done ones, keeping the order they came in. */
    fun tasksWith(name: String, tasks: List<Task>): List<Task> =
        tasks.filter { mentions(name, it.title + " " + (it.note ?: "")) }.sortedBy { it.done }

    /** A new task's title, with the person's name added so it shows up on their card. */
    fun taskTitle(text: String, name: String): String {
        val clean = text.trim()
        if (clean.isEmpty() || mentions(name, clean)) return clean
        return "$clean — ${name.trim()}"
    }

    /** How long ago something was, in words: "hoje", "ontem", "há 5 dias", "há 3 semanas", "há 2 meses", "há 1 ano". */
    fun sinceText(days: Long): String = when {
        days <= 0 -> "hoje"
        days == 1L -> "ontem"
        days < 14 -> "há $days dias"
        days < 60 -> "há ${days / 7} semanas"
        days < 365 -> "há ${days / 30} meses"
        days < 730 -> "há 1 ano"
        else -> "há ${days / 365} anos"
    }
}
