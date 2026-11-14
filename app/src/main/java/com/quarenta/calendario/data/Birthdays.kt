package com.quarenta.calendario.data

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

/** The rules for birthdays, kept free of Android code so they can be tested. */
object Birthdays {

    data class Entry(
        val personId: Long,
        val name: String,
        val date: LocalDate,
        val daysLeft: Int,
        /** How old they turn on [date], when the year of birth is known. */
        val turns: Int?,
    )

    /** The next time [day] comes round, today included. 29 February is kept on 28 February in other years. */
    fun nextDate(day: MonthDay, today: LocalDate): LocalDate {
        fun inYear(y: Int): LocalDate = if (day.isValidYear(y)) day.atYear(y) else LocalDate.of(y, 2, 28)
        val thisYear = inYear(today.year)
        return if (thisYear.isBefore(today)) inYear(today.year + 1) else thisYear
    }

    /** Everyone's next birthday, soonest first. */
    fun upcoming(people: List<Triple<Long, String, Pair<MonthDay, Int?>>>, today: LocalDate): List<Entry> =
        people.map { (id, name, bday) ->
            val date = nextDate(bday.first, today)
            Entry(
                personId = id, name = name, date = date,
                daysLeft = ChronoUnit.DAYS.between(today, date).toInt(),
                turns = bday.second?.let { date.year - it }?.takeIf { it in 1..130 },
            )
        }.sortedWith(compareBy({ it.daysLeft }, { it.name }))

    /** "Hoje!", "Amanhã", "Faltam 5 dias", "Faltam 3 semanas", "Faltam 4 meses". */
    fun countdown(daysLeft: Int): String = when {
        daysLeft <= 0 -> "Hoje!"
        daysLeft == 1 -> "Amanhã"
        daysLeft < 14 -> "Faltam $daysLeft dias"
        daysLeft < 60 -> "Faltam ${daysLeft / 7} semanas"
        else -> "Faltam ${daysLeft / 30} meses"
    }

    /** Ready-made messages to send on the day. */
    fun wishes(firstName: String, turns: Int?): List<String> {
        val name = firstName.trim().ifEmpty { null }
        val hi = name?.let { ", $it" }.orEmpty()
        return listOfNotNull(
            "Parabéns$hi! 🎉 Que tenhas um dia muito feliz!",
            turns?.let { "Muitos parabéns$hi! Que os teus $it anos sejam incríveis 🎂" },
            "Feliz aniversário$hi! Um grande abraço 🤗",
            "Parabéns$hi! Hoje é dia de festa, aproveita muito! 🥳",
        )
    }
}
