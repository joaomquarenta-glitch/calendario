package com.quarenta.calendario.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.ui.theme.Bento
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import com.quarenta.calendario.ui.theme.Gold
import com.quarenta.calendario.ui.theme.ThemeState

/** A rounded white "bento" tile, the basic building block of every screen. */
@Composable
fun BentoTile(
    modifier: Modifier = Modifier,
    color: Color = Bento.Tile,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier = modifier
            .then(if (ThemeState.isLuxe) Modifier.luxe(shape, plain = color == Bento.Tile, tint = color) else Modifier.clip(shape).background(color))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

/**
 * The black-and-gold surface: a soft gold glow around the tile, a matte brushed-metal face
 * and a thin crystal rim that catches the light. [plain] tiles get the black metal; coloured
 * ones keep their colour, darkened, under the same rim.
 */
fun Modifier.luxe(shape: Shape, plain: Boolean = true, tint: Color = Color.Unspecified, glow: Boolean = true): Modifier =
    this
        .then(if (glow) Modifier.drawBehind { drawGlow(shape, Gold.Main.copy(alpha = 0.32f), 14.dp.toPx()) } else Modifier)
        .clip(shape)
        .then(
            if (plain || tint == Color.Unspecified) Modifier.background(Gold.surface)
            else Modifier.background(Brush.linearGradient(listOf(tint.copy(alpha = 0.92f), lerp(tint, Color.Black, 0.3f)))),
        )
        .drawWithCache {
            // Brushed metal: very fine horizontal strokes, barely visible, drawn once per size.
            val step = 2.5.dp.toPx()
            val lines = (size.height / step).toInt()
            onDrawBehind {
                for (i in 0..lines) {
                    val y = i * step
                    drawLine(
                        Color.White.copy(alpha = if (i % 3 == 0) 0.035f else 0.015f),
                        Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                    )
                }
                // A crystal highlight along the top edge.
                drawRect(
                    Brush.verticalGradient(0f to Gold.Bright.copy(alpha = 0.10f), 0.35f to Color.Transparent),
                )
            }
        }
        .border(1.dp, Gold.rim, shape)

/** A blurred halo of [color] around [shape]; on phones before Android 9 it is simply left out. */
private fun DrawScope.drawGlow(shape: Shape, color: Color, radius: Float) {
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return
    val outline = shape.createOutline(size, layoutDirection, this)
    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            this.color = color
            asFrameworkPaint().maskFilter = android.graphics.BlurMaskFilter(radius, android.graphics.BlurMaskFilter.Blur.OUTER)
        }
        canvas.drawOutline(outline, paint)
    }
}

/** Big, colourful rounded-square icon, in the style of Apple app icons. In black and gold it glows like neon. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 44.dp, iconSize: Dp = 24.dp, tint: Color = Color.White) {
    val shape = RoundedCornerShape(size * 0.3f)
    val luxe = ThemeState.isLuxe
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (luxe) Modifier
                    .drawBehind { drawGlow(shape, color.copy(alpha = 0.55f), 10.dp.toPx()) }
                    .clip(shape)
                    .background(Brush.linearGradient(listOf(lerp(color, Color.Black, 0.62f), lerp(color, Color.Black, 0.86f))))
                    .border(1.dp, Brush.linearGradient(listOf(lerp(color, Color.White, 0.55f), color.copy(alpha = 0.5f), lerp(color, Color.White, 0.3f))), shape)
                else Modifier.clip(shape).background(color),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (luxe) lerp(color, Color.White, 0.35f) else tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Bento.Hairline),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        color = Bento.Secondary,
    )
}
