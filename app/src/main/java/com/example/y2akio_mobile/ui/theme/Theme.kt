package com.example.y2akio_mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.y2akio_mobile.settings.AppLanguage
import com.example.y2akio_mobile.settings.AppTheme
import com.example.y2akio_mobile.settings.Strings

val BrandPinkLight = Color(0xFFF58AFB)
val BrandPink = Color(0xFFF25EE7)
val BrandGradient = Brush.linearGradient(listOf(BrandPinkLight, BrandPink))

private val LightColors = lightColorScheme(
    primary = Color(0xFFA9259F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD6F8),
    onPrimaryContainer = Color(0xFF3A0036),
    secondary = Color(0xFF6E5869),
    secondaryContainer = Color(0xFFF7DAEE),
    onSecondaryContainer = Color(0xFF271624),
    background = Color(0xFFFFF7FB),
    surface = Color(0xFFFFF7FB),
    surfaceContainerLow = Color(0xFFFBF0F7),
    surfaceContainer = Color(0xFFF5EAF2),
    surfaceContainerHigh = Color(0xFFEFE4EC),
)

private val DarkColors = darkColorScheme(
    primary = BrandPinkLight,
    onPrimary = Color(0xFF5B0056),
    primaryContainer = Color(0xFF80107A),
    onPrimaryContainer = Color(0xFFFFD6F8),
    secondary = Color(0xFFDBBFD2),
    secondaryContainer = Color(0xFF54414F),
    onSecondaryContainer = Color(0xFFF7DAEE),
    background = Color(0xFF17121A),
    surface = Color(0xFF17121A),
    surfaceContainerLow = Color(0xFF1F1A22),
    surfaceContainer = Color(0xFF241E26),
    surfaceContainerHigh = Color(0xFF2E2831),
)

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
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
