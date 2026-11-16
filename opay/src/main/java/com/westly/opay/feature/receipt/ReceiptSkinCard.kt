package com.westly.opay.feature.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.UserProfile
import kotlin.math.abs

private const val MESSAGE_MAX_LENGTH = 60
private val PillBackground = Color(0x33FFFFFF)
private val PanelBackground = Color(0x33000000)
private val PanelDivider = Color(0x55FFFFFF)

@Composable
fun ReceiptSkinCard(
    skin: ReceiptSkin,
    transaction: Transaction,
    profile: UserProfile,
    modifier: Modifier = Modifier,
    message: String = skin.message,
    onMessageChange: ((String) -> Unit)? = null
) {
    if (skin == ReceiptSkin.Default) {
        DefaultReceiptCard(transaction = transaction, profile = profile, modifier = modifier)
        return
    }
    val credit = isCreditTransfer(transaction)
    val sender = if (credit) counterpartyParty(transaction, maskedAccount = true)
    else ownerParty(profile, maskedAccount = true)
    val recipient = if (credit) ownerParty(profile, maskedAccount = true)
    else counterpartyParty(transaction, maskedAccount = true)
    val headline = when (receiptKind(transaction)) {
        ReceiptKind.Airtime, ReceiptKind.Data -> purchaseHeadline(transaction)
        else -> "Transfer from " + sender.name
    }
    val colors = skin.gradient
    val brush = if (colors.size == 3) {
        Brush.verticalGradient(0f to colors[0], 0.55f to colors[1], 1f to colors[2])
    } else {
        Brush.verticalGradient(0f to colors[0], 1f to colors[1])
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(brush)
    ) {
        Text(
            text = "OPay",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                .background(PillBackground)
                .padding(horizontal = 14.dp, vertical = 2.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = headline,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 22.dp)
            )
            Text(
                text = formatNaira(abs(transaction.amountKobo)),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                contentAlignment = Alignment.Center
            ) {
                SkinArt(color = skin.accent)
            }
            MessagePanel(
                recipientName = recipient.name,
                message = message,
                onMessageChange = onMessageChange,
                recipientLine = recipient.line.orEmpty(),
                dateText = formatReceiptDate(transaction.timestampMillis),
                txNo = transaction.txNo
            )
        }
    }
}

@Composable
private fun MessagePanel(
    recipientName: String,
    message: String,
    onMessageChange: ((String) -> Unit)?,
    recipientLine: String,
    dateText: String,
    txNo: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanelBackground)
            .padding(14.dp)
    ) {
        Text(text = "Dear $recipientName", fontSize = 15.sp, color = Color.White)
        BasicTextField(
            value = message,
            onValueChange = { value ->
                if (onMessageChange != null && value.length <= MESSAGE_MAX_LENGTH) {
                    onMessageChange(value)
                }
            },
            enabled = onMessageChange != null,
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 21.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Bold
            ),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, bottom = 12.dp)
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
        ) {
            drawLine(
                color = PanelDivider,
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))
            )
        }
        PanelRow(label = "Recipient", value = recipientLine)
        PanelRow(label = "Transaction Date", value = dateText)
        PanelRow(label = "Transaction No.", value = txNo)
    }
}

@Composable
private fun PanelRow(label: String, value: String) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 9.dp)
    ) {
        val valueMaxWidth = maxWidth - 102.dp
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                fontSize = 14.sp,
                color = Color.White,
                modifier = Modifier
                    .widthIn(max = 92.dp)
                    .padding(end = 10.dp)
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.White,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(max = valueMaxWidth)
            )
        }
    }
}
