package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.MonthDay

class ContactFormatTest {

    @Test
    fun readsTheDateFormatsPhonesUse() {
        assertEquals(MonthDay.of(3, 12) to 1980, ContactFormat.parseDate("1980-03-12"))
        assertEquals(MonthDay.of(3, 12) to null, ContactFormat.parseDate("--03-12"))
        assertEquals(MonthDay.of(3, 12) to 1980, ContactFormat.parseDate("12/03/1980"))
        assertEquals(MonthDay.of(3, 12) to 1980, ContactFormat.parseDate("19800312"))
        assertNull(ContactFormat.parseDate("ontem"))
    }

    @Test
    fun showsDatesInPortuguese() {
        assertEquals("12 mar 1980", ContactFormat.formatDate("1980-03-12"))
        assertEquals("3 dez", ContactFormat.formatDate("--12-03"))
    }

    @Test
    fun turnsTypedDatesIntoContactDates() {
        assertEquals("1980-03-12", ContactFormat.dateForContacts("12/03/1980"))
        assertEquals("1980-03-12", ContactFormat.dateForContacts("12-3-1980"))
        assertEquals("--03-12", ContactFormat.dateForContacts("12/03"))
        assertEquals("1980-03-12", ContactFormat.dateForContacts("12.03.80"))
        assertNull(ContactFormat.dateForContacts("31/02/2000"))
        assertNull(ContactFormat.dateForContacts("amanhã"))
        assertEquals("12/03/1980", ContactFormat.dateForEditing("1980-03-12"))
        assertEquals("12/03", ContactFormat.dateForEditing("--03-12"))
    }

    @Test
    fun buildsWhatsAppNumbers() {
        assertEquals("351912345678", ContactFormat.whatsAppNumber("912345678"))
        assertEquals("351961112233", ContactFormat.whatsAppNumber("+351 961 112 233"))
        assertEquals("447700900123", ContactFormat.whatsAppNumber("00 44 7700 900123"))
        assertNull(ContactFormat.whatsAppNumber("112"))
    }

    @Test
    fun initialsSkipEmoji() {
        assertEquals("MS", ContactFormat.initials("Maria Silva"))
        assertEquals("A", ContactFormat.initials("〽️ ana"))
        assertEquals("", ContactFormat.initials("〽️"))
    }
}
