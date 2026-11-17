package com.quarenta.calendario.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {
    @Test fun phoneUprightKeepsTheTabBar() {
        assertFalse(Adaptive.useRail(360))
        assertFalse(Adaptive.useRail(411))
        assertFalse(Adaptive.useRail(599))
    }

    @Test fun sidewaysFoldableAndTabletUseTheRail() {
        assertTrue(Adaptive.useRail(600)) // small tablet / open foldable
        assertTrue(Adaptive.useRail(891)) // phone on its side
        assertTrue(Adaptive.useRail(1280)) // large tablet
    }
}
