package com.westly.opay.feature.receipt

import androidx.compose.ui.graphics.Color

enum class ReceiptSkin(
    val label: String,
    val gradient: List<Color>,
    val message: String,
    val accent: Color
) {
    Default(
        label = "Default",
        gradient = listOf(Color(0xFFEEEEEE), Color(0xFFEEEEEE)),
        message = "",
        accent = Color(0xFF1FE0A4)
    ),
    ChopLife(
        label = "Chop Life",
        gradient = listOf(Color(0xFFBDF08F), Color(0xFF52C46C), Color(0xFF2C9A52)),
        message = "Enjoy life.",
        accent = Color(0xFF2F8F4E)
    ),
    Urgent2k(
        label = "Urgent 2k",
        gradient = listOf(Color(0xFF28345F), Color(0xFF1B2442)),
        message = "Urgent payment sent.Hope it helps.",
        accent = Color(0xFF1FA35C)
    ),
    Christmas(
        label = "Christmas",
        gradient = listOf(Color(0xFF139F59), Color(0xFF0A6B3A)),
        message = "Merry christmas!",
        accent = Color(0xFFC0392B)
    ),
    Ramadan(
        label = "Ramadan",
        gradient = listOf(Color(0xFF12A05A), Color(0xFF066A38)),
        message = "Wishing you a blessed and joyful Eid.",
        accent = Color(0xFF0D7A45)
    )
}
