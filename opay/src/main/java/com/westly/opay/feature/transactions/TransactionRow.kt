package com.westly.opay.feature.transactions

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

private val RowZone: ZoneId = ZoneId.of("Africa/Lagos")
private val CoinPurple = Color(0xFFA66BFF)
private val CoinPurpleSoft = Color(0x33A66BFF)

private fun daySuffix(day: Int): String = when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

internal fun formatRowDate(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(RowZone)
    val day = t.dayOfMonth
    return MonthNames[t.monthValue - 1] + " " + day + daySuffix(day) + ", " +
        String.format(Locale.US, "%02d:%02d:%02d", t.hour, t.minute, t.second)
}

@Composable
fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val credit = transaction.amountKobo > 0
    val amountText = if (credit) {
        "+" + formatNaira(transaction.amountKobo)
    } else {
        "-" + formatNaira(abs(transaction.amountKobo))
    }
    val statusColor = when (transaction.status) {
        "Successful" -> c.green
        "Pending" -> c.warn
        else -> c.danger
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TransactionIcon(transaction = transaction)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.title,
                style = OPayType.body,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatRowDate(transaction.timestampMillis),
                style = OPayType.captionSmall,
                color = c.muted
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountText,
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = if (credit) c.green else c.text
            )
            Text(
                text = transaction.status,
                style = OPayType.captionSmall,
                color = statusColor
            )
        }
    }
}

@Composable
private fun TransactionIcon(transaction: Transaction) {
    val c = OPay.colors
    val coin = transaction.type == TxType.Interest
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(if (coin) CoinPurpleSoft else c.greenSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (transaction.type) {
            TxType.Interest -> CoinGlyph()
            TxType.Airtime, TxType.Data ->
                OPayIconView(icon = OPayIcon.Phone, size = 16.dp, tint = c.green)
            TxType.Bill -> OPayIconView(icon = OPayIcon.Bank, size = 16.dp, tint = c.green)
            else -> ArrowGlyph(up = transaction.amountKobo < 0, tint = c.green)
        }
    }
}

@Composable
private fun ArrowGlyph(up: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val unit = size.minDimension / 24f
        val tail = if (up) 19f else 5f
        val head = if (up) 5f else 19f
        val wing = if (up) 6f else -6f
        val width = 2f * unit
        drawLine(
            color = tint,
            start = Offset(12f * unit, tail * unit),
            end = Offset(12f * unit, head * unit),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(6f * unit, (head + wing) * unit),
            end = Offset(12f * unit, head * unit),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(18f * unit, (head + wing) * unit),
            end = Offset(12f * unit, head * unit),
            strokeWidth = width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun CoinGlyph() {
    Canvas(modifier = Modifier.size(16.dp)) {
        val unit = size.minDimension / 24f
        drawCircle(color = CoinPurple, radius = 11f * unit)
        drawCircle(
            color = Color.White.copy(alpha = 0.7f),
            radius = 6.5f * unit,
            style = Stroke(width = 1.6f * unit)
        )
    }
}
