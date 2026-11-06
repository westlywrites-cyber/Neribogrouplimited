package com.westly.neribogroup.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours Material has no slot for. */
data class NeriboExtras(
    val amber: Color,
    val heroSurface: Color,
    val dangerContainer: Color
)

private val DarkExtras = NeriboExtras(
    amber = Color(0xFFE0B04A),
    heroSurface = Color(0xFF1B2B2A),
    dangerContainer = Color(0xFF3A2220)
)

private val LightExtras = NeriboExtras(
    amber = Color(0xFF9A6B00),
    heroSurface = Color(0xFFDDEBE8),
    dangerContainer = Color(0xFFF6DAD7)
)

val LocalNeriboExtras = staticCompositionLocalOf { DarkExtras }

val MaterialTheme.extras: NeriboExtras
    @Composable get() = LocalNeriboExtras.current

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6F6B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6EBE8),
    onPrimaryContainer = Color(0xFF0F3533),
    background = Color(0xFFF6F8F7),
    onBackground = Color(0xFF1B1F1E),
    surface = Color(0xFFF6F8F7),
    onSurface = Color(0xFF1B1F1E),
    surfaceVariant = Color(0xFFEAF0EE),
    onSurfaceVariant = Color(0xFF5B6865),
    surfaceContainerLowest = Color(0xFFF6F8F7),
    surfaceContainerLow = Color(0xFFEAF0EE),
    surfaceContainer = Color(0xFFEAF0EE),
    surfaceContainerHigh = Color(0xFFE0E8E5),
    surfaceContainerHighest = Color(0xFFE0E8E5),
    outline = Color(0xFF8A9693),
    outlineVariant = Color(0xFFCBD5D2),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF6DAD7),
    onErrorContainer = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CCBC6),
    onPrimary = Color(0xFF00302D),
    primaryContainer = Color(0xFF1F4F4B),
    onPrimaryContainer = Color(0xFF8CCBC6),
    background = Color(0xFF101414),
    onBackground = Color(0xFFE1E6E4),
    surface = Color(0xFF101414),
    onSurface = Color(0xFFE1E6E4),
    surfaceVariant = Color(0xFF1A2120),
    onSurfaceVariant = Color(0xFF899390),
    surfaceContainerLowest = Color(0xFF101414),
    surfaceContainerLow = Color(0xFF1A2120),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF232C2A),
    surfaceContainerHighest = Color(0xFF232C2A),
    outline = Color(0xFF5E6967),
    outlineVariant = Color(0xFF2A3331),
    error = Color(0xFFE58A84),
    onError = Color(0xFF3A2220),
    errorContainer = Color(0xFF3A2220),
    onErrorContainer = Color(0xFFE58A84)
)

private val NeriboTypography = Typography(
    displaySmall = TextStyle(fontSize = 44.sp, lineHeight = 57.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
)

@Composable
fun NeriboTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalNeriboExtras provides if (dark) DarkExtras else LightExtras) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = NeriboTypography,
            content = content
        )
    }
}
