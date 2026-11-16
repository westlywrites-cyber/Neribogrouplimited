package com.westly.opay.feature.receipt

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.data.model.Beneficiary
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import com.westly.opay.feature.bills.NetworkLogo
import com.westly.opay.feature.bills.formatPhoneDisplay
import com.westly.opay.feature.transfer.SelectedTransaction
import com.westly.opay.feature.transfer.TransferViewModel
import com.westly.opay.navigation.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val ReportIssueFill = Color(0xFF14473A)

@Composable
fun TransactionDetailsScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected by SelectedTransaction.current.collectAsStateWithLifecycle()
    val transaction = selected
    if (transaction == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(OPay.colors.bg)
        )
        LaunchedEffect(Unit) { onBack() }
    } else {
        DetailsContent(
            transaction = transaction,
            onBack = onBack,
            onNavigate = onNavigate,
            modifier = modifier
        )
    }
}

/** The "expected to be credited within 5 minutes" note belongs to money sent only. */
internal fun showsRecipientBankNote(transaction: Transaction): Boolean =
    transaction.type == TxType.TransferOut

/** Every received transfer starts its detail list with "Credited to Available Balance". */
internal fun showsCreditedToRow(transaction: Transaction): Boolean =
    isCreditTransfer(transaction)

private fun stepTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(millis)).replace(' ', '\n')

@Composable
private fun DetailsContent(
    transaction: Transaction,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val clipboard = LocalClipboardManager.current
    val toast = rememberOPayToast()
    val copyValue: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        toast.show("Copied")
    }
    val credit = isCreditTransfer(transaction)
    val party = if (credit) counterpartyParty(transaction, maskedAccount = true)
    else counterpartyParty(transaction, maskedAccount = false)
    val bankDeposit = isBankDeposit(transaction)
    val purchase = receiptKind(transaction).let { it == ReceiptKind.Airtime || it == ReceiptKind.Data }
    val purchaseDetails = if (purchase) PurchaseDetails.from(transaction) else null
    val partyValue = when {
        purchaseDetails != null ->
            formatPhoneDisplay(purchaseDetails.phone.filter { it in '0'..'9' }).ifEmpty { "-" }
        party.line != null -> party.name + "\n" + party.line
        else -> party.name
    }
    val isTransfer = transaction.type == TxType.TransferOut || transaction.type == TxType.TransferIn
    val verb = if (credit) "Transfer from " else "Transfer to "
    val heading = when {
        isTransfer -> verb + party.name
        purchase -> if (transaction.type == TxType.Data) "Mobile Data" else "Airtime"
        else -> transaction.title
    }
    val activity = LocalContext.current as ComponentActivity
    val transferViewModel = viewModel<TransferViewModel>(viewModelStoreOwner = activity)
    val transferAgain: (() -> Unit)? = if (isTransfer && canRepeat(transaction)) {
        {
            val bank = transaction.counterpartyBank.orEmpty()
            transferViewModel.reset()
            transferViewModel.selectBeneficiary(
                Beneficiary(
                    name = transaction.counterpartyName.orEmpty(),
                    accountNumber = transaction.counterpartyAccount.orEmpty(),
                    bankName = bank,
                    isFavourite = false
                )
            )
            onNavigate(if (bank == "OPay") Routes.TransferAmount else Routes.TransferBank)
        }
    } else {
        null
    }
    val initial = party.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "O"
    val noteText = buildAnnotatedString {
        append("The recipient account is expected to be credited within 5 minutes, subject to notification by the bank. If you have any questions, you can also ")
        withStyle(SpanStyle(color = c.green)) { append("contact the recipient bank >>") }
    }

    val badge = detailsBadgeFor(transaction)
    val badged = badge != DetailsBadge.None
    val opayHeader = badge == DetailsBadge.Opay
    val showPill = opayHeader && credit
    val showProgress = !(badged && credit)
    val dark = c.bg.luminance() < 0.5f
    val statusColor = if (transaction.status == "Successful") c.green else c.muted
    val headerShape = RoundedCornerShape(16.dp)
    val headerFill: Brush = if (opayHeader) {
        Brush.horizontalGradient(
            if (dark) listOf(Color(0xFF14392F), Color(0xFF103A38))
            else listOf(Color(0xFFC6F0DF), Color(0xFFA2E9DA))
        )
    } else {
        SolidColor(c.card)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bg)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.card)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Back"; role = Role.Button }
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
                }
                Text(
                    text = "Transaction Details",
                    fontSize = 18.sp,
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Help"; role = Role.Button }
                        .clickable { onNavigate(Routes.Help) },
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Support, size = 24.dp, tint = c.text)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Box(modifier = Modifier.padding(top = if (badged) 44.dp else 32.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerFill, headerShape)
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = if (badged) 44.dp else 40.dp,
                                bottom = 16.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = heading,
                            fontSize = 16.sp,
                            color = c.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = formatNaira(abs(transaction.amountKobo)),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = c.text,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = if (showPill) 0.dp else 10.dp)
                        )
                        if (showPill) {
                            Row(
                                modifier = Modifier
                                    .padding(top = 12.dp)
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = if (dark) 0.12f else 0.55f))
                                    .clickable(role = Role.Button) { onNavigate(Routes.Transactions) }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "View more payments received",
                                    fontSize = 14.sp,
                                    color = c.text
                                )
                                Spacer(Modifier.width(4.dp))
                                OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = c.text)
                            }
                        }
                        if (opayHeader) {
                            Row(
                                modifier = Modifier.padding(top = if (showPill) 14.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .background(c.text, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OPayIconView(icon = OPayIcon.Check, size = 14.dp, tint = c.bg)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = transaction.status,
                                    fontSize = 20.sp,
                                    color = statusColor
                                )
                            }
                        } else {
                            Text(
                                text = transaction.status,
                                fontSize = 18.sp,
                                color = statusColor,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (showProgress) {
                            ProgressDots(modifier = Modifier.padding(top = 18.dp))
                            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                StepLabel(
                                    label = "Payment successful",
                                    time = stepTime(transaction.timestampMillis),
                                    modifier = Modifier.weight(1f)
                                )
                                StepLabel(
                                    label = if (isTransfer) "Processing by bank" else "Processing",
                                    time = stepTime(transaction.timestampMillis),
                                    modifier = Modifier.weight(1f)
                                )
                                StepLabel(
                                    label = if (isTransfer) "Received by bank" else "Completed",
                                    time = stepTime(transaction.timestampMillis),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (showsRecipientBankNote(transaction)) {
                                Text(
                                    text = noteText,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = c.muted,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 14.dp)
                                        .background(c.bg, RoundedCornerShape(14.dp))
                                        .padding(14.dp)
                                )
                            }
                        }
                    }
                    when (badge) {
                        is DetailsBadge.Bank -> BankLogoBadge(
                            bankName = badge.name,
                            opay = false,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-30).dp)
                        )
                        DetailsBadge.Opay -> BankLogoBadge(
                            bankName = "OPay",
                            opay = true,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-30).dp)
                        )
                        DetailsBadge.None -> Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-24).dp)
                                .size(48.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (purchaseDetails != null && purchaseDetails.network != "-") {
                                NetworkLogo(name = purchaseDetails.network, size = 48.dp)
                            } else {
                                Text(
                                    text = initial,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1B1B1B)
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp, bottom = 16.dp)
                        .background(c.card, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Transaction Details",
                        fontSize = 18.sp,
                        color = c.text,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    if (showsCreditedToRow(transaction)) {
                        DetailRow(label = "Credited to", value = "Available Balance \u203A")
                    }
                    DetailRow(
                        label = if (credit) "Sender Details" else "Recipient Details",
                        value = partyValue
                    )
                    if (credit) {
                        if (bankDeposit) {
                            if (transaction.remark.orEmpty().isNotBlank()) {
                                DetailRow(label = "Remark", value = transaction.remark.orEmpty())
                            }
                            DetailRow(label = "Transaction Type", value = "Bank Deposit")
                        }
                        DetailRow(label = "Transaction No.", value = transaction.txNo, onCopy = { copyValue(transaction.txNo) })
                        DetailRow(
                            label = "Transaction Date",
                            value = formatReceiptDate(transaction.timestampMillis)
                        )
                        if (bankDeposit) {
                            DetailRow(label = "Session ID", value = transaction.sessionId, onCopy = { copyValue(transaction.sessionId) })
                        }
                    } else {
                        DetailRow(label = "Payment Method", value = "OWealth")
                        DetailRow(label = "Transaction No.", value = transaction.txNo, onCopy = { copyValue(transaction.txNo) })
                        DetailRow(label = "Session ID", value = transaction.sessionId, onCopy = { copyValue(transaction.sessionId) })
                        DetailRow(
                            label = "Transaction Date",
                            value = formatReceiptDate(transaction.timestampMillis)
                        )
                    }
                }
                if (!purchase) {
                    MoreActionsCard(
                        transaction = transaction,
                        bankDeposit = bankDeposit,
                        onTransferAgain = transferAgain,
                        onViewRecords = { onNavigate(Routes.Transactions) }
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (!bankDeposit) {
                    ActionPill(
                        text = "Report Issue",
                        background = ReportIssueFill,
                        textColor = c.green,
                        onClick = { onNavigate(Routes.Help) },
                        modifier = Modifier.weight(1f)
                    )
                }
                ActionPill(
                    text = "Share Receipt",
                    background = c.green,
                    textColor = c.onGreen,
                    onClick = { onNavigate(Routes.Receipt) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun ProgressDots(modifier: Modifier = Modifier) {
    val c = OPay.colors
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.66f)
                .height(2.dp)
                .background(c.green)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(3) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(c.card, CircleShape)
                            .padding(3.dp)
                            .background(c.green, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        OPayIconView(icon = OPayIcon.Check, size = 14.dp, tint = c.onGreen)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepLabel(label: String, time: String, modifier: Modifier = Modifier) {
    val c = OPay.colors
    Column(modifier = modifier.padding(horizontal = 2.dp)) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = time,
            fontSize = 12.sp,
            color = c.text,
            textAlign = TextAlign.Start
        )
    }
}

private fun canRepeat(transaction: Transaction): Boolean {
    val digits = transaction.counterpartyAccount.orEmpty().filter { it in '0'..'9' }
    return digits.length == 10 &&
        !transaction.counterpartyBank.isNullOrBlank() &&
        !transaction.counterpartyName.isNullOrBlank()
}

private fun categoryLabel(transaction: Transaction): String =
    transaction.category ?: when (transaction.type) {
        TxType.TransferOut, TxType.TransferIn -> "Transfer"
        TxType.Interest -> "Interest"
        TxType.Withdraw -> "Withdraw"
        TxType.SafeBox -> "SafeBox"
        TxType.Bill -> "Bills"
        TxType.Airtime -> "Airtime"
        TxType.Data -> "Data"
    }

@Composable
private fun MoreActionsCard(
    transaction: Transaction,
    bankDeposit: Boolean,
    onTransferAgain: (() -> Unit)?,
    onViewRecords: () -> Unit
) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .background(c.card, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "More Actions",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = c.text,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Category",
                fontSize = 14.sp,
                color = c.muted,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (bankDeposit) "Withdraw / Deposit" else categoryLabel(transaction),
                fontSize = 14.sp,
                color = c.text
            )
            Spacer(Modifier.width(4.dp))
            OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = c.muted)
        }
        Canvas(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
        ) {
            drawLine(
                color = c.muted.copy(alpha = 0.5f),
                start = Offset(0f, 0f),
                end = Offset(size.width, 0f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            if (onTransferAgain != null) {
                ActionLink(
                    icon = OPayIcon.Repeat,
                    text = if (bankDeposit) "Transfer Back" else "Transfer Again",
                    onClick = onTransferAgain
                )
            }
            if (!bankDeposit) {
                ActionLink(icon = OPayIcon.Clock, text = "View Records", onClick = onViewRecords)
            }
        }
    }
}

@Composable
private fun ActionLink(icon: OPayIcon, text: String, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OPayIconView(icon = icon, size = 16.dp, tint = c.green)
        Spacer(Modifier.width(6.dp))
        Text(text = text, fontSize = 16.sp, color = c.green)
    }
}

@Composable
private fun DetailRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = c.muted,
            modifier = Modifier.padding(end = 20.dp)
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = c.text,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (onCopy != null) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(24.dp)
                    .semantics { contentDescription = "Copy $label"; role = Role.Button }
                    .clickable(onClick = onCopy),
                contentAlignment = Alignment.Center
            ) {
                CopyGlyph(tint = c.green)
            }
        }
    }
}

@Composable
private fun CopyGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(8f * unit, 8f * unit),
            size = Size(12f * unit, 12f * unit),
            cornerRadius = CornerRadius(2f * unit, 2f * unit),
            style = stroke
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(4f * unit, 4f * unit),
            size = Size(12f * unit, 12f * unit),
            cornerRadius = CornerRadius(2f * unit, 2f * unit),
            style = stroke
        )
    }
}

@Composable
private fun ActionPill(
    text: String,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(30.dp))
            .background(background)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            maxLines = 1
        )
    }
}
