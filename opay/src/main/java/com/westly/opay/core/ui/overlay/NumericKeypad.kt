package com.westly.opay.core.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType

@Composable
fun NumericKeypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.card)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShieldGlyph(green = c.green, mark = c.onGreen)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "OPay Secure Numeric Keypad",
                style = OPayType.caption,
                color = c.muted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Done",
                style = OPayType.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = c.green,
                modifier = Modifier
                    .clickable(onClick = onDone)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.divider),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            listOf("123", "456", "789").forEach { digits ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    digits.forEach { digit ->
                        KeyCell(modifier = Modifier.weight(1f), onClick = { onDigit(digit) }) {
                            DigitLabel(digit)
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                KeyCell(modifier = Modifier.weight(1f), onClick = null) {}
                KeyCell(modifier = Modifier.weight(1f), onClick = { onDigit('0') }) {
                    DigitLabel('0')
                }
                KeyCell(modifier = Modifier.weight(1f), onClick = onDelete) {
                    BackspaceGlyph(color = c.text)
                }
            }
        }
    }
}

@Composable
private fun KeyCell(
    modifier: Modifier,
    onClick: (() -> Unit)?,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .height(58.dp)
            .background(OPay.colors.bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun DigitLabel(digit: Char) {
    Text(
        text = digit.toString(),
        style = OPayType.body.copy(fontSize = 21.sp),
        color = OPay.colors.text,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ShieldGlyph(green: Color, mark: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, h * 0.06f)
            lineTo(w * 0.88f, h * 0.2f)
            lineTo(w * 0.88f, h * 0.5f)
            cubicTo(w * 0.88f, h * 0.72f, w * 0.7f, h * 0.86f, w * 0.5f, h * 0.94f)
            cubicTo(w * 0.3f, h * 0.86f, w * 0.12f, h * 0.72f, w * 0.12f, h * 0.5f)
            lineTo(w * 0.12f, h * 0.2f)
            close()
        }
        drawPath(shield, green)
        val tick = Path().apply {
            moveTo(w * 0.34f, h * 0.5f)
            lineTo(w * 0.46f, h * 0.62f)
            lineTo(w * 0.68f, h * 0.38f)
        }
        drawPath(
            tick,
            mark,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun BackspaceGlyph(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val outline = Path().apply {
            moveTo(w * 0.34f, h * 0.22f)
            lineTo(w * 0.92f, h * 0.22f)
            lineTo(w * 0.92f, h * 0.78f)
            lineTo(w * 0.34f, h * 0.78f)
            lineTo(w * 0.08f, h * 0.5f)
            close()
        }
        drawPath(
            outline,
            color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawLine(color, Offset(w * 0.5f, h * 0.38f), Offset(w * 0.74f, h * 0.62f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.74f, h * 0.38f), Offset(w * 0.5f, h * 0.62f), stroke, StrokeCap.Round)
    }
}
