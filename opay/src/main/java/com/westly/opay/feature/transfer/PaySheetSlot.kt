package com.westly.opay.feature.transfer

import android.app.Application
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.OPayApplication
import com.westly.opay.core.security.BiometricGate
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import com.westly.opay.core.ui.overlay.SecureKeypad
import com.westly.opay.core.ui.overlay.SecureWindow
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.model.TransferDraft
import com.westly.opay.feature.register.PAYMENT_PIN_LENGTH
import com.westly.opay.feature.register.PinDots
import com.westly.opay.navigation.Routes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val PROMPT_TITLE = "Pay"
private const val PROMPT_SUBTITLE = "Confirm with your fingerprint"
private const val PROMPT_NEGATIVE = "Use PIN"
private val DetailsMaxHeight = 340.dp
private val SheetPadding = 16.dp

/**
 * Carries the screen the success route must open instead of showing a receipt.
 * The pay sheet only has the three callbacks of its signature, so leaving the
 * transfer flow (cancel, Add Money, Add a Bank Card) goes through onPaid and the
 * success route forwards to the requested screen.
 */
internal object PaymentExit {
    private var route: String? = null

    fun request(target: String) {
        route = target
    }

    fun consume(): String? {
        val target = route
        route = null
        return target
    }
}

@Composable
fun PaySheetSlot(visible: Boolean, onDismiss: () -> Unit, onPaid: () -> Unit) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val transferModel = viewModel<TransferViewModel>(viewModelStoreOwner = activity)
    val application = context.applicationContext as Application
    val payModel = viewModel<PayViewModel>(
        factory = remember(transferModel) {
            PayViewModel.factory(application) { transferModel.draft.value }
        }
    )
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val draft by transferModel.draft.collectAsStateWithLifecycle()
    val balanceKobo by repository.balanceKobo.collectAsStateWithLifecycle()
    val state by payModel.state.collectAsStateWithLifecycle()
    val currentOnPaid by rememberUpdatedState(onPaid)

    var methodExpanded by remember { mutableStateOf(false) }
    var showCancel by remember { mutableStateOf(false) }

    LaunchedEffect(visible) {
        if (visible) {
            payModel.onSheetOpened()
        } else {
            methodExpanded = false
            showCancel = false
            payModel.onSheetClosed()
        }
    }
    LaunchedEffect(payModel) {
        payModel.events.collect { event ->
            when (event) {
                is PayEvent.LaunchPrompt -> BiometricGate.authenticate(
                    activity = activity,
                    title = PROMPT_TITLE,
                    subtitle = PROMPT_SUBTITLE,
                    cryptoObject = BiometricPrompt.CryptoObject(event.cipher),
                    onSuccess = { unlocked -> payModel.onPromptSuccess(unlocked?.cipher) },
                    onError = { payModel.onPromptError() },
                    onCancel = payModel::onPromptCancelled,
                    negativeText = PROMPT_NEGATIVE,
                    onNegative = payModel::onUsePin
                )
                is PayEvent.Paid -> {
                    transferModel.reset()
                    currentOnPaid()
                }
            }
        }
    }

    fun leaveFlow(target: String) {
        showCancel = false
        SelectedTransaction.select(null)
        PaymentExit.request(target)
        transferModel.reset()
        onPaid()
    }

    OPayBottomSheet(
        visible = visible,
        onDismiss = {
            when {
                state.processing -> Unit
                state.stage == PayStage.Pin -> payModel.onBackFromPin()
                else -> onDismiss()
            }
        }
    ) {
        if (state.stage == PayStage.Pin) {
            SecureWindow()
            SecureSheetWindow()
            PinContent(
                filled = state.filled,
                message = state.message,
                shakeCount = state.shakeCount,
                toast = state.toast,
                fingerprintAvailable = state.fingerprintAvailable,
                onBack = payModel::onBackFromPin,
                onUseFingerprint = payModel::onUseFingerprint,
                onDigit = payModel::onDigit,
                onBackspace = payModel::onBackspace,
                onDone = payModel::onBackFromPin
            )
        } else {
            PayContent(
                draft = draft,
                balanceKobo = balanceKobo,
                methodExpanded = methodExpanded,
                toast = state.toast,
                onToggleMethod = { methodExpanded = !methodExpanded },
                onClose = { showCancel = true },
                onUsePin = payModel::onUsePin,
                onAddMoney = { leaveFlow(Routes.AddMoney) },
                onAddCard = { leaveFlow(Routes.BankCards) },
                onPay = payModel::onPayClicked
            )
        }
    }

    if (showCancel) {
        OPayDialog(
            title = "Reminder",
            message = "Do you want to cancel this payment?",
            primaryText = "Continue to pay",
            onPrimary = { showCancel = false },
            onDismiss = { showCancel = false },
            secondaryText = "Cancel",
            onSecondary = { leaveFlow(Routes.Home) }
        )
    }
    state.notice?.let { notice ->
        val acknowledge = {
            payModel.dismissNotice()
            if (notice.leavesFlow) leaveFlow(Routes.Home) else onDismiss()
        }
        OPayDialog(
            title = notice.text,
            message = null,
            primaryText = "OK",
            onPrimary = acknowledge,
            onDismiss = acknowledge
        )
    }
    if (state.processing) {
        ProcessingOverlay()
    }
}

/** Blocks screenshots of the sheet's own window while the PIN keypad is shown. */
@Composable
private fun SecureSheetWindow() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    DisposableEffect(window) {
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

@Composable
private fun PayContent(
    draft: TransferDraft,
    balanceKobo: Long,
    methodExpanded: Boolean,
    toast: String?,
    onToggleMethod: () -> Unit,
    onClose: () -> Unit,
    onUsePin: () -> Unit,
    onAddMoney: () -> Unit,
    onAddCard: () -> Unit,
    onPay: () -> Unit
) {
    val c = OPay.colors
    val resolved = draft.resolved
    val insufficient = draft.amountKobo > balanceKobo
    val bankName = if (draft.isOpayTransfer) {
        "OPay"
    } else {
        draft.bank?.name ?: resolved?.bank?.name.orEmpty()
    }

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
        Spacer(Modifier.weight(1f))
        Text(
            text = "Use Payment PIN",
            style = OPayType.titleSmall,
            color = c.green,
            modifier = Modifier.clickable(onClick = onUsePin)
        )
    }
    Text(
        text = formatNaira(draft.amountKobo),
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
            .heightIn(max = DetailsMaxHeight)
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OPayShapes.card)
                .background(c.cardAlt)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            SheetRow(label = "Bank", value = bankName)
            SheetRow(label = "Account Number", value = resolved?.accountNumber.orEmpty())
            SheetRow(label = "Name", value = resolved?.name.orEmpty())
            SheetRow(
                label = "Amount",
                value = formatNaira(draft.amountKobo),
                tooltip = payMagnitude(draft.amountKobo)
            )
            SheetRow(label = "Fee", value = formatNaira(0L))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Payment Method",
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
            Text(text = "All >", style = OPayType.bodySmall, color = c.muted)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(OPayShapes.card)
                .background(c.cardAlt)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleMethod),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Available Balance (${formatNaira(balanceKobo)})",
                    style = OPayType.body,
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                OPayIconView(icon = OPayIcon.Check, size = 18.dp, tint = c.green)
            }
            if (methodExpanded) {
                Text(
                    text = "Wallet (${formatNaira(0L)})",
                    style = OPayType.bodySmall,
                    color = c.muted,
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "OWealth (${formatNaira(balanceKobo)})",
                        style = OPayType.bodySmall,
                        color = c.muted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "-${formatNaira(draft.amountKobo)}",
                        style = OPayType.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = c.text
                    )
                }
            }
            if (insufficient) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAddCard)
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OPayIconView(icon = OPayIcon.Plus, size = 18.dp, tint = c.green)
            Text(text = "Add a Bank Card / Account", style = OPayType.body, color = c.text)
        }
    }
    if (toast != null) PayToast(text = toast)
    OPayButton(
        text = "Pay",
        onClick = onPay,
        modifier = Modifier.padding(top = 6.dp),
        enabled = !insufficient
    )
}

@Composable
private fun SheetRow(label: String, value: String, tooltip: String? = null) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(text = label, style = OPayType.bodySmall, color = c.muted)
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.End
        ) {
            if (tooltip != null) {
                Text(text = tooltip, style = OPayType.captionSmall, color = c.green)
            }
            Text(
                text = value,
                style = OPayType.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.text,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun PinContent(
    filled: Int,
    message: String?,
    shakeCount: Int,
    toast: String?,
    fingerprintAvailable: Boolean,
    onBack: () -> Unit,
    onUseFingerprint: () -> Unit,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onDone: () -> Unit
) {
    val c = OPay.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Back"; role = Role.Button }
                .clickable(onClick = onBack),
            contentAlignment = Alignment.CenterStart
        ) {
            OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
        }
        Spacer(Modifier.weight(1f))
        if (fingerprintAvailable) {
            Text(
                text = "Use Fingerprint",
                style = OPayType.titleSmall,
                color = c.green,
                modifier = Modifier.clickable(onClick = onUseFingerprint)
            )
        }
    }
    Text(
        text = "Enter Payment PIN",
        style = OPayType.title,
        color = c.text,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        PinDots(
            length = PAYMENT_PIN_LENGTH,
            filled = filled,
            hasError = message != null,
            shakeCount = shakeCount
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (message != null) {
            Text(
                text = message,
                style = OPayType.caption,
                color = c.danger,
                textAlign = TextAlign.Center
            )
        }
    }
    if (toast != null) PayToast(text = toast)
    SecureKeypad(
        onDigit = onDigit,
        onBackspace = onBackspace,
        onDone = onDone,
        modifier = Modifier.bleed(SheetPadding)
    )
}

@Composable
private fun PayToast(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = OPayType.body,
            color = Color.White,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                .padding(horizontal = 16.dp, vertical = 9.dp)
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

private fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val inset = horizontal.roundToPx()
    val width = constraints.maxWidth + inset * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-inset, 0)
    }
}

private fun payMagnitude(amountKobo: Long): String? {
    val naira = amountKobo / 100
    return when {
        amountKobo <= 0L -> null
        naira < 100L -> "Tens"
        naira < 1_000L -> "Hundreds"
        naira < 1_000_000L -> "Thousands"
        else -> "Millions"
    }
}
