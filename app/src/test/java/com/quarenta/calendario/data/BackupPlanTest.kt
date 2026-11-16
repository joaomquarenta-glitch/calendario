package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupPlanTest {

    @Test
    fun knownPartsGoBackToTheirPlace() {
        assertEquals("databases/calendario.db", BackupPlan.target(BackupPlan.databaseEntry()))
        assertEquals("shared_prefs/forty.xml", BackupPlan.target("prefs/forty.xml"))
        assertEquals("shared_prefs/gift_ideas.xml", BackupPlan.target(BackupPlan.prefsEntry("gift_ideas")))
        assertEquals("files/people-photos/ab12cd.jpg", BackupPlan.target("photos/ab12cd.jpg"))
    }

    @Test
    fun anythingElseIsIgnored() {
        assertNull(BackupPlan.target("photos/../../databases/x.db"))
        assertNull(BackupPlan.target("photos/.hidden"))
        assertNull(BackupPlan.target("photos/a/b.jpg"))
        assertNull(BackupPlan.target("prefs/other_app.xml"))
        assertNull(BackupPlan.target("prefs/forty.txt"))
        assertNull(BackupPlan.target("db/other.db"))
        assertNull(BackupPlan.target("/etc/passwd"))
        assertNull(BackupPlan.target(""))
    }

    @Test
    fun recognisesItsOwnFiles() {
        assertTrue(BackupPlan.isBackup(BackupPlan.SIGNATURE + "\n2026-11-15"))
        assertFalse(BackupPlan.isBackup("outra coisa"))
        assertFalse(BackupPlan.isBackup(null))
        assertEquals("FortyCalendar-copia-2026-11-15.zip", BackupPlan.fileName(LocalDate.of(2026, 11, 15)))
    }
}
