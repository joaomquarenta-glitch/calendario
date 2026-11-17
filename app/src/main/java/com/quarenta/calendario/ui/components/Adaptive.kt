package com.quarenta.calendario.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.theme.Gold
import com.quarenta.calendario.ui.theme.ThemeState

/**
 * Phone held sideways, foldable or tablet: the tabs move to a rail on the left
 * and the screens keep a comfortable reading width in the middle.
 */
object Adaptive {
    /** From this width on (a phone on its side, an open foldable, a tablet) the tabs go to the side. */
    const val RAIL_FROM_DP = 600

    /** The widest a screen's content gets; wider than this, lines get too long to read. */
    val MaxContent: Dp = 720.dp

    fun useRail(widthDp: Int): Boolean = widthDp >= RAIL_FROM_DP
}

/** True when the window is wide enough for the side rail. */
@Composable
fun isWide(): Boolean = Adaptive.useRail(LocalConfiguration.current.screenWidthDp)

/**
 * Keeps clear of a side camera cut-out or side buttons when the phone lies down.
 * [startHandled]: the rail on the left already keeps clear of that side.
 */
@Composable
fun Modifier.sideSafe(startHandled: Boolean = false): Modifier = windowInsetsPadding(
    WindowInsets.safeDrawing.only(if (startHandled) WindowInsetsSides.End else WindowInsetsSides.Horizontal),
)

/** The content in the middle, never wider than [Adaptive.MaxContent]. On a phone it simply fills. */
@Composable
fun CenteredContent(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxHeight().widthIn(max = Adaptive.MaxContent).fillMaxWidth(), content = content)
    }
}

/** A full-screen sheet: centred like the tabs, except the ones that draw edge to edge (a person's card). */
@Composable
fun SheetFrame(edgeToEdge: Boolean, content: @Composable () -> Unit) {
    if (edgeToEdge) content() else CenteredContent(Modifier.sideSafe()) { content() }
}

/** The tabs as a rail down the left side, for wide windows. */
@Composable
fun TabRail(
    items: List<Pair<String, ImageVector>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationRail(
        containerColor = Bento.Ground,
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .then(
                // Black and gold: a thin line of light along the rail's edge, like the tab bar's top.
                if (ThemeState.isLuxe) Modifier.drawBehind {
                    drawLine(Gold.rim, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                } else Modifier,
            ),
    ) {
        Spacer(Modifier.size(8.dp))
        items.forEachIndexed { i, (label, icon) ->
            NavigationRailItem(
                selected = i == selected,
                onClick = { onSelect(i) },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = Bento.Blue,
                    selectedTextColor = Bento.Blue,
                    indicatorColor = Bento.Blue.copy(alpha = if (ThemeState.isLuxe) 0.18f else 0.12f),
                    unselectedIconColor = Bento.Secondary,
                    unselectedTextColor = Bento.Secondary,
                ),
            )
        }
    }
}

