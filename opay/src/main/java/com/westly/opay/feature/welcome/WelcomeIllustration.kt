package com.westly.opay.feature.welcome

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay

private class ArtPalette(
    val backdrop: Color,
    val card: Color,
    val line: Color,
    val green: Color,
    val greenDark: Color,
    val onGreen: Color
)

/** Decorative vector drawing for a welcome slide. Not announced by screen readers. */
@Composable
fun WelcomeIllustration(art: WelcomeArt, modifier: Modifier = Modifier) {
    val c = OPay.colors
    val palette = remember(c) {
        ArtPalette(
            backdrop = c.green.copy(alpha = 0.14f),
            card = c.card,
            line = c.muted.copy(alpha = 0.35f),
            green = c.green,
            greenDark = c.greenDark,
            onGreen = c.onGreen
        )
    }
    Canvas(modifier = modifier.size(240.dp)) {
        drawCircle(color = palette.backdrop, radius = 112f * unit(), center = center)
        when (art) {
            WelcomeArt.Welcome -> drawWelcomeArt(palette)
            WelcomeArt.Send -> drawSendArt(palette)
            WelcomeArt.Bills -> drawBillsArt(palette)
            WelcomeArt.Save -> drawSaveArt(palette)
            WelcomeArt.Secure -> drawSecureArt(palette)
        }
    }
}

private fun DrawScope.unit(): Float = size.width / 240f

private fun DrawScope.drawWelcomeArt(p: ArtPalette) {
    val u = unit()
    val left = 72f * u
    val top = 36f * u
    val w = 96f * u
    val h = 168f * u
    drawRoundRect(p.card, Offset(left, top), Size(w, h), CornerRadius(16f * u))
    drawRoundRect(
        p.green, Offset(left, top), Size(w, h), CornerRadius(16f * u),
        style = Stroke(width = 3f * u)
    )
    drawRoundRect(
        p.green, Offset(left + 10f * u, top + 22f * u), Size(w - 20f * u, 26f * u), CornerRadius(8f * u)
    )
    for (i in 0..2) {
        drawRoundRect(
            p.line,
            Offset(left + 10f * u, top + 62f * u + i * 24f * u),
            Size(w - 20f * u, 12f * u),
            CornerRadius(6f * u)
        )
    }
    val badge = Offset(left + w - 4f * u, top + h - 26f * u)
    drawCircle(p.greenDark, radius = 28f * u, center = badge)
    drawCircle(p.green, radius = 24f * u, center = badge)
    val check = Path().apply {
        moveTo(badge.x - 11f * u, badge.y + 1f * u)
        lineTo(badge.x - 3f * u, badge.y + 9f * u)
        lineTo(badge.x + 12f * u, badge.y - 8f * u)
    }
    drawPath(
        check, p.onGreen,
        style = Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawSendArt(p: ArtPalette) {
    val u = unit()
    drawRoundRect(p.card, Offset(30f * u, 80f * u), Size(50f * u, 80f * u), CornerRadius(8f * u))
    drawRoundRect(
        p.green, Offset(30f * u, 80f * u), Size(50f * u, 80f * u), CornerRadius(8f * u),
        style = Stroke(width = 3f * u)
    )
    drawCircle(p.green, radius = 10f * u, center = Offset(55f * u, 120f * u))
    drawLine(
        p.green, Offset(94f * u, 120f * u), Offset(138f * u, 120f * u),
        strokeWidth = 5f * u, cap = StrokeCap.Round
    )
    val head = Path().apply {
        moveTo(128f * u, 108f * u)
        lineTo(142f * u, 120f * u)
        lineTo(128f * u, 132f * u)
    }
    drawPath(
        head, p.green,
        style = Stroke(width = 5f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    val roof = Path().apply {
        moveTo(184f * u, 78f * u)
        lineTo(220f * u, 108f * u)
        lineTo(148f * u, 108f * u)
        close()
    }
    drawPath(roof, p.greenDark)
    listOf(156f, 174f, 192f).forEach { x ->
        drawRect(p.green, Offset(x * u, 112f * u), Size(11f * u, 44f * u))
    }
    drawRect(p.greenDark, Offset(148f * u, 158f * u), Size(72f * u, 9f * u))
}

private fun DrawScope.drawBillsArt(p: ArtPalette) {
    val u = unit()
    val receipt = Path().apply {
        moveTo(70f * u, 40f * u)
        lineTo(170f * u, 40f * u)
        lineTo(170f * u, 190f * u)
        lineTo(150f * u, 178f * u)
        lineTo(130f * u, 190f * u)
        lineTo(110f * u, 178f * u)
        lineTo(90f * u, 190f * u)
        lineTo(70f * u, 178f * u)
        close()
    }
    drawPath(receipt, p.card)
    drawPath(receipt, p.green, style = Stroke(width = 3f * u, join = StrokeJoin.Round))
    listOf(100f, 74f, 88f).forEachIndexed { i, width ->
        drawRoundRect(
            p.line,
            Offset(86f * u, (68f + i * 24f) * u),
            Size(width * u * 0.8f, 11f * u),
            CornerRadius(5.5f * u)
        )
    }
    val badge = Offset(170f * u, 168f * u)
    drawCircle(p.greenDark, radius = 32f * u, center = badge)
    drawCircle(p.green, radius = 28f * u, center = badge)
    val bolt = Path().apply {
        moveTo(176f * u, 148f * u)
        lineTo(160f * u, 172f * u)
        lineTo(171f * u, 172f * u)
        lineTo(164f * u, 190f * u)
        lineTo(182f * u, 164f * u)
        lineTo(171f * u, 164f * u)
        close()
    }
    drawPath(bolt, p.onGreen)
}

private fun DrawScope.drawSaveArt(p: ArtPalette) {
    val u = unit()
    val bottom = 196f * u
    listOf(
        Triple(50f, 44f, p.greenDark),
        Triple(94f, 80f, p.green),
        Triple(138f, 116f, p.green)
    ).forEach { (x, height, color) ->
        drawRoundRect(
            color,
            Offset(x * u, bottom - height * u),
            Size(34f * u, height * u),
            CornerRadius(6f * u)
        )
    }
    drawLine(p.line, Offset(36f * u, bottom + 4f * u), Offset(204f * u, bottom + 4f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
    val coin = Offset(172f * u, 62f * u)
    drawCircle(p.greenDark, radius = 28f * u, center = coin)
    drawCircle(p.green, radius = 24f * u, center = coin)
    drawCircle(p.onGreen, radius = 14f * u, center = coin, style = Stroke(width = 3f * u))
    val arrow = Path().apply {
        moveTo(coin.x - 6f * u, coin.y + 6f * u)
        lineTo(coin.x, coin.y - 6f * u)
        lineTo(coin.x + 6f * u, coin.y + 6f * u)
    }
    drawPath(
        arrow, p.onGreen,
        style = Stroke(width = 3f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawSecureArt(p: ArtPalette) {
    val u = unit()
    val shield = Path().apply {
        moveTo(120f * u, 28f * u)
        lineTo(190f * u, 54f * u)
        lineTo(190f * u, 114f * u)
        cubicTo(190f * u, 158f * u, 158f * u, 184f * u, 120f * u, 200f * u)
        cubicTo(82f * u, 184f * u, 50f * u, 158f * u, 50f * u, 114f * u)
        lineTo(50f * u, 54f * u)
        close()
    }
    drawPath(shield, p.card)
    drawPath(shield, p.green, style = Stroke(width = 4f * u, join = StrokeJoin.Round))
    drawArc(
        color = p.greenDark,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(106f * u, 82f * u),
        size = Size(28f * u, 36f * u),
        style = Stroke(width = 6f * u, cap = StrokeCap.Round)
    )
    drawLine(p.greenDark, Offset(106f * u, 100f * u), Offset(106f * u, 114f * u), strokeWidth = 6f * u, cap = StrokeCap.Round)
    drawLine(p.greenDark, Offset(134f * u, 100f * u), Offset(134f * u, 114f * u), strokeWidth = 6f * u, cap = StrokeCap.Round)
    drawRoundRect(p.green, Offset(98f * u, 108f * u), Size(44f * u, 40f * u), CornerRadius(8f * u))
    drawCircle(p.onGreen, radius = 5f * u, center = Offset(120f * u, 124f * u))
    drawRoundRect(
        p.onGreen, Offset(118f * u, 126f * u), Size(4f * u, 11f * u), CornerRadius(2f * u)
    )
}
