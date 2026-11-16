package com.quarenta.calendario.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.ui.theme.Bento
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The little drawings that stand in for "nothing here". */
enum class EmptyArt { FreeDay, NoEvents, NoTasks, NoPeople, NoResults }

/**
 * A drawn empty state: a small illustration in the app's accent (gold in the black-and-gold look)
 * that breathes gently, a title and a line of help. [compact] puts the drawing beside one line of text.
 */
@Composable
fun EmptyState(art: EmptyArt, title: String, subtitle: String? = null, modifier: Modifier = Modifier, compact: Boolean = false) {
    val accent = Bento.Blue
    val soft = Bento.Secondary
    val breathe by rememberInfiniteTransition(label = "empty").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "breathe",
    )
    if (compact) {
        Row(modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Drawing(art, accent, soft, breathe, 44.dp)
            Column {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink)
                subtitle?.let { Text(it, fontSize = 13.sp, color = soft) }
            }
        }
        return
    }
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Drawing(art, accent, soft, breathe, 132.dp)
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Ink, textAlign = TextAlign.Center)
        subtitle?.let { Text(it, fontSize = 15.sp, color = soft, textAlign = TextAlign.Center, lineHeight = 21.sp) }
    }
}

@Composable
private fun Drawing(art: EmptyArt, accent: Color, soft: Color, breathe: Float, size: Dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val line = Stroke(width = s * 0.035f, cap = StrokeCap.Round)
        // A soft halo behind every drawing.
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.22f + 0.08f * breathe), Color.Transparent), center, s * 0.5f),
            radius = s * 0.5f,
        )
        when (art) {
            EmptyArt.FreeDay -> sun(s, accent, soft, line, breathe)
            EmptyArt.NoEvents -> calendar(s, accent, soft, line, breathe)
            EmptyArt.NoTasks -> tick(s, accent, line, breathe)
            EmptyArt.NoPeople -> people(s, accent, soft, line)
            EmptyArt.NoResults -> lens(s, accent, soft, line, breathe)
        }
    }
}

/** A sun rising over a hill: the day is yours. */
private fun DrawScope.sun(s: Float, accent: Color, soft: Color, line: Stroke, breathe: Float) {
    val c = Offset(s * 0.5f, s * 0.52f)
    drawCircle(Brush.linearGradient(listOf(lerp(accent, Color.White, 0.35f), accent), Offset(c.x, c.y - s * 0.16f), Offset(c.x, c.y + s * 0.16f)), s * 0.16f, c)
    rotate(breathe * 12f, c) {
        repeat(8) { i ->
            val a = i * PI.toFloat() / 4
            val r1 = s * 0.23f
            val r2 = s * 0.31f
            drawLine(accent, Offset(c.x + cos(a) * r1, c.y + sin(a) * r1), Offset(c.x + cos(a) * r2, c.y + sin(a) * r2), line.width, StrokeCap.Round)
        }
    }
    val hill = Path().apply {
        moveTo(s * 0.08f, s * 0.82f)
        quadraticTo(s * 0.35f, s * 0.62f, s * 0.6f, s * 0.78f)
        quadraticTo(s * 0.78f, s * 0.68f, s * 0.92f, s * 0.82f)
    }
    drawPath(hill, soft.copy(alpha = 0.8f), style = line)
}

/** A calendar page with a little sparkle: nothing booked yet. */
private fun DrawScope.calendar(s: Float, accent: Color, soft: Color, line: Stroke, breathe: Float) {
    val tl = Offset(s * 0.2f, s * 0.24f)
    val size = Size(s * 0.6f, s * 0.56f)
    drawRoundRect(accent.copy(alpha = 0.14f), tl, size, CornerRadius(s * 0.08f))
    drawRoundRect(accent, tl, size, CornerRadius(s * 0.08f), style = line)
    drawLine(accent, Offset(tl.x, s * 0.38f), Offset(tl.x + size.width, s * 0.38f), line.width)
    drawLine(accent, Offset(s * 0.34f, s * 0.18f), Offset(s * 0.34f, s * 0.28f), line.width, StrokeCap.Round)
    drawLine(accent, Offset(s * 0.66f, s * 0.18f), Offset(s * 0.66f, s * 0.28f), line.width, StrokeCap.Round)
    for (row in 0..1) for (col in 0..2) {
        drawCircle(soft.copy(alpha = 0.7f), s * 0.025f, Offset(s * (0.34f + col * 0.16f), s * (0.52f + row * 0.14f)))
    }
    sparkle(Offset(s * 0.8f, s * 0.2f), s * (0.06f + 0.02f * breathe), accent)
}

/** A big tick in a ring, with two sparkles: all done. */
private fun DrawScope.tick(s: Float, accent: Color, line: Stroke, breathe: Float) {
    drawCircle(accent.copy(alpha = 0.14f), s * 0.28f, center)
    drawCircle(accent, s * 0.28f, center, style = line)
    val p = Path().apply {
        moveTo(s * 0.38f, s * 0.51f)
        lineTo(s * 0.47f, s * 0.6f)
        lineTo(s * 0.64f, s * 0.41f)
    }
    drawPath(p, accent, style = Stroke(width = s * 0.05f, cap = StrokeCap.Round))
    sparkle(Offset(s * 0.82f, s * 0.24f), s * (0.05f + 0.02f * breathe), accent)
    sparkle(Offset(s * 0.18f, s * 0.78f), s * (0.04f + 0.015f * (1 - breathe)), accent)
}

/** Two friendly heads side by side. */
private fun DrawScope.people(s: Float, accent: Color, soft: Color, line: Stroke) {
    fun person(x: Float, color: Color, scale: Float) {
        drawCircle(color, s * 0.09f * scale, Offset(x, s * 0.4f), style = line)
        val body = Path().apply {
            moveTo(x - s * 0.16f * scale, s * 0.74f)
            quadraticTo(x, s * (0.74f - 0.3f * scale), x + s * 0.16f * scale, s * 0.74f)
        }
        drawPath(body, color, style = line)
    }
    person(s * 0.62f, soft.copy(alpha = 0.8f), 0.85f)
    person(s * 0.42f, accent, 1f)
}

/** A magnifying glass over an empty page. */
private fun DrawScope.lens(s: Float, accent: Color, soft: Color, line: Stroke, breathe: Float) {
    val c = Offset(s * (0.44f + 0.03f * breathe), s * 0.44f)
    drawCircle(accent.copy(alpha = 0.12f), s * 0.18f, c)
    drawCircle(accent, s * 0.18f, c, style = line)
    drawLine(accent, Offset(c.x + s * 0.13f, c.y + s * 0.13f), Offset(c.x + s * 0.27f, c.y + s * 0.27f), s * 0.06f, StrokeCap.Round)
    drawLine(soft.copy(alpha = 0.7f), Offset(c.x - s * 0.08f, c.y), Offset(c.x + s * 0.08f, c.y), line.width, StrokeCap.Round)
}

private fun DrawScope.sparkle(at: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(at.x, at.y - r)
        quadraticTo(at.x, at.y, at.x + r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y + r)
        quadraticTo(at.x, at.y, at.x - r, at.y)
        quadraticTo(at.x, at.y, at.x, at.y - r)
        close()
    }
    drawPath(p, color)
}
