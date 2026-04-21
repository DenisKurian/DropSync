package com.example.test.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Accent,
    background = Background,
    surface = Surface,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFF1B2341),
    onSurfaceVariant = TextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    secondary = AccentLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    onPrimary = TextPrimary,
    onSecondary = TextPrimaryLightMode,
    onBackground = TextPrimaryLightMode,
    onSurface = TextPrimaryLightMode,
    surfaceVariant = Color(0xFFE0E0E0),
    onSurfaceVariant = TextSecondaryLightMode
)

@Composable
fun TestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // REQUIRED: disable dynamic color
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}