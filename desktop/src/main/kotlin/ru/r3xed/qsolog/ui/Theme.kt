package ru.r3xed.qsolog.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import ru.r3xed.qsolog.data.CustomTheme.Role as ThemeRole
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Sans = FontFamily(
    Font("font/atkinson_regular.ttf", FontWeight.Normal),
    Font("font/atkinson_bold.ttf", FontWeight.Bold),
)
val Mono = FontFamily(
    Font("font/jetbrains_mono_medium.ttf", FontWeight.Medium),
    Font("font/jetbrains_mono_bold.ttf", FontWeight.Bold),
)

/** Colours outside the Material scheme: the "worked before" highlight and success state. */
@Immutable
data class Extra(
    val hl: Color, val hlBorder: Color, val hlInk: Color,
    val ok: Color, val okBg: Color,
    val muted: Color, val field: Color, val line: Color,
    /** The stripe at the left of a log row uploaded to HAMLOG.online. */
    val hamlog: Color = Color(0xFF7B4FB0),
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
    hamlog = Color(0xFF7B4FB0),
)
private val DarkExtra = Extra(
    hl = Color(0xFF3A2F0B), hlBorder = Color(0xFFE0A800), hlInk = Color(0xFFFFE08A),
    ok = Color(0xFF6BD49A), okBg = Color(0xFF12301F),
    muted = Color(0xFFA6B5C4), field = Color(0xFF1C2630), line = Color(0xFF26323D),
    hamlog = Color(0xFFC39BEF),
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

/**
 * How much bigger the text is than the rest at a compact "Масштаб интерфейса" (1 at 100%): fields of a fixed width
 * that hold a label widen by it, so the label does not wrap.
 */
val LocalTextBoost = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** How much smaller than its sizes in dp the desktop interface is drawn (1.14.0: 30% more compact). */
const val UI_SCALE = 0.7f

@Composable
fun QsoTheme(
    dark: Boolean = isSystemInDarkTheme(),
    scale: ru.r3xed.qsolog.data.UiScale = ru.r3xed.qsolog.data.UiScale.S100,
    custom: ru.r3xed.qsolog.data.CustomTheme? = null,
    focusRing: Boolean = false,
    content: @Composable () -> Unit,
) {
    val (scheme, extra) = themeColors(dark, custom)
    // The whole window drawn 30% smaller than the phone sizes the screens were made with: text, buttons and gaps alike.
    // "Масштаб интерфейса" makes it smaller still, the text less than the rest (UiScale.text) so it stays readable.
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        LocalExtra provides extra,
        LocalFocusRing provides if (focusRing) focusRingColor(dark, custom) else null,
        LocalTextBoost provides scale.text / scale.factor,
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density * UI_SCALE * scale.factor, d.fontScale * scale.text / scale.factor),
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

/** The colour of the callsign field for [d]: red — a repeat, amber — this band in another mode, green — a new band. */
@androidx.compose.runtime.Composable
fun dupeTint(d: ru.r3xed.qsolog.data.Dupe): Color? = when (d) {
    ru.r3xed.qsolog.data.Dupe.DUPE -> androidx.compose.material3.MaterialTheme.colorScheme.error
    ru.r3xed.qsolog.data.Dupe.BAND -> LocalExtra.current.hlBorder
    ru.r3xed.qsolog.data.Dupe.WORKED -> LocalExtra.current.ok
    ru.r3xed.qsolog.data.Dupe.NEW -> null
}

/** The contrast outline of the input field with the cursor ("Контрастная обводка" in the settings); null — off. */
val LocalFocusRing = staticCompositionLocalOf<Color?> { null }

/** The Material scheme and the extra colours of the user's own theme. */
private fun customScheme(t: ru.r3xed.qsolog.data.CustomTheme): Pair<androidx.compose.material3.ColorScheme, Extra> {
    fun c(r: ru.r3xed.qsolog.data.CustomTheme.Role) = Color(t[r].toInt())
    val bg = c(ThemeRole.BACKGROUND); val text = c(ThemeRole.TEXT); val accent = c(ThemeRole.ACCENT); val group = c(ThemeRole.GROUP); val line = c(ThemeRole.LINE)
    val muted = c(ThemeRole.MUTED); val field = c(ThemeRole.FIELD); val error = c(ThemeRole.ERROR)
    val base = if (t.dark) Dark else Light
    val scheme = base.copy(
        primary = accent, onPrimary = c(ThemeRole.ON_ACCENT), inversePrimary = accent,
        primaryContainer = group, onPrimaryContainer = c(ThemeRole.GROUP_TEXT),
        secondaryContainer = field, onSecondaryContainer = text,
        background = bg, onBackground = text, surface = bg, onSurface = text,
        surfaceVariant = field, onSurfaceVariant = muted,
        surfaceContainerHigh = field, surfaceContainer = field, surfaceContainerHighest = field,
        surfaceContainerLow = bg, surfaceContainerLowest = bg,
        outline = line, outlineVariant = line, error = error,
    )
    val ok = c(ThemeRole.OK)
    val extra = Extra(
        hl = c(ThemeRole.HIGHLIGHT), hlBorder = c(ThemeRole.HIGHLIGHT_LINE), hlInk = c(ThemeRole.HIGHLIGHT_TEXT),
        ok = ok, okBg = ok.copy(alpha = 0.16f).compositeOver(bg),
        muted = muted, field = field, line = line,
        hamlog = c(ThemeRole.HAMLOG),
    )
    return scheme to extra
}

/** The colours in use: the user's theme when [custom] is set, otherwise the light or the dark one. */
@Composable
internal fun themeColors(dark: Boolean, custom: ru.r3xed.qsolog.data.CustomTheme?): Pair<androidx.compose.material3.ColorScheme, Extra> =
    if (custom != null) androidx.compose.runtime.remember(custom) { customScheme(custom) }
    else if (dark) Dark to DarkExtra else Light to LightExtra

/** The contrast outline's colour for the theme: the user's own, or bright orange / amber. */
fun focusRingColor(dark: Boolean, custom: ru.r3xed.qsolog.data.CustomTheme?): Color =
    Color((custom?.get(ru.r3xed.qsolog.data.CustomTheme.Role.FOCUS) ?: (if (dark) ru.r3xed.qsolog.data.CustomTheme.DARK else ru.r3xed.qsolog.data.CustomTheme.LIGHT)
        .getValue(ru.r3xed.qsolog.data.CustomTheme.Role.FOCUS)).toInt())

/** Uploaded to HAMLOG.online (the mark of [ru.r3xed.qsolog.data.OnlineLog.HAMLOG]). */
fun ru.r3xed.qsolog.data.Qso.onHamlog(): Boolean = ru.r3xed.qsolog.data.OnlineLog.HAMLOG.isSent(this)
