package com.quarenta.calendario.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.R

/** Apple-style system colours used across the app (tiles, icons, accents). */
object Bento {
    val Ground = Color(0xFFF2F2F7)
    val Tile = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1C1C1E)
    val Secondary = Color(0xFF6E6E73)
    val Hairline = Color(0xFFE5E5EA)

    val Blue = Color(0xFF007AFF)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
    val Red = Color(0xFFFF3B30)
    val Pink = Color(0xFFFF2D55)
    val Purple = Color(0xFFAF52DE)
    val Indigo = Color(0xFF5856D6)
    val Teal = Color(0xFF30B0C7)

    /** Darker shades that keep 4.5:1 contrast for small text on white. */
    val BlueText = Color(0xFF0062CC)
    val RedText = Color(0xFFD70015)
    val GreenText = Color(0xFF1B6E30)
}

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
    Font(R.font.inter_extrabold, FontWeight.ExtraBold),
)

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

@Composable
fun CalendarioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Bento.Blue,
            onPrimary = Color.White,
            background = Bento.Ground,
            surface = Bento.Tile,
            onSurface = Bento.Ink,
            onBackground = Bento.Ink,
            secondary = Bento.Green,
            error = Bento.Red,
        ),
        typography = AppTypography,
        content = content,
    )
}
