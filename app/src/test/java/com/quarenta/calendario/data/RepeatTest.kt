package com.quarenta.calendario.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatTest {
    private val wednesday = LocalDate.of(2030, 3, 13)

    @Test fun writesRules() {
        assertNull(Repeat.rrule(Repeat.None, wednesday))
        assertEquals("FREQ=DAILY", Repeat.rrule(Repeat.Daily, wednesday))
        assertEquals("FREQ=WEEKLY;BYDAY=WE", Repeat.rrule(Repeat.Weekly, wednesday))
        assertEquals("FREQ=MONTHLY;BYMONTHDAY=13", Repeat.rrule(Repeat.Monthly, wednesday))
        assertEquals("FREQ=YEARLY", Repeat.rrule(Repeat.Yearly, wednesday))
        assertEquals("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", Repeat.rrule(Repeat.Weekdays, wednesday))
        assertEquals("FREQ=WEEKLY;COUNT=3", Repeat.rrule(Repeat.Custom, wednesday, "FREQ=WEEKLY;COUNT=3"))
    }

    @Test fun readsBackWhatItWrites() {
        Repeat.choices.forEach { assertEquals(it, Repeat.of(Repeat.rrule(it, wednesday))) }
    }

    @Test fun readsRulesFromOtherApps() {
        assertEquals(Repeat.Yearly, Repeat.of("FREQ=YEARLY;WKST=SU"))
        assertEquals(Repeat.Weekly, Repeat.of("RRULE:FREQ=WEEKLY;WKST=MO;BYDAY=TH"))
        assertEquals(Repeat.Weekdays, Repeat.of("FREQ=WEEKLY;BYDAY=FR,MO,TH,TU,WE"))
        assertEquals(Repeat.Custom, Repeat.of("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO"))
        assertEquals(Repeat.Custom, Repeat.of("FREQ=DAILY;UNTIL=20300401T000000Z"))
        assertEquals(Repeat.Custom, Repeat.of("FREQ=MONTHLY;BYDAY=2TU"))
        assertEquals(Repeat.None, Repeat.of(""))
    }
}
