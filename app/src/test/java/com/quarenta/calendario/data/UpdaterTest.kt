package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {

    @Test
    fun readsVersionTags() {
        assertEquals(listOf(0, 3, 12), Updater.parseVersion("v0.3.12"))
        assertEquals(listOf(1, 0), Updater.parseVersion("1.0"))
        assertNull(Updater.parseVersion("v0.3.beta"))
    }

    @Test
    fun comparesNumbersNotText() {
        assertTrue(Updater.isNewer("v0.3.10", "0.3.9"))
        assertTrue(Updater.isNewer("v0.4.1", "0.3.99"))
        assertTrue(Updater.isNewer("v1.0", "0.9.50"))
    }

    @Test
    fun sameOrOlderIsNotAnUpdate() {
        assertFalse(Updater.isNewer("v0.3.12", "0.3.12"))
        assertFalse(Updater.isNewer("v0.2.30", "0.3.1"))
        assertFalse(Updater.isNewer("rubbish", "0.3.1"))
    }
}
