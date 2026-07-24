package com.ismartcoding.plain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Teal = Color(0xFF0B7285)
private val TealLight = Color(0xFF15AABF)
private val TealDark = Color(0xFF0C5460)

private val LightColors = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDF3),
    onPrimaryContainer = Color(0xFF04353F),
    secondary = TealLight,
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDF1F4),
)

private val DarkColors = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF04252B),
    primaryContainer = TealDark,
    onPrimaryContainer = Color(0xFFCDEDF3),
    secondary = Teal,
    background = Color(0xFF12141C),
    surface = Color(0xFF1A1D27),
    surfaceVariant = Color(0xFF232733),
)

@Composable
fun MwiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
