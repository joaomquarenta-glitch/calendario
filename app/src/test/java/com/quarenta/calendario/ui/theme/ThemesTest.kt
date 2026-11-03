package com.quarenta.calendario.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemesTest {

    @Test
    fun offersAtLeastTwelvePastelColours() {
        assertTrue(Themes.pastels.size >= 12)
        val ids = (Themes.pastels + Themes.Light + Themes.Black).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(Themes.pastels.none { it.dark })
    }

    @Test
    fun systemFollowsThePhone() {
        ThemeState.selectedId = Themes.SYSTEM
        ThemeState.systemDark = true
        assertEquals(Themes.Black, ThemeState.current)
        ThemeState.systemDark = false
        assertEquals(Themes.Light, ThemeState.current)
        assertNull(Themes.byId(Themes.SYSTEM))
    }

    @Test
    fun chosenColourWinsOverThePhone() {
        ThemeState.systemDark = true
        ThemeState.selectedId = "menta"
        assertEquals("Menta", ThemeState.current.name)
        ThemeState.selectedId = "black"
        assertTrue(ThemeState.isDark)
        ThemeState.selectedId = Themes.SYSTEM
    }
}
