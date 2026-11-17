package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventColorsTest {
    @Test fun withoutAccountColoursThePaletteIsOffered() {
        val c = EventColors.choices(emptyList())
        assertEquals(12, c.size)
        assertTrue(c.all { it.key == null })
    }

    @Test fun accountColoursKeepTheirKeysInRainbowOrder() {
        val c = EventColors.choices(listOf("9" to 0xFF3F51B5.toInt(), "11" to 0xFFD50000.toInt(), "8" to 0xFF616161.toInt(), "10" to 0xFF0B8043.toInt()))
        assertEquals(listOf("11", "10", "9", "8"), c.map { it.key })
    }

    @Test fun repeatedAccountColoursAreShownOnce() =
        assertEquals(1, EventColors.choices(listOf("1" to 0xFF7986CB.toInt(), "2" to 0x807986CB.toInt())).size)

    @Test fun sameIgnoresTransparency() {
        assertTrue(EventColors.same(0xFF34C759.toInt(), 0x0034C759))
        assertFalse(EventColors.same(0xFF34C759.toInt(), 0xFF34C758.toInt()))
    }

    @Test fun hues() {
        assertEquals(0f, EventColors.hue(0xFFFF0000.toInt()), 0.5f)
        assertEquals(120f, EventColors.hue(0xFF00FF00.toInt()), 0.5f)
        assertEquals(240f, EventColors.hue(0xFF0000FF.toInt()), 0.5f)
        assertTrue(EventColors.hue(0xFF808080.toInt()) > 360f)
    }
}
