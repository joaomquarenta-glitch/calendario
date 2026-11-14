package com.quarenta.calendario.ui.components

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoZoomTest {
    private val tile = Rect(40f, 600f, 340f, 975f)
    private val card = Rect(0f, 0f, 1080f, 1200f)

    @Test fun startsAtTheTileAndEndsAtTheCard() {
        assertEquals(tile, PhotoZoom.between(tile, card, 0f))
        assertEquals(card, PhotoZoom.between(tile, card, 1f))
        assertEquals(Rect(20f, 300f, 710f, 1087.5f), PhotoZoom.between(tile, card, 0.5f))
    }

    @Test fun theTileIsUsedOnceAndOnlyForThatPerson() {
        PhotoZoom.remember(7L, tile)
        assertFalse(PhotoZoom.pending(8L))
        assertTrue(PhotoZoom.pending(7L))
        assertEquals(tile, PhotoZoom.take(7L))
        assertNull(PhotoZoom.take(7L))

        PhotoZoom.remember(7L, tile)
        assertNull(PhotoZoom.take(8L))
        assertNull(PhotoZoom.take(7L))
    }
}
