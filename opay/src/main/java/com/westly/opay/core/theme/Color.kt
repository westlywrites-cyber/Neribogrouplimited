package com.westly.opay.core.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class OPayColors(
    val bg: Color,
    val card: Color,
    val cardAlt: Color,
    val green: Color,
    val greenDark: Color,
    val greenSoft: Color,
    val text: Color,
    val muted: Color,
    val divider: Color,
    val danger: Color,
    val warn: Color,
    val onGreen: Color
)

val OPayDarkColors = OPayColors(
    bg = Color(0xFF1B1B1B),
    card = Color(0xFF2C2C2C),
    cardAlt = Color(0xFF3A3A3D),
    green = Color(0xFF1DCF9F),
    greenDark = Color(0xFF1D7A5A),
    greenSoft = Color(0xFF1F4A3B),
    text = Color(0xFFF2F2F2),
    muted = Color(0xFF9B9B9F),
    divider = Color(0xFF38383B),
    danger = Color(0xFFFF5C7A),
    warn = Color(0xFFC9A227),
    onGreen = Color(0xFF042619)
)

val OPayLightColors = OPayColors(
    bg = Color(0xFFF2F3F5),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFECEEF0),
    green = Color(0xFF1DCF9F),
    greenDark = Color(0xFF1D7A5A),
    greenSoft = Color(0xFF1F4A3B),
    text = Color(0xFF1B1B1B),
    muted = Color(0xFF7A7A80),
    divider = Color(0xFFE6E6E9),
    danger = Color(0xFFFF5C7A),
    warn = Color(0xFFC9A227),
    onGreen = Color(0xFF042619)
)

internal val LocalOPayColors = staticCompositionLocalOf { OPayDarkColors }
