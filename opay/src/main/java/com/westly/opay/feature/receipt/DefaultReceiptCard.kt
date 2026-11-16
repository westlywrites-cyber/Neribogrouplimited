package com.westly.opay.feature.receipt

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.util.formatNaira
import com.westly.opay.core.util.maskAccount
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import com.westly.opay.data.model.UserProfile
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

private val ReceiptZone: ZoneId = ZoneId.of("Africa/Lagos")
private val MonthNames = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private const val FOOTER_TEXT = "Enjoy a better life with OPay. Get free transfers, withdrawals, " +
    "bill payments, instant loans, and good annual interest On your savings. " +
    "OPay is licensed by the Central Bank of Nigeria and insured by the NDIC."

private const val WATERMARK_ANGLE_DEGREES = -18f

/**
 * Every size of the default receipt. [ScreenMetrics] is the receipt as it shows in the app. [ExportMetrics] is the
 * receipt that Share as image and Share as PDF save: sizes and spacing measured from a real OPay receipt on a
 * 360dp wide card, with line boxes fixed so each line lands where it does in the real one.
 */
internal data class ReceiptMetrics(
    val export: Boolean,
    val cornerRadius: Dp,
    val edgeRadius: Dp,
    val edgeStep: Dp,
    val edgeStart: Dp,
    val padStart: Dp,
    val padEnd: Dp,
    val padTop: Dp,
    val padBottom: Dp,
    val amountFont: TextUnit,
    val amountLine: TextUnit,
    val amountTop: Dp,
    val statusFont: TextUnit,
    val statusLine: TextUnit,
    val statusTop: Dp,
    val dateFont: TextUnit,
    val dateLine: TextUnit,
    val dateTop: Dp,
    val dateBottom: Dp,
    val dividerHeight: Dp,
    val rowFont: TextUnit,
    val rowLine: TextUnit,
    val rowGap: Dp,
    val labelEnd: Dp,
    val valueFraction: Float,
    val purchaseGap: Dp,
    val purchaseLine: TextUnit,
    val dashTop: Dp,
    val dashStroke: Dp,
    val dashOn: Dp,
    val dashOff: Dp,
    val footerFont: TextUnit,
    val footerLine: TextUnit,
    val footerTop: Dp,
    val watermarkTileWidth: Float,
    val watermarkTileHeight: Float,
    val watermarkTextX: Float,
    val watermarkBaseline: Float,
    val watermarkFont: TextUnit
)

internal val ScreenMetrics = ReceiptMetrics(
    export = false,
    cornerRadius = 10.dp,
    edgeRadius = 4.dp,
    edgeStep = 12.dp,
    edgeStart = 6.dp,
    padStart = 16.dp,
    padEnd = 16.dp,
    padTop = 40.dp,
    padBottom = 14.dp,
    amountFont = 22.sp,
    amountLine = TextUnit.Unspecified,
    amountTop = 18.dp,
    statusFont = 18.sp,
    statusLine = TextUnit.Unspecified,
    statusTop = 4.dp,
    dateFont = 9.sp,
    dateLine = TextUnit.Unspecified,
    dateTop = 4.dp,
    dateBottom = 12.dp,
    dividerHeight = 1.dp,
    rowFont = 12.sp,
    rowLine = TextUnit.Unspecified,
    rowGap = 14.dp,
    labelEnd = 12.dp,
    valueFraction = 0.58f,
    purchaseGap = 25.dp,
    purchaseLine = 14.sp,
    dashTop = 14.dp,
    dashStroke = 1.dp,
    dashOn = 4.dp,
    dashOff = 3.dp,
    footerFont = 10.sp,
    footerLine = 14.sp,
    footerTop = 14.dp,
    watermarkTileWidth = 144f,
    watermarkTileHeight = 64f,
    watermarkTextX = 20f,
    watermarkBaseline = 40f,
    watermarkFont = 22.sp
)

internal val ExportMetrics = ReceiptMetrics(
    export = true,
    cornerRadius = 10.dp,
    edgeRadius = 4.4.dp,
    edgeStep = 13.2.dp,
    edgeStart = 6.4.dp,
    padStart = 17.9.dp,
    padEnd = 17.9.dp,
    padTop = 0.dp,
    padBottom = 16.dp,
    amountFont = 29.3.sp,
    amountLine = 34.sp,
    amountTop = 4.3.dp,
    statusFont = 26.3.sp,
    statusLine = 31.sp,
    statusTop = 3.7.dp,
    dateFont = 14.4.sp,
    dateLine = 17.sp,
    dateTop = 3.8.dp,
    dateBottom = 13.6.dp,
    dividerHeight = 1.1.dp,
    rowFont = 19.sp,
    rowLine = 22.3.sp,
    rowGap = 15.2.dp,
    labelEnd = 16.dp,
    valueFraction = 0.54f,
    purchaseGap = 30.3.dp,
    purchaseLine = 22.3.sp,
    dashTop = 14.9.dp,
    dashStroke = 1.3.dp,
    dashOn = 4.4.dp,
    dashOff = 3.3.dp,
    footerFont = 16.sp,
    footerLine = 22.3.sp,
    footerTop = 15.5.dp,
    watermarkTileWidth = 158f,
    watermarkTileHeight = 70.2f,
    watermarkTextX = 21f,
    watermarkBaseline = 40f,
    watermarkFont = 24.5.sp
)

internal val LocalReceiptMetrics = compositionLocalOf { ScreenMetrics }

// Fixed line boxes without font padding, so a line's baseline sits at a known place in its box.
private val ExportTextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

@Composable
private fun ReceiptMetrics.baseStyle(): TextStyle = if (export) ExportTextStyle else LocalTextStyle.current

internal data class ReceiptParty(val name: String, val line: String?)

private fun ordinalSuffix(day: Int): String = when {
    day % 100 in 11..13 -> "th"
    day % 10 == 1 -> "st"
    day % 10 == 2 -> "nd"
    day % 10 == 3 -> "rd"
    else -> "th"
}

internal fun formatReceiptTime(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ReceiptZone)
    return String.format(Locale.US, "%02d:%02d:%02d", t.hour, t.minute, t.second)
}

internal fun formatReceiptDate(millis: Long): String {
    val t = Instant.ofEpochMilli(millis).atZone(ReceiptZone)
    val day = t.dayOfMonth
    return MonthNames[t.monthValue - 1] + " " + day + ordinalSuffix(day) + ", " +
        t.year + " " + formatReceiptTime(millis)
}

internal fun isCreditTransfer(transaction: Transaction): Boolean =
    transaction.type == TxType.TransferIn

internal fun officialBankName(bank: String?): String? = when (bank) {
    "First Bank" -> "First Bank Of Nigeria"
    else -> bank
}

internal fun isBankDeposit(transaction: Transaction): Boolean =
    transaction.type == TxType.TransferIn &&
        !transaction.counterpartyBank.isNullOrBlank() &&
        transaction.counterpartyBank != "OPay"

internal fun counterpartyParty(transaction: Transaction, maskedAccount: Boolean): ReceiptParty {
    val account = transaction.counterpartyAccount?.let { if (maskedAccount) maskAccount(it) else it }
    val line = listOfNotNull(officialBankName(transaction.counterpartyBank), account)
        .joinToString(" | ")
        .ifEmpty { null }
    return ReceiptParty(transaction.counterpartyName ?: transaction.title, line)
}

internal fun ownerParty(profile: UserProfile, maskedAccount: Boolean): ReceiptParty {
    val account = if (maskedAccount) maskAccount(profile.accountNumber) else profile.accountNumber
    return ReceiptParty(profile.fullName.uppercase(Locale.ROOT), "OPay | $account")
}

internal fun watermarkRowShift(row: Int, tileWidth: Float): Float =
    if (row % 2 == 0) 0f else tileWidth / 2f

@Composable
internal fun Modifier.receiptWatermark(): Modifier {
    val measurer = rememberTextMeasurer()
    val color = receiptColors().watermark
    val m = LocalReceiptMetrics.current
    val layout = remember(measurer, color, m) {
        measurer.measure(
            text = "OPay",
            style = TextStyle(color = color, fontSize = m.watermarkFont, fontWeight = FontWeight.Bold)
        )
    }
    return drawBehind {
        val tileWidth = m.watermarkTileWidth.dp.toPx()
        val tileHeight = m.watermarkTileHeight.dp.toPx()
        val textX = m.watermarkTextX.dp.toPx()
        val baselineY = m.watermarkBaseline.dp.toPx()
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = watermarkRowShift(row, tileWidth) - tileWidth
            while (x < size.width) {
                withTransform({
                    translate(left = x + textX, top = y + baselineY)
                    rotate(degrees = WATERMARK_ANGLE_DEGREES, pivot = Offset.Zero)
                }) {
                    drawText(textLayoutResult = layout, topLeft = Offset(0f, -layout.firstBaseline))
                }
                x += tileWidth
            }
            y += tileHeight
            row += 1
        }
    }
}

@Composable
fun DefaultReceiptCard(
    transaction: Transaction,
    profile: UserProfile,
    modifier: Modifier = Modifier
) {
    val kind = receiptKind(transaction)
    ReceiptFrame(transaction = transaction, modifier = modifier) { r ->
        when (kind) {
            ReceiptKind.Airtime, ReceiptKind.Data ->
                PurchaseRows(transaction = transaction, kind = kind, palette = r)
            ReceiptKind.Transfer, ReceiptKind.Other ->
                TransferRows(transaction = transaction, profile = profile, palette = r)
        }
    }
}

@Composable
internal fun ReceiptFrame(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    rows: @Composable (ReceiptPalette) -> Unit
) {
    val r = receiptColors()
    val export = LocalReceiptExport.current
    val m = if (export) ExportMetrics else ScreenMetrics
    val edgeColor = if (export) Color.Black else r.page
    CompositionLocalProvider(LocalReceiptMetrics provides m) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(m.cornerRadius))
                .background(r.card)
                .receiptWatermark()
                .drawWithContent {
                    drawContent()
                    val radius = m.edgeRadius.toPx()
                    val step = m.edgeStep.toPx()
                    var x = m.edgeStart.toPx()
                    while (x < size.width + step) {
                        drawCircle(color = edgeColor, radius = radius, center = Offset(x, 0f))
                        drawCircle(color = edgeColor, radius = radius, center = Offset(x, size.height))
                        x += step
                    }
                }
                .padding(start = m.padStart, end = m.padEnd, top = m.padTop, bottom = m.padBottom)
        ) {
            if (export) {
                ExportHeader(palette = r)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ReceiptLogo(color = r.green)
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "Pay",
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                            color = r.title
                        )
                    }
                    Text(
                        text = "Transaction Receipt",
                        fontSize = 16.sp,
                        color = r.title,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Text(
                text = formatNaira(abs(transaction.amountKobo)),
                style = m.baseStyle(),
                fontSize = m.amountFont,
                lineHeight = m.amountLine,
                fontWeight = FontWeight.Bold,
                color = r.green,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = m.amountTop)
            )
            Text(
                text = transaction.status,
                style = m.baseStyle(),
                fontSize = m.statusFont,
                lineHeight = m.statusLine,
                color = r.title,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = m.statusTop)
            )
            Text(
                text = formatReceiptDate(transaction.timestampMillis),
                style = m.baseStyle(),
                fontSize = m.dateFont,
                lineHeight = m.dateLine,
                color = r.label,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = m.dateTop, bottom = m.dateBottom)
            )
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(m.dividerHeight)
                    .background(r.divider)
            )
            rows(r)
            Canvas(
                modifier = Modifier
                    .padding(top = m.dashTop)
                    .fillMaxWidth()
                    .height(m.dashStroke)
            ) {
                drawLine(
                    color = r.label,
                    start = Offset(0f, size.height / 2f),
                    end = Offset(size.width, size.height / 2f),
                    strokeWidth = m.dashStroke.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(m.dashOn.toPx(), m.dashOff.toPx()))
                )
            }
            Text(
                text = FOOTER_TEXT,
                style = m.baseStyle(),
                fontSize = m.footerFont,
                lineHeight = m.footerLine,
                color = r.label,
                modifier = Modifier.padding(top = m.footerTop)
            )
        }
    }
}

/**
 * The header of the shared receipt: logo and title placed so that their baselines match the real OPay receipt
 * (logo baseline 76.5dp and title baseline 72.4dp from the top, on a 360dp wide card).
 */
@Composable
private fun ExportHeader(palette: ReceiptPalette) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.4.dp)
        ) {
            ReceiptLogo(
                color = palette.green,
                size = 26.3.dp,
                modifier = Modifier.offset(y = 51.35.dp)
            )
            Spacer(Modifier.width(1.5.dp))
            Text(
                text = "Pay",
                style = ExportTextStyle,
                fontSize = 35.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-2.5).sp,
                color = palette.title,
                modifier = Modifier.offset(y = 44.55.dp)
            )
        }
        Text(
            text = "Transaction Receipt",
            style = ExportTextStyle,
            fontSize = 24.7.sp,
            lineHeight = 29.sp,
            color = palette.title,
            textAlign = TextAlign.End,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = 49.47.dp)
        )
    }
}

@Composable
private fun TransferRows(transaction: Transaction, profile: UserProfile, palette: ReceiptPalette) {
    val credit = isCreditTransfer(transaction)
    val opayToOpay = transaction.counterpartyBank == "OPay"
    val bankDeposit = isBankDeposit(transaction)
    val recipient = if (credit) ownerParty(profile, maskedAccount = true)
    else counterpartyParty(transaction, maskedAccount = opayToOpay)
    val sender = if (credit) counterpartyParty(transaction, maskedAccount = true)
    else ownerParty(profile, maskedAccount = true)
    ReceiptRow(label = "Recipient Details", name = recipient.name, line = recipient.line, palette = palette)
    ReceiptRow(label = "Sender Details", name = sender.name, line = sender.line, palette = palette)
    if (bankDeposit) {
        ReceiptRow(
            label = "Transaction Type",
            name = "Bank Deposit",
            line = null,
            palette = palette,
            nameWeight = FontWeight.Normal
        )
    }
    ReceiptRow(
        label = "Transaction No.",
        name = transaction.txNo,
        line = null,
        palette = palette,
        nameWeight = FontWeight.Normal
    )
    if (!opayToOpay) {
        ReceiptRow(
            label = "Session ID",
            name = transaction.sessionId,
            line = null,
            palette = palette,
            nameWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun PurchaseRows(transaction: Transaction, kind: ReceiptKind, palette: ReceiptPalette) {
    val details = PurchaseDetails.from(transaction)
    val data = kind == ReceiptKind.Data
    PurchaseRow(label = "Mobile Network Operators", value = details.network, palette = palette)
    PurchaseRow(label = "Recipient Mobile", value = details.phone, palette = palette)
    if (data) {
        PurchaseRow(label = "Data Bundle", value = details.plan ?: "-", palette = palette)
    }
    PurchaseRow(
        label = "Transaction Type",
        value = if (data) "Mobile Data" else "Airtime",
        palette = palette
    )
    val m = LocalReceiptMetrics.current
    ReceiptRow(
        label = "Transaction No.",
        name = transaction.txNo,
        line = null,
        palette = palette,
        nameWeight = FontWeight.Normal,
        gap = m.purchaseGap,
        lineHeight = m.purchaseLine
    )
}

@Composable
private fun PurchaseRow(label: String, value: String, palette: ReceiptPalette) {
    val m = LocalReceiptMetrics.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = m.purchaseGap),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = m.baseStyle(),
            fontSize = m.rowFont,
            lineHeight = m.purchaseLine,
            color = palette.label,
            modifier = Modifier.padding(end = m.labelEnd)
        )
        Text(
            text = value,
            style = m.baseStyle(),
            fontSize = m.rowFont,
            lineHeight = m.purchaseLine,
            color = palette.value,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun ReceiptLogo(color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 50.5f
        val centerX = 26.5f * unit
        val centerY = 25.25f * unit
        val radius = 19.75f * unit
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 320f,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 8.5f * unit)
        )
        drawRect(
            color = color,
            topLeft = Offset(0f, 21.55f * unit),
            size = Size(14f * unit, 7.4f * unit)
        )
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    name: String,
    line: String?,
    palette: ReceiptPalette,
    nameWeight: FontWeight = FontWeight.Bold,
    gap: Dp? = null,
    lineHeight: TextUnit? = null
) {
    val m = LocalReceiptMetrics.current
    val rowGap = gap ?: m.rowGap
    val rowLine = lineHeight ?: m.rowLine
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = rowGap)
    ) {
        val maxValueWidth = maxWidth * m.valueFraction
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = m.baseStyle(),
                fontSize = m.rowFont,
                lineHeight = rowLine,
                color = palette.label,
                modifier = Modifier.padding(end = m.labelEnd)
            )
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = maxValueWidth)
            ) {
                Text(
                    text = name,
                    style = m.baseStyle(),
                    fontSize = m.rowFont,
                    lineHeight = rowLine,
                    fontWeight = nameWeight,
                    color = palette.value,
                    textAlign = TextAlign.End
                )
                if (line != null) {
                    Text(
                        text = line,
                        style = m.baseStyle(),
                        fontSize = m.rowFont,
                        lineHeight = rowLine,
                        fontWeight = FontWeight.Normal,
                        color = palette.value,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}
