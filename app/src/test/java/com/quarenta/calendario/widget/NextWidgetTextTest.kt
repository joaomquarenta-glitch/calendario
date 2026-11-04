package com.quarenta.calendario.widget

import com.quarenta.calendario.data.Person
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class NextWidgetTextTest {
    private val today = LocalDate.of(2030, 3, 1)
    private val rita = Person(1, "k1", "Rita Marques", null, null, false)

    @Test fun birthdayWithPhotoShowsOnlyTheOccasion() {
        val l = NextWidgetText.lines("Aniversário Rita Marques", true, 1996, LocalDate.of(2030, 3, 5), today, null, rita, hasPhoto = true)
        assertEquals("34º aniversário · dia 5", l.headline)
        assertEquals("", l.subline)
    }

    @Test fun birthdayWithoutPhotoKeepsTheName() {
        val l = NextWidgetText.lines("Aniversário Rita Marques", true, 1996, LocalDate.of(2030, 3, 5), today, null, rita, hasPhoto = false)
        assertEquals("Rita Marques", l.subline)
    }

    @Test fun photoDropsTheNameFromTheTitle() {
        val l = NextWidgetText.lines("Jantar com Rita Marques", false, null, today, today, "20:30", rita, hasPhoto = true)
        assertEquals("Jantar", l.headline)
        assertEquals("Hoje · 20:30", l.subline)
    }

    @Test fun noPersonKeepsTheTitle() {
        val l = NextWidgetText.lines("Dentista", false, null, today.plusDays(1), today, "09:00", null, hasPhoto = false)
        assertEquals("Dentista", l.headline)
        assertEquals("Amanhã · 09:00", l.subline)
    }
}
