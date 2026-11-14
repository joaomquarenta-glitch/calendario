package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarChoiceTest {
    @Test fun hidingAndShowingAgain() {
        val hidden = CalendarChoice.toggle(emptySet(), 7, shown = false)
        assertEquals(setOf(7L), hidden)
        assertEquals(emptySet<Long>(), CalendarChoice.toggle(hidden, 7, shown = true))
        assertEquals(setOf(7L), CalendarChoice.toggle(hidden, 7, shown = false))
    }

    @Test fun groupsByAccountPrimaryFirst() {
        val cals = listOf(
            CalendarInfo(1, "Trabalho", "zeta@example.com", 0, false),
            CalendarInfo(2, "Feriados", "alfa@example.com", 0, false),
            CalendarInfo(3, "alfa@example.com", "alfa@example.com", 0, true),
            CalendarInfo(4, "Local", "", 0, false),
        )
        val groups = CalendarChoice.byAccount(cals)
        assertEquals(listOf("alfa@example.com", "Neste telemóvel", "zeta@example.com"), groups.map { it.first })
        assertEquals(listOf(3L, 2L), groups[0].second.map { it.id })
    }
}
