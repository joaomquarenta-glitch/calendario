package com.quarenta.calendario.ui.components

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp

/**
 * Where a photo was tapped, so the person's card can grow out of that exact tile, the way
 * photos open on the iPhone. The tile writes it, the card reads it once.
 */
object PhotoZoom {
    private var personId: Long? = null
    private var bounds: Rect? = null

    fun remember(personId: Long, bounds: Rect) {
        this.personId = personId
        this.bounds = bounds
    }

    /** True when the card about to open for [personId] will grow out of a tile. */
    fun pending(personId: Long): Boolean = this.personId == personId && bounds != null

    /** The tile's place on screen if [personId] was the one tapped, and forgets it. */
    fun take(personId: Long): Rect? {
        val b = if (this.personId == personId) bounds else null
        this.personId = null
        bounds = null
        return b
    }

    /** The rectangle part way ([progress] 0 = the tile, 1 = the card's photo). */
    fun between(from: Rect, to: Rect, progress: Float): Rect = Rect(
        lerp(from.left, to.left, progress),
        lerp(from.top, to.top, progress),
        lerp(from.right, to.right, progress),
        lerp(from.bottom, to.bottom, progress),
    )
}
