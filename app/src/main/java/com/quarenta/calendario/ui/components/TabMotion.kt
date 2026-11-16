package com.quarenta.calendario.ui.components

/** Which way the tabs slide, so moving along the tab bar feels like turning pages. */
object TabMotion {
    /** +1 when going to a tab further right, -1 when going back, 0 for the same tab. */
    fun direction(from: Int, to: Int): Int = when {
        to > from -> 1
        to < from -> -1
        else -> 0
    }

    /** Where the new tab starts: a short glide of a sixth of the width, never a full swipe. */
    fun offset(width: Int, direction: Int): Int = direction * width / 6
}
