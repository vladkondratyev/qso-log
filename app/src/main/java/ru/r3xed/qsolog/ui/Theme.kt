package ru.r3xed.qsolog.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ru.r3xed.qsolog.R

val Sans = FontFamily(
    Font(R.font.atkinson_regular, FontWeight.Normal),
    Font(R.font.atkinson_bold, FontWeight.Bold),
)
val Mono = FontFamily(
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

/** Colours outside the Material scheme: the "worked before" highlight and success state. */
@Immutable
data class Extra(
    val hl: Color, val hlBorder: Color, val hlInk: Color,
    val ok: Color, val okBg: Color,
    val muted: Color, val field: Color, val line: Color,
)

val LocalExtra = staticCompositionLocalOf {
    Extra(Color.Yellow, Color.Yellow, Color.Black, Color.Green, Color.Green, Color.Gray, Color.LightGray, Color.Gray)
}

private val Light = lightColorScheme(
    primary = Color(0xFF0A5C8A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E8F5), onPrimaryContainer = Color(0xFF06324D),
    secondaryContainer = Color(0xFFE3EDF4), inversePrimary = Color(0xFF8FD0F5), onSecondaryContainer = Color(0xFF14202B),
    background = Color(0xFFFFFFFF), onBackground = Color(0xFF14202B),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF14202B),
    surfaceVariant = Color(0xFFF1F4F7), onSurfaceVariant = Color(0xFF4A5A6D),
    surfaceContainerHigh = Color(0xFFF1F4F7),
    outline = Color(0xFFB9C4D0), outlineVariant = Color(0xFFDCE2E9),
    error = Color(0xFFB3261E), onError = Color.White,
)
private val Dark = darkColorScheme(
    primary = Color(0xFF6BBEEF), onPrimary = Color(0xFF06121B),
    primaryContainer = Color(0xFF12405E), onPrimaryContainer = Color(0xFFD3E8F5),
    secondaryContainer = Color(0xFF1C2630), inversePrimary = Color(0xFF0A5C8A), onSecondaryContainer = Color(0xFFE8EEF4),
    background = Color(0xFF10171E), onBackground = Color(0xFFE8EEF4),
    surface = Color(0xFF10171E), onSurface = Color(0xFFE8EEF4),
    surfaceVariant = Color(0xFF1C2630), onSurfaceVariant = Color(0xFFA6B5C4),
    surfaceContainerHigh = Color(0xFF1C2630),
    outline = Color(0xFF3A4957), outlineVariant = Color(0xFF26323D),
    error = Color(0xFFFF8A80), onError = Color(0xFF3B0906),
)
private val LightExtra = Extra(
    hl = Color(0xFFFFF1C9), hlBorder = Color(0xFFE0A800), hlInk = Color(0xFF4A3700),
    ok = Color(0xFF1E7B45), okBg = Color(0xFFE2F4E9),
    muted = Color(0xFF4A5A6D), field = Color(0xFFF1F4F7), line = Color(0xFFDCE2E9),
)
private val DarkExtra = Extra(
    hl = Color(0xFF3A2F0B), hlBorder = Color(0xFFE0A800), hlInk = Color(0xFFFFE08A),
    ok = Color(0xFF6BD49A), okBg = Color(0xFF12301F),
    muted = Color(0xFFA6B5C4), field = Color(0xFF1C2630), line = Color(0xFF26323D),
)

private val base = Typography()
private fun TextStyle.sans() = copy(fontFamily = Sans)
private val AppTypography = Typography(
    displaySmall = base.displaySmall.sans(),
    headlineLarge = base.headlineLarge.sans().copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.sans().copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.sans().copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.sans().copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleMedium = base.titleMedium.sans().copy(fontWeight = FontWeight.Bold, fontSize = 19.sp),
    titleSmall = base.titleSmall.sans().copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    bodyLarge = base.bodyLarge.sans().copy(fontSize = 18.sp, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.sans().copy(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = base.bodySmall.sans().copy(fontSize = 15.sp),
    labelLarge = base.labelLarge.sans().copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
    labelMedium = base.labelMedium.sans().copy(fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelSmall = base.labelSmall.sans().copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
)

@Composable
fun QsoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}
