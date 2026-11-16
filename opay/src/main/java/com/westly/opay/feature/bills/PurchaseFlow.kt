package com.westly.opay.feature.bills

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.model.Transaction
import com.westly.opay.data.model.TxType
import com.westly.opay.feature.transfer.SelectedTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

internal const val NOT_AVAILABLE_MESSAGE = ""

private const val MIN_PROCESSING_MILLIS = 1_600L
private const val FAILURE_MILLIS = 2_000L
private const val LOCAL_NUMBER_LENGTH = 11
private const val MAX_SESSION_NUMBERS = 3
private val MtnPrefixes = setOf(
    "0803", "0806", "0810", "0813", "0814", "0816", "0903", "0906", "0913", "0916"
)
private val AirtelPrefixes = setOf("0802", "0808", "0812", "0701", "0708", "0902", "0907", "0912")
private val GloPrefixes = setOf("0805", "0807", "0811", "0815", "0705", "0905")
private val NineMobilePrefixes = setOf("0809", "0817", "0818", "0908", "0909")

data class PurchaseRequest(
    val type: TxType,
    val title: String,
    val subtitle: String,
    val counterparty: String?,
    val amountKobo: Long,
    val storedTitle: String = title,
    val remark: String? = null
)

private enum class PurchaseStage { Confirm, Processing, Success }

/**
 * Keeps the last numbers used for a purchase while the app process is alive.
 */
internal object SessionNumbers {
    private val state = MutableStateFlow<List<String>>(emptyList())

    val used: StateFlow<List<String>> = state.asStateFlow()

    fun add(digits: String) {
        state.value = (listOf(digits) + state.value.filterNot { it == digits }).take(MAX_SESSION_NUMBERS)
    }
}

internal fun localPhoneDigits(raw: String): String {
    val digits = raw.filter { it in '0'..'9' }
    return if (digits.startsWith("234") && digits.length == LOCAL_NUMBER_LENGTH + 2) {
        "0" + digits.substring(3)
    } else {
        digits.take(LOCAL_NUMBER_LENGTH)
    }
}

internal fun formatPhoneDisplay(raw: String): String {
    val digits = raw.filter { it in '0'..'9' }.take(LOCAL_NUMBER_LENGTH)
    val builder = StringBuilder()
    digits.forEachIndexed { index, ch ->
        if (index == 4 || index == 7) builder.append(' ')
        builder.append(ch)
    }
    return builder.toString()
}

internal fun isCompleteNumber(digits: String): Boolean =
    digits.length == LOCAL_NUMBER_LENGTH && digits.startsWith("0")

/**
 * Detects the network from the four-digit prefix. A prefix outside the four lists
 * defaults to MTN so every number shows a network.
 */
internal fun detectNetwork(digits: String): String? {
    if (digits.length < 4) return null
    val prefix = digits.take(4)
    return when (prefix) {
        in AirtelPrefixes -> "Airtel"
        in GloPrefixes -> "Glo"
        in NineMobilePrefixes -> "T2 Mobile"
        in MtnPrefixes -> "MTN"
        else -> "MTN"
    }
}

@Composable
fun PurchaseFlow(
    request: PurchaseRequest?,
    onFinished: () -> Unit,
    onViewDetails: () -> Unit,
    onAddMoney: () -> Unit,
    onSuccess: () -> Unit = {}
) {
    if (request != null) {
        PurchaseFlowContent(
            request = request,
            onFinished = onFinished,
            onViewDetails = onViewDetails,
            onAddMoney = onAddMoney,
            onSuccess = onSuccess
        )
    }
}

@Composable
private fun PurchaseFlowContent(
    request: PurchaseRequest,
    onFinished: () -> Unit,
    onViewDetails: () -> Unit,
    onAddMoney: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val balanceKobo by repository.balanceKobo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(PurchaseStage.Confirm) }
    var completed by remember { mutableStateOf<Transaction?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(failure) {
        if (failure != null) {
            delay(FAILURE_MILLIS)
            failure = null
        }
    }

    fun pay() {
        if (stage != PurchaseStage.Confirm) return
        if (request.amountKobo > repository.balanceKobo.value) {
            failure = "Insufficient Balance"
            return
        }
        stage = PurchaseStage.Processing
        scope.launch {
            try {
                val startedAt = System.currentTimeMillis()
                val transaction = repository.purchase(
                    type = request.type,
                    title = request.storedTitle,
                    counterparty = request.counterparty,
                    amountKobo = request.amountKobo,
                    remark = request.remark
                )
                delay(MIN_PROCESSING_MILLIS - (System.currentTimeMillis() - startedAt))
                completed = transaction
                stage = PurchaseStage.Success
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                stage = PurchaseStage.Confirm
                failure = error.message ?: "Payment failed"
            }
        }
    }

    OPayBottomSheet(
        visible = stage == PurchaseStage.Confirm,
        onDismiss = onFinished
    ) {
        ConfirmContent(
            request = request,
            balanceKobo = balanceKobo,
            failure = failure,
            onClose = onFinished,
            onAddMoney = onAddMoney,
            onPay = { pay() }
        )
    }
    OPayBottomSheet(
        visible = stage == PurchaseStage.Success,
        onDismiss = onFinished
    ) {
        val transaction = completed
        if (transaction != null) {
            SuccessContent(
                transaction = transaction,
                onDone = onFinished,
                onViewDetails = {
                    SelectedTransaction.select(transaction)
                    onViewDetails()
                }
            )
        }
    }
    if (stage == PurchaseStage.Processing) {
        ProcessingOverlay()
    }
}

@Composable
private fun ConfirmContent(
    request: PurchaseRequest,
    balanceKobo: Long,
    failure: String?,
    onClose: () -> Unit,
    onAddMoney: () -> Unit,
    onPay: () -> Unit
) {
    val c = OPay.colors
    val insufficient = request.amountKobo > balanceKobo

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Close"; role = Role.Button }
                .clickable(onClick = onClose),
            contentAlignment = Alignment.CenterStart
        ) {
            OPayIconView(icon = OPayIcon.Close, size = 18.dp, tint = c.text)
        }
        Text(
            text = "Confirm Payment",
            style = OPayType.title,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(28.dp))
    }
    Text(
        text = formatNaira(request.amountKobo),
        style = OPayType.display,
        color = c.text,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 14.dp)
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.card)
            .background(c.cardAlt)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        PurchaseRow(label = "Item", value = request.title)
        PurchaseRow(label = "Details", value = request.subtitle)
        PurchaseRow(label = "Fee", value = formatNaira(0L))
        PurchaseRow(label = "Payment Method", value = "OWealth (${formatNaira(balanceKobo)})")
        if (insufficient) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Insufficient Balance",
                    style = OPayType.bodySmall,
                    color = c.danger,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Add Money >",
                    style = OPayType.bodySmall,
                    color = c.green,
                    modifier = Modifier.clickable(onClick = onAddMoney)
                )
            }
        }
    }
    if (failure != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = failure,
                style = OPayType.body,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
    OPayButton(
        text = "Pay",
        onClick = onPay,
        modifier = Modifier.padding(top = 16.dp),
        enabled = !insufficient
    )
}

@Composable
private fun PurchaseRow(label: String, value: String) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, style = OPayType.bodySmall, color = c.muted)
        Text(
            text = value,
            style = OPayType.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = c.text,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SuccessContent(
    transaction: Transaction,
    onDone: () -> Unit,
    onViewDetails: () -> Unit
) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(c.green),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Check, size = 28.dp, tint = Color.White)
        }
    }
    Text(
        text = "Payment successful",
        fontSize = 19.sp,
        color = c.text,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    )
    Text(
        text = formatNaira(abs(transaction.amountKobo)),
        style = OPayType.display,
        color = c.text,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 20.dp)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OPayButton(
            text = "View Details",
            onClick = onViewDetails,
            modifier = Modifier.weight(1f),
            style = ButtonStyle.Outline
        )
        OPayButton(
            text = "Done",
            onClick = onDone,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProcessingOverlay() {
    val c = OPay.colors
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        RemoveWindowDim()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(c.bg)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OPayLoader()
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "Transaction is currently being processed",
                    fontSize = 17.sp,
                    color = c.text,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Please do not close this page until the process is complete",
                    style = OPayType.body,
                    color = c.green,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
