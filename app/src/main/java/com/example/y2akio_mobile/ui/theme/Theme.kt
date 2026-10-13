package com.example.y2akio_mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.y2akio_mobile.settings.AppLanguage
import com.example.y2akio_mobile.settings.AppTheme
import com.example.y2akio_mobile.settings.Strings

val BrandPinkLight = Color(0xFFF58AFB)
val BrandPink = Color(0xFFF25EE7)
val BrandGradient = Brush.linearGradient(listOf(BrandPinkLight, BrandPink))

/**
 * One UI surfaces: a neutral grey canvas with white (light) / near-black (dark) rounded cards on
 * it, one accent colour for values, switches and primary buttons.
 *
 * Mapping used across the app:
 * - background            → screen canvas
 * - surfaceContainerLow   → rounded cards ("RoundedGroup")
 * - surfaceContainerHigh  → filled inputs, inactive segments, thumbnails placeholders
 * - outlineVariant        → inset list dividers
 * - onSurfaceVariant      → summaries / secondary text
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFFC03AB8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFBE3F9),
    onPrimaryContainer = Color(0xFF4A0045),
    secondary = Color(0xFF6E6E73),
    background = Color(0xFFF4F4F6),
    onBackground = Color(0xFF111111),
    surface = Color(0xFFF4F4F6),
    onSurface = Color(0xFF111111),
    onSurfaceVariant = Color(0xFF75757A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFEFEFE),
    surfaceContainer = Color(0xFFF7F7F9),
    surfaceContainerHigh = Color(0xFFEDEDF0),
    surfaceContainerHighest = Color(0xFFE5E5E8),
    outline = Color(0xFFBDBDC2),
    outlineVariant = Color(0xFFE6E6EA),
    error = Color(0xFFE5383B),
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF8C1215),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF37FEB),
    onPrimary = Color(0xFF3D0039),
    primaryContainer = Color(0xFF4A1846),
    onPrimaryContainer = Color(0xFFFFD7FA),
    secondary = Color(0xFF9E9EA4),
    background = Color(0xFF010101),
    onBackground = Color(0xFFF2F2F2),
    surface = Color(0xFF010101),
    onSurface = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF98989E),
    surfaceContainerLowest = Color(0xFF0E0E0F),
    surfaceContainerLow = Color(0xFF171718),
    surfaceContainer = Color(0xFF1E1E20),
    surfaceContainerHigh = Color(0xFF262628),
    surfaceContainerHighest = Color(0xFF2F2F32),
    outline = Color(0xFF4A4A4E),
    outlineVariant = Color(0xFF2A2A2D),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFFC9C9),
)

/** One UI type scale on the system font (SamsungOne on Galaxy devices). */
private val OneUiTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontSize = 38.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
        titleSmall = titleSmall.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        bodyLarge = bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyMedium = bodyMedium.copy(fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
        bodySmall = bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelMedium = labelMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    )
}

/** One UI spacing tokens. */
object OneUi {
    /** Card distance from the screen edge. */
    val ScreenMargin = 12.dp
    /** Content inset inside a card. */
    val CardPadding = 22.dp
    val CardRadius = 26.dp
    /** Gap between two cards of the same section. */
    val CardGap = 12.dp
    val RowMinHeight = 60.dp
    val CollapsedBarHeight = 56.dp
    val SubHeaderStyle: TextStyle @Composable get() = MaterialTheme.typography.labelMedium
}

/** Current UI language; [tr] reads it so every string recomposes instantly when it changes. */
val LocalLanguage = compositionLocalOf { AppLanguage.Vietnamese }

@Composable
fun tr(key: String): String = Strings.get(key, LocalLanguage.current)

@Composable
fun tr(key: String, vararg args: Any): String {
    var s = Strings.get(key, LocalLanguage.current)
    args.forEachIndexed { i, a -> s = s.replace("{$i}", a.toString()) }
    return s
}

@Composable
fun Y2AkioTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = OneUiTypography, content = content)
}
