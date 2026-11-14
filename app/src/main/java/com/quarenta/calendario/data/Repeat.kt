package com.quarenta.calendario.data

import java.time.DayOfWeek
import java.time.LocalDate

/** The repeat choices offered when creating or editing an appointment. */
enum class Repeat(val label: String) {
    None("Não repete"),
    Daily("Todos os dias"),
    Weekdays("Dias úteis"),
    Weekly("Todas as semanas"),
    Monthly("Todos os meses"),
    Yearly("Todos os anos"),
    /** A rule from another app that this screen does not offer; it is kept as it is. */
    Custom("Personalizado");

    companion object {
        val choices = listOf(None, Daily, Weekdays, Weekly, Monthly, Yearly)

        private val days = mapOf(
            DayOfWeek.MONDAY to "MO", DayOfWeek.TUESDAY to "TU", DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH", DayOfWeek.FRIDAY to "FR", DayOfWeek.SATURDAY to "SA", DayOfWeek.SUNDAY to "SU",
        )

        /** The RRULE for [repeat] starting on [start]; null for none. Custom keeps [existing]. */
        fun rrule(repeat: Repeat, start: LocalDate, existing: String? = null): String? = when (repeat) {
            None -> null
            Daily -> "FREQ=DAILY"
            Weekdays -> "FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR"
            Weekly -> "FREQ=WEEKLY;BYDAY=${days.getValue(start.dayOfWeek)}"
            Monthly -> "FREQ=MONTHLY;BYMONTHDAY=${start.dayOfMonth}"
            Yearly -> "FREQ=YEARLY"
            Custom -> existing
        }

        /** Which choice an existing rule is; rules with an end, a count or an interval are Custom. */
        fun of(rrule: String?): Repeat {
            if (rrule.isNullOrBlank()) return None
            val parts = rrule.removePrefix("RRULE:").split(';').mapNotNull {
                val eq = it.indexOf('=')
                if (eq > 0) it.substring(0, eq).uppercase() to it.substring(eq + 1).uppercase() else null
            }.toMap()
            if (parts.keys.any { it in setOf("UNTIL", "COUNT") } || (parts["INTERVAL"] ?: "1") != "1") return Custom
            val byDay = parts["BYDAY"]
            val others = parts.keys - setOf("FREQ", "BYDAY", "BYMONTHDAY", "WKST", "INTERVAL")
            if (others.isNotEmpty()) return Custom
            return when (parts["FREQ"]) {
                "DAILY" -> if (byDay == null) Daily else Custom
                "WEEKLY" -> when {
                    byDay == null -> Weekly
                    byDay.split(',').toSet() == setOf("MO", "TU", "WE", "TH", "FR") -> Weekdays
                    !byDay.contains(',') -> Weekly
                    else -> Custom
                }
                "MONTHLY" -> if (byDay == null) Monthly else Custom
                "YEARLY" -> if (byDay == null && parts["BYMONTHDAY"] == null) Yearly else Custom
                else -> Custom
            }
        }
    }
}

/** When editing a repeating appointment: change only this occurrence, or the whole series. */
enum class EditScope { ThisOne, All }
