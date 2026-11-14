package com.gorisse.thomas.willitfit.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The scheme from one source colour, deep teal #1F6F78.
private val Light = lightColorScheme(
    primary = Color(0xFF00696F), onPrimary = Color.White,
    primaryContainer = Color(0xFF9DF0F7), onPrimaryContainer = Color(0xFF002022),
    secondary = Color(0xFF4A6365), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8EA), onSecondaryContainer = Color(0xFF051F21),
    // Warm paper surfaces, as in the target: the teal reads better on them than on mint.
    surface = Color(0xFFFAF7F2), onSurface = Color(0xFF1C1B1A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F4EE),
    surfaceContainer = Color(0xFFF2EDE6), surfaceContainerHigh = Color(0xFFEDE7DF),
    surfaceContainerHighest = Color(0xFFE7E1D8),
    onSurfaceVariant = Color(0xFF3F4949), outline = Color(0xFF6F7979), outlineVariant = Color(0xFFBEC8C9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF80D4DB), onPrimary = Color(0xFF00363A),
    primaryContainer = Color(0xFF004F54), onPrimaryContainer = Color(0xFF9DF0F7),
    secondary = Color(0xFFB1CBCE), onSecondary = Color(0xFF1B3436),
    secondaryContainer = Color(0xFF324B4D), onSecondaryContainer = Color(0xFFCCE8EA),
    surface = Color(0xFF0E1415), onSurface = Color(0xFFDEE4E4),
    surfaceContainerLowest = Color(0xFF090F10), surfaceContainerLow = Color(0xFF171D1E),
    surfaceContainer = Color(0xFF1B2122), surfaceContainerHigh = Color(0xFF252B2C),
    surfaceContainerHighest = Color(0xFF303637),
    onSurfaceVariant = Color(0xFFBEC8C9), outline = Color(0xFF899393), outlineVariant = Color(0xFF3F4949),
)

/** Semantic colours the charter adds to M3: a green for "it goes through". */
object FitColors {
    @Composable fun success(): Color = if (isSystemInDarkTheme()) Color(0xFF8DD99A) else Color(0xFF1E6B33)
}

@Composable
fun WillItFitTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // The brand teal on every phone, not the wallpaper's colours: the furniture, the buttons
    // and the store captures all speak the same colour.
    val scheme = if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, content = content)
}
