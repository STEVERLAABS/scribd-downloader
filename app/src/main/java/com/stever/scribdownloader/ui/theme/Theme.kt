package com.stever.scribdownloader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = SteVeRPurple,
    secondary = SteVeRPink,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
)

private val LightColors = lightColorScheme(
    primary = SteVeRPurple,
    secondary = SteVeRPink,
    background = LightBackground,
    surface = LightSurface,
    surfaceVariant = LightSurfaceVariant,
)

// Brand identity is fixed: dynamic color is intentionally disabled (design doc §5).
@Composable
fun SteVeRTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SteVeRTypography,
        content = content,
    )
}
