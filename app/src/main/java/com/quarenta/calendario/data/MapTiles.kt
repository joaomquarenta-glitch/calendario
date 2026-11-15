package com.quarenta.calendario.data

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/** The square map tiles (256 px, Web Mercator) that cover a small picture centred on a place. */
object MapTiles {
    const val TILE = 256

    data class Tile(val x: Int, val y: Int, val drawX: Int, val drawY: Int)

    /** Where a place sits on the whole-world map at [zoom], in pixels. */
    fun worldPixel(lat: Double, lon: Double, zoom: Int): Pair<Double, Double> {
        val size = TILE.toDouble() * (1 shl zoom)
        val rad = lat.coerceIn(-85.0511, 85.0511) * PI / 180
        val x = (lon + 180) / 360 * size
        val y = (1 - ln(tan(rad) + 1 / cos(rad)) / PI) / 2 * size
        return x to y
    }

    /** Every tile needed for a [width] × [height] picture centred on the place, and where each one is drawn. */
    fun cover(lat: Double, lon: Double, zoom: Int, width: Int, height: Int): List<Tile> {
        val (cx, cy) = worldPixel(lat, lon, zoom)
        val left = cx - width / 2.0
        val top = cy - height / 2.0
        val max = 1 shl zoom
        val firstX = floor(left / TILE).toInt()
        val lastX = floor((left + width - 1) / TILE).toInt()
        val firstY = floor(top / TILE).toInt()
        val lastY = floor((top + height - 1) / TILE).toInt()
        val tiles = mutableListOf<Tile>()
        for (ty in firstY..lastY) {
            if (ty !in 0 until max) continue
            for (tx in firstX..lastX) {
                val wrapped = ((tx % max) + max) % max
                tiles += Tile(wrapped, ty, (tx * TILE - left).toInt(), (ty * TILE - top).toInt())
            }
        }
        return tiles
    }
}
