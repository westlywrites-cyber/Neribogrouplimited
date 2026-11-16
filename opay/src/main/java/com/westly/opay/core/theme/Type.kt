package com.westly.opay.core.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Every text in the app is drawn at this share of the size it is written with (and of the phone's font size
 * setting), so the whole app has the compact text of the real OPay app. Set once in [OPayTheme].
 */
const val TextScale = 0.86f

/** The density with [TextScale] taken out again, for content that must keep its own fixed sizes. */
@Composable
fun rememberUnscaledTextDensity(): Density {
    val density = LocalDensity.current
    return remember(density) { Density(density.density, density.fontScale / TextScale) }
}

object OPayType {
    val body = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal)
    val bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal)
    val bodyMuted: TextStyle
        @Composable @ReadOnlyComposable
        get() = body.copy(color = OPay.colors.muted)
    val titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal)
    val title = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Normal)
    val headline = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val display = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)
    val captionSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal)
    val money = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum"
    )
}

val OPayMaterialTypography = Typography(
    bodyLarge = OPayType.body,
    bodyMedium = OPayType.body,
    bodySmall = OPayType.caption,
    titleMedium = OPayType.titleSmall,
    titleLarge = OPayType.title,
    headlineMedium = OPayType.headline,
    headlineLarge = OPayType.display,
    labelMedium = OPayType.caption,
    labelSmall = OPayType.captionSmall
)
