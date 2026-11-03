package com.quarenta.calendario.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.R

/** One look for the whole app: the page colour, the tile colour, and whether text is light on dark. */
data class AppTheme(
    val id: String,
    val name: String,
    val ground: Color,
    val tile: Color,
    val dark: Boolean = false,
)

object Themes {
    val Light = AppTheme("light", "Claro", Color(0xFFF2F2F7), Color(0xFFFFFFFF))
    val Black = AppTheme("black", "Preto", Color(0xFF000000), Color(0xFF1C1C1E), dark = true)
    /** Follows the phone: Claro by day, Preto when the phone is in dark mode. */
    const val SYSTEM = "system"

    val pastels = listOf(
        AppTheme("rosa", "Rosa", Color(0xFFFADDE6), Color(0xFFFFF4F7)),
        AppTheme("pessego", "Pêssego", Color(0xFFFDE2D2), Color(0xFFFFF6F0)),
        AppTheme("damasco", "Damasco", Color(0xFFFFE6C7), Color(0xFFFFF8EE)),
        AppTheme("manteiga", "Manteiga", Color(0xFFFBF0C2), Color(0xFFFFFCEC)),
        AppTheme("lima", "Lima", Color(0xFFE6F2C9), Color(0xFFF8FCEF)),
        AppTheme("menta", "Menta", Color(0xFFD4F0E0), Color(0xFFF2FBF6)),
        AppTheme("salva", "Salva", Color(0xFFDCE8D6), Color(0xFFF5F9F3)),
        AppTheme("agua", "Água", Color(0xFFCFEFF0), Color(0xFFF0FAFA)),
        AppTheme("ceu", "Céu", Color(0xFFD6E8FA), Color(0xFFF2F8FE)),
        AppTheme("pervinca", "Pervinca", Color(0xFFDCDDF8), Color(0xFFF4F4FD)),
        AppTheme("lavanda", "Lavanda", Color(0xFFE7DCF7), Color(0xFFF9F4FE)),
        AppTheme("malva", "Malva", Color(0xFFF1D9EE), Color(0xFFFCF3FA)),
        AppTheme("areia", "Areia", Color(0xFFEDE4D8), Color(0xFFFBF8F4)),
        AppTheme("perola", "Pérola", Color(0xFFE3E5EA), Color(0xFFF8F9FB)),
    )

    fun byId(id: String?): AppTheme? = (listOf(Light, Black) + pastels).firstOrNull { it.id == id }
}

/** The chosen look. Reading it inside the UI redraws the screens when it changes. */
object ThemeState {
    var selectedId by mutableStateOf(Themes.SYSTEM)
    var systemDark by mutableStateOf(false)

    val current: AppTheme
        get() = Themes.byId(selectedId) ?: if (systemDark) Themes.Black else Themes.Light

    val isDark: Boolean get() = current.dark
}

/** Apple-style system colours used across the app (tiles, icons, accents). */
object Bento {
    val Ground: Color get() = ThemeState.current.ground
    val Tile: Color get() = ThemeState.current.tile
    val Ink: Color get() = if (ThemeState.isDark) Color(0xFFF5F5F7) else Color(0xFF1C1C1E)
    val Secondary: Color get() = if (ThemeState.isDark) Color(0xFF98989F) else Color(0xFF5E5E66)
    val Hairline: Color get() = if (ThemeState.isDark) Color(0xFF38383A) else Color(0x1F000000)

    val Blue = Color(0xFF007AFF)
    val Green = Color(0xFF34C759)
    val Orange = Color(0xFFFF9500)
    val Red = Color(0xFFFF3B30)
    val Pink = Color(0xFFFF2D55)
    val Purple = Color(0xFFAF52DE)
    val Indigo = Color(0xFF5856D6)
    val Teal = Color(0xFF30B0C7)
    val Yellow = Color(0xFFFFCC00)

    /** Text shades that keep 4.5:1 contrast on the tiles, in light and dark looks. */
    val BlueText: Color get() = if (ThemeState.isDark) Color(0xFF64A8FF) else Color(0xFF0062CC)
    val RedText: Color get() = if (ThemeState.isDark) Color(0xFFFF6961) else Color(0xFFD70015)
    val GreenText: Color get() = if (ThemeState.isDark) Color(0xFF4CD964) else Color(0xFF1B6E30)
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
    val systemDark = isSystemInDarkTheme()
    SideEffect { ThemeState.systemDark = systemDark }
    val t = ThemeState.current
    val colors = if (t.dark) {
        darkColorScheme(
            primary = Bento.Blue, onPrimary = Color.White,
            background = t.ground, surface = t.tile, surfaceVariant = t.tile,
            onSurface = Bento.Ink, onBackground = Bento.Ink, onSurfaceVariant = Bento.Secondary,
            secondary = Bento.Green, error = Bento.Red,
        )
    } else {
        lightColorScheme(
            primary = Bento.Blue, onPrimary = Color.White,
            background = t.ground, surface = t.tile, surfaceVariant = t.tile,
            onSurface = Bento.Ink, onBackground = Bento.Ink, onSurfaceVariant = Bento.Secondary,
            secondary = Bento.Green, error = Bento.Red,
        )
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
