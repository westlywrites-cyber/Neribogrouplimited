package com.westly.opay.feature.receipt

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.westly.opay.core.theme.OPay

@Immutable
internal data class ReceiptPalette(
    val page: Color,
    val card: Color,
    val green: Color,
    val title: Color,
    val label: Color,
    val value: Color,
    val divider: Color,
    val watermark: Color
)

internal object ReceiptColors {
    val Dark = ReceiptPalette(
        page = Color(0xFF1B1B1B),
        card = Color(0xFF2C2C2C),
        green = Color(0xFF1DCF9F),
        title = Color(0xFFFFFFFF),
        label = Color(0xFFA0A0A0),
        value = Color(0xFFE0E0E0),
        divider = Color(0xFF252525),
        watermark = Color(0xFF212121)
    )

    val Light = ReceiptPalette(
        page = Color(0xFFF5F5F5),
        card = Color(0xFFFFFFFF),
        green = Color(0xFF00B070),
        title = Color(0xFF111111),
        label = Color(0xFF8A8A8A),
        value = Color(0xFF1A1A1A),
        divider = Color(0xFFE8E8E8),
        watermark = Color(0xFFEFEFEF)
    )

    fun select(dark: Boolean): ReceiptPalette = if (dark) Dark else Light
}

/** True while the receipt is drawn for sharing as an image or PDF; that file is always the white look. */
internal val LocalReceiptExport = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
internal fun receiptColors(): ReceiptPalette =
    ReceiptColors.select(dark = OPay.colors.bg.luminance() < 0.5f)
