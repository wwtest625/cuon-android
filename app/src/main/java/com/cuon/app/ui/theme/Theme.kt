package com.cuon.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

private val AppleDarkColorScheme = darkColorScheme(
    primary = AppleBlue,
    onPrimary = AppleDarkTextPrimary,
    primaryContainer = AppleDarkBorder,
    onPrimaryContainer = AppleDarkTextPrimary,
    surface = AppleDarkBackground,
    onSurface = AppleDarkTextPrimary,
    surfaceVariant = AppleDarkCard,
    onSurfaceVariant = AppleDarkTextSecondary,
    outline = AppleDarkBorder,
    outlineVariant = AppleDarkBorder,
    background = AppleDarkBackground,
    onBackground = AppleDarkTextPrimary
)

private val AppleLightColorScheme = lightColorScheme(
    primary = AppleBlue,
    onPrimary = AppleLightCard,
    primaryContainer = AppleLightBackground,
    onPrimaryContainer = AppleLightTextPrimary,
    surface = AppleLightBackground,
    onSurface = AppleLightTextPrimary,
    surfaceVariant = AppleLightCard,
    onSurfaceVariant = AppleLightTextSecondary,
    outline = AppleLightBorder,
    outlineVariant = AppleLightBorder,
    background = AppleLightBackground,
    onBackground = AppleLightTextPrimary
)

@Composable
fun CuonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AppleDarkColorScheme else AppleLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppleTypography,
        content = content
    )
}
