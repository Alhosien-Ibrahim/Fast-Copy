package com.example.fastcopy.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.*
import androidx.compose.runtime.Composable

private val LightColorPalette = lightColors(
    primary = LightPrimary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightSurface,
    onBackground = LightText,
    onSurface = LightText
)

private val DarkColorPalette = darkColors(
    primary = DarkPrimary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = DarkSurface,
    onBackground = DarkText,
    onSurface = DarkText
)

@Composable
fun FastCopyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
