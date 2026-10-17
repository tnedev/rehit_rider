package com.tisho.ride.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

data class Palette(
    val dark: Boolean,
    val bg: Color, val surface: Color, val surface2: Color, val outline: Color,
    val text: Color, val muted: Color,
    val accent: Color, val power: Color, val cadence: Color, val heart: Color, val level: Color, val good: Color,
    val sprintBg: Color, val zones: List<Color>,
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFF3F5F8), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE8ECF1), outline = Color(0xFFD3D9E1),
    text = Color(0xFF0F1419), muted = Color(0xFF505B69),
    accent = Color(0xFFE2431C), power = Color(0xFFB86E00), cadence = Color(0xFF00857F), heart = Color(0xFFD01F46),
    level = Color(0xFF5141D6), good = Color(0xFF1A8A4A), sprintBg = Color(0xFFFFE3DA),
    zones = listOf(Color(0xFF8B95A3), Color(0xFF2F7DE1), Color(0xFF1A9E55), Color(0xFFD9A400), Color(0xFFE8730C), Color(0xFFD01F46), Color(0xFFB12ED8), Color(0xFF7B5CE0)),
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF0E1116), surface = Color(0xFF181D25), surface2 = Color(0xFF232A34), outline = Color(0xFF323A46),
    text = Color(0xFFF4F6F9), muted = Color(0xFFA7B0BD),
    accent = Color(0xFFFF6A45), power = Color(0xFFFFB627), cadence = Color(0xFF45DED8), heart = Color(0xFFFF5C7A),
    level = Color(0xFF9D90FF), good = Color(0xFF45DC8A), sprintBg = Color(0xFF3A120A),
    zones = listOf(Color(0xFF6B7584), Color(0xFF5AA2FF), Color(0xFF45DC8A), Color(0xFFFFD43B), Color(0xFFFF922B), Color(0xFFFF5C7A), Color(0xFFDA4BF9), Color(0xFFB197FC)),
)

/** Current palette; reading any colour inside a composable recomposes when the theme changes. */
object C {
    var palette by mutableStateOf(LightPalette)
    val bg get() = palette.bg
    val surface get() = palette.surface
    val surface2 get() = palette.surface2
    val outline get() = palette.outline
    val text get() = palette.text
    val muted get() = palette.muted
    val accent get() = palette.accent
    val power get() = palette.power
    val cadence get() = palette.cadence
    val heart get() = palette.heart
    val level get() = palette.level
    val good get() = palette.good
    val sprintBg get() = palette.sprintBg
    val zones get() = palette.zones
    /** Text on accent-coloured surfaces. */
    val onAccent = Color.White
    val avatars = listOf(Color(0xFFE2431C), Color(0xFF2F7DE1), Color(0xFF1A9E55), Color(0xFFD08A00), Color(0xFF5141D6), Color(0xFFD01F46), Color(0xFF00857F), Color(0xFFC2185B))
}

private val typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 96.sp, letterSpacing = (-2).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 64.sp, letterSpacing = (-1).sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.6.sp),
)

/** [mode] is "light", "dark" or "system". */
@Composable
fun RideTheme(mode: String = "light", content: @Composable () -> Unit) {
    val dark = when (mode) { "dark" -> true; "system" -> isSystemInDarkTheme(); else -> false }
    val p = if (dark) DarkPalette else LightPalette
    C.palette = p
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = Color.White, secondary = p.cadence, background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.outline, error = p.heart,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = Color.White, secondary = p.cadence, background = p.bg, onBackground = p.text,
        surface = p.surface, onSurface = p.text, surfaceVariant = p.surface2, onSurfaceVariant = p.muted, outline = p.outline, error = p.heart,
        surfaceContainerHighest = p.surface2, surfaceContainer = p.surface,
    )
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
