package com.quarenta.calendario.widget

import com.quarenta.calendario.data.Person
import com.quarenta.calendario.ui.agenda.Highlights
import com.quarenta.calendario.ui.agenda.withoutName
import java.time.LocalDate

/** The two lines of the "Próximo" widget, kept apart from Android so they can be tested. */
object NextWidgetText {

    data class Lines(val headline: String, val subline: String)

    /**
     * Same rules as the cards in the app: with a photo the name is left out, and a birthday
     * reads "34º aniversário · dia 5".
     */
    fun lines(
        title: String,
        birthday: Boolean,
        birthYear: Int?,
        day: LocalDate,
        today: LocalDate,
        time: String?,
        person: Person?,
        hasPhoto: Boolean,
    ): Lines {
        val whenText = (Highlights.shortDay(day, today) + (time?.let { " · $it" } ?: "")).replaceFirstChar { it.uppercase() }
        return when {
            birthday && person != null -> Lines(Highlights.birthdayLabel(birthYear, day, today).replaceFirstChar { it.uppercase() }, person.name.takeUnless { hasPhoto }.orEmpty())
            hasPhoto -> Lines(withoutName(title, person), whenText)
            else -> Lines(title, whenText)
        }
    }
}
