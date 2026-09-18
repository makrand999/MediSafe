package com.example.medac.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MedacColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = CardSurface,
    primaryContainer = NavyDark,
    onPrimaryContainer = CardSurface,
    secondary = BlueInfoText,
    onSecondary = CardSurface,
    secondaryContainer = BlueInfo,
    onSecondaryContainer = BlueInfoText,
    background = AppBackground,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = PausedMuted,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    error = WarningRed,
    onError = CardSurface
)

@Composable
fun MedacTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MedacColorScheme,
        typography = Typography,
        content = content
    )
}
