package com.example.recordvoice.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RecordPwaColorScheme = lightColorScheme(
    primary = CoralPrimary,
    onPrimary = Color.White,
    primaryContainer = CoralSoftBg,
    onPrimaryContainer = CoralPrimaryDeep,
    secondary = DarkNavy,
    onSecondary = Color.White,
    tertiary = OnlineGreen,
    background = AppBackground,
    onBackground = DarkNavy,
    surface = CardSurface,
    onSurface = DarkNavy,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = SlateSecondary,
    outline = BorderSubtle
)

@Composable
fun RecordVoiceTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RecordPwaColorScheme,
        typography = Typography,
        content = content
    )
}
