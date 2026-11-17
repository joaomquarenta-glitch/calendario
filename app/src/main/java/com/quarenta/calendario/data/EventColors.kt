package com.quarenta.calendario.data

/** A colour an appointment can wear. [key] is the account's own colour id (Google), when it has them. */
data class EventColor(val key: String?, val argb: Int)

object EventColors {
    /** Used when the account has no colour list of its own (phone-only calendars, some others). */
    val palette: List<Int> = listOf(
        0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00, 0xFF34C759, 0xFF00C7BE, 0xFF30B0C7,
        0xFF007AFF, 0xFF5856D6, 0xFFAF52DE, 0xFFFF2D55, 0xFFA2845E, 0xFF8E8E93,
    ).map { it.toInt() }

    /**
     * The colours to offer: the account's own list when it has one (Google keeps its 11 colours
     * and only accepts those), ordered like a rainbow; otherwise [palette].
     */
    fun choices(account: List<Pair<String, Int>>): List<EventColor> =
        if (account.isEmpty()) palette.map { EventColor(null, it) }
        else account.distinctBy { it.second and 0xFFFFFF }.sortedBy { hue(it.second) }.map { EventColor(it.first, it.second) }

    /** Same colour, ignoring transparency (the calendar store sometimes keeps a different alpha). */
    fun same(a: Int, b: Int): Boolean = (a and 0xFFFFFF) == (b and 0xFFFFFF)

    /** Hue 0..360, greys last. */
    fun hue(argb: Int): Float {
        val r = (argb shr 16 and 0xFF) / 255f
        val g = (argb shr 8 and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        if (d < 0.08f) return 400f + (1 - max) // greys at the end, light to dark
        val h = when (max) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * (((b - r) / d) + 2)
            else -> 60 * (((r - g) / d) + 4)
        }
        return if (h < 0) h + 360 else h
    }
}
