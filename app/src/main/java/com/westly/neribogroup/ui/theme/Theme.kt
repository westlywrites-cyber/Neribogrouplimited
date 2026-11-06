package com.westly.neribogroup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6F6B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EBE8),
    onPrimaryContainer = Color(0xFF0F3533),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF1B1F1E),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF1B1F1E),
    surfaceVariant = Color(0xFFE4EAE8),
    onSurfaceVariant = Color(0xFF4A5553),
    outline = Color(0xFF8A9693),
    outlineVariant = Color(0xFFCBD5D2),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CCBC6),
    onPrimary = Color(0xFF00302D),
    primaryContainer = Color(0xFF1F4F4B),
    onPrimaryContainer = Color(0xFFD6EBE8),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE1E6E4),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE1E6E4),
    surfaceVariant = Color(0xFF2A3331),
    onSurfaceVariant = Color(0xFFBFC9C6),
    outline = Color(0xFF899390),
    outlineVariant = Color(0xFF3B4745),
    error = Color(0xFFF2B8B5)
)

@Composable
fun NeriboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
