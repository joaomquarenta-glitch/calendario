package com.quarenta.calendario.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class TabMotionTest {
    @Test
    fun forwardComesFromTheRightAndBackFromTheLeft() {
        assertEquals(1, TabMotion.direction(0, 2))
        assertEquals(-1, TabMotion.direction(3, 1))
        assertEquals(0, TabMotion.direction(1, 1))
    }

    @Test
    fun theGlideIsShort() {
        assertEquals(180, TabMotion.offset(1080, 1))
        assertEquals(-180, TabMotion.offset(1080, -1))
        assertEquals(0, TabMotion.offset(1080, 0))
    }
}
