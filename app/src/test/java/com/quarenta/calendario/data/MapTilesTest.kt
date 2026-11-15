package com.quarenta.calendario.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapTilesTest {
    @Test
    fun theWorldCentreIsTheMiddleOfTheMap() {
        val (x, y) = MapTiles.worldPixel(0.0, 0.0, 1)
        assertEquals(256.0, x, 0.001)
        assertEquals(256.0, y, 0.001)
    }

    @Test
    fun aPlaceFallsInTheRightTile() {
        // Central Lisbon at zoom 16 is tile 31104 / 25111.
        val (x, y) = MapTiles.worldPixel(38.7223, -9.1393, 16)
        assertEquals(31104, (x / 256).toInt())
        assertEquals(25111, (y / 256).toInt())
    }

    @Test
    fun theTilesCoverThePictureWithNoGaps() {
        val tiles = MapTiles.cover(38.7223, -9.1393, 16, 768, 384)
        assertTrue(tiles.size in 6..12)
        assertTrue(tiles.any { it.drawX <= 0 && it.drawY <= 0 })
        assertTrue(tiles.any { it.drawX + 256 >= 768 && it.drawY + 256 >= 384 })
        assertTrue(tiles.all { it.drawX > -256 && it.drawY > -256 && it.drawX < 768 && it.drawY < 384 })
    }
}
