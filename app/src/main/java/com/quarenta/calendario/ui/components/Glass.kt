package com.quarenta.calendario.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp
import com.quarenta.calendario.ui.theme.Bento
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * Space taken by the frosted bars drawn over the tabs (the status bar on top, the tab bar below).
 * Lists start and end inside it, so their content can slide underneath the glass.
 */
val LocalBars = compositionLocalOf { PaddingValues(0.dp) }

/** Frosted glass like the iPhone's bars: the content behind is blurred and lightly tinted. */
@Composable
fun glassStyle(): HazeStyle = HazeStyle(
    backgroundColor = Bento.Ground,
    tints = listOf(HazeTint(Bento.Tile.copy(alpha = 0.68f))),
    blurRadius = 26.dp,
    noiseFactor = 0.1f,
    // Before Android 12 there is no blur: an almost solid bar instead.
    fallbackTint = HazeTint(Bento.Tile.copy(alpha = 0.94f)),
)
