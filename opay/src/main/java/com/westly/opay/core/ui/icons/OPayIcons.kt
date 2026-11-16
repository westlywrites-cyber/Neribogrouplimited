package com.westly.opay.core.ui.icons

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class OPayIcon {
    Home, Gift, Chart, Card, User, Me, Bank, Save, Loan, Phone, Data, Game, Tv, Bell, Scan, Help,
    Back, Forward, Share, Plus, Check, Doc, Star, Lock, Key, Chat, Bag, List, Clock, Tag, Zap, Pig,
    Close, Support, SettingsHex, Repeat
}

@Composable
fun OPayIconView(
    icon: OPayIcon,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = LocalContentColor.current
) {
    val vector = iconVectors.getValue(icon)
    Icon(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = tint
    )
}

private fun strokeIcon(name: String, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = build
    ).build()

private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcToRelative(r, r, 0f, true, false, 2 * r, 0f)
    arcToRelative(r, r, 0f, true, false, -2 * r, 0f)
    close()
}

private fun PathBuilder.roundRect(x: Float, y: Float, w: Float, h: Float, rx: Float) {
    moveTo(x + rx, y)
    horizontalLineTo(x + w - rx)
    arcToRelative(rx, rx, 0f, false, true, rx, rx)
    verticalLineTo(y + h - rx)
    arcToRelative(rx, rx, 0f, false, true, -rx, rx)
    horizontalLineTo(x + rx)
    arcToRelative(rx, rx, 0f, false, true, -rx, -rx)
    verticalLineTo(y + rx)
    arcToRelative(rx, rx, 0f, false, true, rx, -rx)
    close()
}

private val iconVectors: Map<OPayIcon, ImageVector> by lazy {
    mapOf(
        OPayIcon.Home to strokeIcon("Home") {
            circle(12f, 12f, 8f)
            circle(12f, 12f, 2.5f)
        },
        OPayIcon.Gift to strokeIcon("Gift") {
            moveTo(12f, 20f)
            reflectiveCurveToRelative(-8f, -5f, -8f, -10f)
            arcToRelative(4.5f, 4.5f, 0f, false, true, 8f, -2f)
            arcToRelative(4.5f, 4.5f, 0f, false, true, 8f, 2f)
            curveToRelative(0f, 5f, -8f, 10f, -8f, 10f)
            close()
        },
        OPayIcon.Chart to strokeIcon("Chart") {
            moveTo(4f, 18f)
            lineToRelative(5f, -6f)
            lineToRelative(4f, 3f)
            lineToRelative(7f, -9f)
            moveTo(4f, 21f)
            horizontalLineToRelative(16f)
        },
        OPayIcon.Card to strokeIcon("Card") {
            roundRect(3f, 6f, 18f, 12f, 2f)
            moveTo(3f, 10f)
            horizontalLineToRelative(18f)
        },
        OPayIcon.User to strokeIcon("User") {
            circle(12f, 8f, 4f)
            moveTo(4f, 21f)
            curveToRelative(1f, -5f, 15f, -5f, 16f, 0f)
        },
        OPayIcon.Me to strokeIcon("Me") {
            circle(12f, 12f, 9f)
            moveTo(8f, 14f)
            curveToRelative(2f, 3f, 6f, 3f, 8f, 0f)
        },
        OPayIcon.Bank to strokeIcon("Bank") {
            moveTo(3f, 10f)
            lineToRelative(9f, -6f)
            lineToRelative(9f, 6f)
            moveTo(5f, 10f)
            verticalLineToRelative(8f)
            moveTo(10f, 10f)
            verticalLineToRelative(8f)
            moveTo(14f, 10f)
            verticalLineToRelative(8f)
            moveTo(19f, 10f)
            verticalLineToRelative(8f)
            moveTo(3f, 20f)
            horizontalLineToRelative(18f)
        },
        OPayIcon.Save to strokeIcon("Save") {
            roundRect(4f, 6f, 16f, 13f, 2f)
            moveTo(9f, 12f)
            horizontalLineToRelative(6f)
            moveTo(12f, 9f)
            verticalLineToRelative(6f)
        },
        OPayIcon.Loan to strokeIcon("Loan") {
            moveTo(12f, 21f)
            reflectiveCurveToRelative(-7f, -4f, -7f, -10f)
            verticalLineTo(5f)
            lineToRelative(7f, -2f)
            lineToRelative(7f, 2f)
            verticalLineToRelative(6f)
            curveToRelative(0f, 6f, -7f, 10f, -7f, 10f)
            close()
        },
        OPayIcon.Phone to strokeIcon("Phone") {
            roundRect(7f, 3f, 10f, 18f, 2f)
            moveTo(11f, 18f)
            horizontalLineToRelative(2f)
        },
        OPayIcon.Data to strokeIcon("Data") {
            roundRect(6f, 3f, 12f, 18f, 2f)
            moveTo(9f, 8f)
            horizontalLineToRelative(6f)
            moveTo(9f, 12f)
            horizontalLineToRelative(6f)
        },
        OPayIcon.Game to strokeIcon("Game") {
            circle(12f, 12f, 3f)
            moveTo(12f, 3f)
            verticalLineToRelative(3f)
            moveTo(12f, 18f)
            verticalLineToRelative(3f)
            moveTo(3f, 12f)
            horizontalLineToRelative(3f)
            moveTo(18f, 12f)
            horizontalLineToRelative(3f)
            moveTo(6f, 6f)
            lineToRelative(2f, 2f)
            moveTo(16f, 16f)
            lineToRelative(2f, 2f)
            moveTo(18f, 6f)
            lineToRelative(-2f, 2f)
            moveTo(8f, 16f)
            lineToRelative(-2f, 2f)
        },
        OPayIcon.Tv to strokeIcon("Tv") {
            roundRect(3f, 6f, 18f, 12f, 2f)
            moveTo(9f, 3f)
            lineToRelative(3f, 3f)
            lineToRelative(3f, -3f)
        },
        OPayIcon.Bell to strokeIcon("Bell") {
            moveTo(6f, 17f)
            verticalLineTo(11f)
            arcToRelative(6f, 6f, 0f, false, true, 12f, 0f)
            verticalLineToRelative(6f)
            lineToRelative(2f, 2f)
            horizontalLineTo(4f)
            close()
            moveTo(10f, 21f)
            horizontalLineToRelative(4f)
        },
        OPayIcon.Scan to strokeIcon("Scan") {
            moveTo(4f, 9f)
            verticalLineToRelative(-2.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, -2.5f)
            horizontalLineToRelative(2.5f)
            moveTo(15f, 4f)
            horizontalLineToRelative(2.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, 2.5f)
            verticalLineToRelative(2.5f)
            moveTo(20f, 15f)
            verticalLineToRelative(2.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, -2.5f, 2.5f)
            horizontalLineToRelative(-2.5f)
            moveTo(9f, 20f)
            horizontalLineToRelative(-2.5f)
            arcToRelative(2.5f, 2.5f, 0f, false, true, -2.5f, -2.5f)
            verticalLineToRelative(-2.5f)
            moveTo(7.5f, 12f)
            horizontalLineToRelative(9f)
        },
        OPayIcon.Support to strokeIcon("Support") {
            moveTo(5f, 12f)
            arcToRelative(7f, 7f, 0f, false, true, 14f, 0f)
            moveTo(6f, 11f)
            horizontalLineTo(5f)
            arcToRelative(1.5f, 1.5f, 0f, false, false, -1.5f, 1.5f)
            verticalLineToRelative(1.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, false, 1.5f, 1.5f)
            horizontalLineToRelative(1f)
            close()
            moveTo(18f, 11f)
            horizontalLineToRelative(1f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, 1.5f, 1.5f)
            verticalLineToRelative(1.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, -1.5f, 1.5f)
            horizontalLineToRelative(-1f)
            close()
            moveTo(19.5f, 15.5f)
            verticalLineToRelative(1f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 3f)
            horizontalLineToRelative(-2.5f)
            moveTo(9.5f, 13.5f)
            arcToRelative(3f, 3f, 0f, false, false, 5f, 0f)
        },
        OPayIcon.SettingsHex to strokeIcon("SettingsHex") {
            moveTo(12f, 3f)
            lineTo(19.8f, 7.5f)
            lineTo(19.8f, 16.5f)
            lineTo(12f, 21f)
            lineTo(4.2f, 16.5f)
            lineTo(4.2f, 7.5f)
            close()
            circle(12f, 12f, 2.8f)
        },
        OPayIcon.Repeat to strokeIcon("Repeat") {
            circle(12f, 12f, 9f)
            moveTo(7.5f, 10f)
            horizontalLineToRelative(8f)
            moveTo(13.5f, 7.5f)
            lineToRelative(2.5f, 2.5f)
            lineToRelative(-2.5f, 2.5f)
            moveTo(16.5f, 14f)
            horizontalLineToRelative(-8f)
            moveTo(10.5f, 11.5f)
            lineToRelative(-2.5f, 2.5f)
            lineToRelative(2.5f, 2.5f)
        },
        OPayIcon.Help to strokeIcon("Help") {
            moveTo(4f, 14f)
            verticalLineToRelative(-2f)
            arcToRelative(8f, 8f, 0f, false, true, 16f, 0f)
            verticalLineToRelative(2f)
            moveTo(4f, 14f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(5f)
            horizontalLineTo(4f)
            close()
            moveTo(17f, 14f)
            horizontalLineToRelative(3f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(-3f)
            close()
        },
        OPayIcon.Back to strokeIcon("Back") {
            moveTo(15f, 5f)
            lineToRelative(-7f, 7f)
            lineToRelative(7f, 7f)
        },
        OPayIcon.Forward to strokeIcon("Forward") {
            moveTo(9f, 5f)
            lineToRelative(7f, 7f)
            lineToRelative(-7f, 7f)
        },
        OPayIcon.Share to strokeIcon("Share") {
            circle(6f, 12f, 2.5f)
            circle(17f, 6f, 2.5f)
            circle(17f, 18f, 2.5f)
            moveTo(8f, 11f)
            lineToRelative(7f, -4f)
            moveTo(8f, 13f)
            lineToRelative(7f, 4f)
        },
        OPayIcon.Plus to strokeIcon("Plus") {
            circle(12f, 12f, 9f)
            moveTo(12f, 8f)
            verticalLineToRelative(8f)
            moveTo(8f, 12f)
            horizontalLineToRelative(8f)
        },
        OPayIcon.Check to strokeIcon("Check") {
            moveTo(5f, 12f)
            lineToRelative(5f, 5f)
            lineToRelative(9f, -10f)
        },
        OPayIcon.Doc to strokeIcon("Doc") {
            roundRect(5f, 3f, 14f, 18f, 2f)
            moveTo(9f, 9f)
            horizontalLineToRelative(6f)
            moveTo(9f, 13f)
            horizontalLineToRelative(6f)
        },
        OPayIcon.Star to strokeIcon("Star") {
            moveTo(12f, 3f)
            lineToRelative(3f, 6f)
            lineToRelative(6f, 1f)
            lineToRelative(-4.5f, 4.5f)
            lineToRelative(1f, 6.5f)
            lineToRelative(-5.5f, -3f)
            lineToRelative(-5.5f, 3f)
            lineToRelative(1f, -6.5f)
            lineTo(3f, 10f)
            lineToRelative(6f, -1f)
            close()
        },
        OPayIcon.Lock to strokeIcon("Lock") {
            roundRect(5f, 11f, 14f, 9f, 2f)
            moveTo(8f, 11f)
            verticalLineTo(8f)
            arcToRelative(4f, 4f, 0f, false, true, 8f, 0f)
            verticalLineToRelative(3f)
        },
        OPayIcon.Key to strokeIcon("Key") {
            circle(8f, 12f, 4f)
            moveTo(12f, 12f)
            horizontalLineToRelative(9f)
            moveTo(18f, 12f)
            verticalLineToRelative(4f)
        },
        OPayIcon.Chat to strokeIcon("Chat") {
            moveTo(4f, 5f)
            horizontalLineToRelative(16f)
            verticalLineToRelative(11f)
            horizontalLineTo(9f)
            lineToRelative(-5f, 4f)
            close()
        },
        OPayIcon.Bag to strokeIcon("Bag") {
            moveTo(5f, 8f)
            horizontalLineToRelative(14f)
            lineToRelative(-1f, 12f)
            horizontalLineTo(6f)
            close()
            moveTo(9f, 8f)
            arcToRelative(3f, 3f, 0f, false, true, 6f, 0f)
        },
        OPayIcon.List to strokeIcon("List") {
            moveTo(5f, 7f)
            horizontalLineToRelative(14f)
            moveTo(5f, 12f)
            horizontalLineToRelative(14f)
            moveTo(5f, 17f)
            horizontalLineToRelative(14f)
        },
        OPayIcon.Clock to strokeIcon("Clock") {
            circle(12f, 12f, 9f)
            moveTo(12f, 7f)
            verticalLineToRelative(5f)
            lineToRelative(3f, 2f)
        },
        OPayIcon.Tag to strokeIcon("Tag") {
            moveTo(3f, 12f)
            verticalLineTo(4f)
            horizontalLineToRelative(8f)
            lineToRelative(10f, 10f)
            lineToRelative(-8f, 8f)
            close()
            circle(8f, 8f, 1.5f)
        },
        OPayIcon.Zap to strokeIcon("Zap") {
            moveTo(13f, 3f)
            lineTo(5f, 14f)
            horizontalLineToRelative(6f)
            lineToRelative(-1f, 7f)
            lineToRelative(8f, -11f)
            horizontalLineToRelative(-6f)
            close()
        },
        OPayIcon.Pig to strokeIcon("Pig") {
            moveTo(5f, 12f)
            arcToRelative(7f, 6f, 0f, false, true, 14f, 0f)
            curveToRelative(0f, 3f, -2f, 5f, -4f, 5f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(-3f)
            horizontalLineTo(9f)
            verticalLineToRelative(3f)
            horizontalLineTo(6f)
            verticalLineToRelative(-3f)
            curveToRelative(-1f, -1f, -1f, -3f, -1f, -5f)
            close()
        },
        OPayIcon.Close to strokeIcon("Close") {
            moveTo(6f, 6f)
            lineToRelative(12f, 12f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    )
}
