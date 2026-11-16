package com.westly.opay.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.westly.opay.OPayApplication
import com.westly.opay.backend.BackendCodes
import com.westly.opay.backend.BackendResult
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.OverlayScrim
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.AppRepository
import com.westly.opay.data.SETTING_SAVE_FAILED_TEXT
import com.westly.opay.data.SettingsKeys
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val LOADER_MILLIS = 700L
private const val EXIT_DELAY_MILLIS = 900L
private const val ACTIVATED_TOAST = "SMS alerts activated"
private const val AGREEMENT_TAG = "agreement"
private const val AGREEMENT_LINK = "OPay SMS Alert Service Agreement"

private val InfoCardColor = Color(0xFF17402F)
private val InfoCardText = Color(0xFFD7F5EA)

private data class SmsOption(val title: String, val description: String)

private val smsOptions = listOf(
    SmsOption("Credit Alerts", "Money sent to your OPay account."),
    SmsOption(
        "Card Transaction Alerts",
        "Payments & Cash Withdrawals made with your OPay Debit Card."
    ),
    SmsOption(
        "Transfer & Withdrawal Alerts",
        "Transfers & Cash Withdrawals made with the OPay App or USSD."
    ),
    SmsOption(
        "Other Alerts",
        "Transactions other than those listed above, such as Airtime, Data, etc."
    )
)

/** The options that are on in the saved settings, as a bit mask in the order of [smsOptions]. */
private fun savedMask(repository: AppRepository): Int =
    SettingsKeys.SMS_ALL.foldIndexed(0) { index, mask, key ->
        if (repository.flag(key)) mask or (1 shl index) else mask
    }

/** One saved value for each option: on for the bits set in [mask], off for the others. */
private fun smsFlags(mask: Int): Map<String, Boolean> =
    SettingsKeys.SMS_ALL.mapIndexed { index, key -> key to (mask and (1 shl index) != 0) }.toMap()

@Composable
fun SmsAlertsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val toast = rememberOPayToast()
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val scope = rememberCoroutineScope()
    var loading by rememberSaveable { mutableStateOf(true) }
    var selectedMask by rememberSaveable { mutableStateOf(savedMask(repository)) }
    var agreed by rememberSaveable { mutableStateOf(selectedMask != 0) }
    var saving by remember { mutableStateOf(false) }
    var activated by rememberSaveable { mutableStateOf(false) }
    var leaveOpen by rememberSaveable { mutableStateOf(false) }
    var agreementOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (loading) {
            delay(LOADER_MILLIS)
            loading = false
        }
    }
    LaunchedEffect(activated) {
        if (activated) {
            delay(EXIT_DELAY_MILLIS)
            onBack()
        }
    }
    BackHandler(enabled = !activated) { leaveOpen = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            SmsTopBar(
                onBack = { if (!activated) leaveOpen = true },
                onHelp = { toast.show(NOT_AVAILABLE_TOAST) },
                onDocument = { toast.show(NOT_AVAILABLE_TOAST) }
            )
            if (loading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    OPayLoader()
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "Activate SMS alerts to get real-time transaction notifications " +
                            "(₦6 per alert), protect your funds. Cancel anytime.",
                        style = OPayType.body,
                        color = c.text,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                    )
                    Text(
                        text = "Please select subscription option(s):",
                        style = OPayType.body,
                        color = c.text,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                    )
                    smsOptions.forEachIndexed { index, option ->
                        SmsOptionCard(
                            option = option,
                            selected = selectedMask and (1 shl index) != 0,
                            showAddUser = index == 0,
                            onClick = { selectedMask = selectedMask xor (1 shl index) },
                            onAddUser = { toast.show(NOT_AVAILABLE_TOAST) }
                        )
                    }
                    Text(
                        text = "SMS charges for each last month will be automatically debited " +
                            "at 10am on the 3rd of each new month.",
                        style = OPayType.caption,
                        color = InfoCardText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(InfoCardColor, OPayShapes.card)
                            .padding(12.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(c.bg)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    OPayButton(
                        text = "Agree & Activate",
                        onClick = {
                            saving = true
                            scope.launch {
                                when (val result = repository.saveSettings(smsFlags(selectedMask))) {
                                    is BackendResult.Success -> {
                                        activated = true
                                        toast.show(ACTIVATED_TOAST)
                                    }
                                    is BackendResult.Failure -> {
                                        saving = false
                                        toast.show(
                                            if (result.error.code == BackendCodes.NETWORK) result.error.message
                                            else SETTING_SAVE_FAILED_TEXT
                                        )
                                    }
                                }
                            }
                        },
                        enabled = selectedMask != 0 && agreed && !activated && !saving
                    )
                    Spacer(Modifier.height(8.dp))
                    AgreementLine(
                        agreed = agreed,
                        onToggle = { agreed = !agreed },
                        onOpenAgreement = { agreementOpen = true }
                    )
                }
            }
        }
        OPayToastHost(state = toast, bottomPadding = 140.dp)
    }

    if (agreementOpen) {
        OPayDialog(
            title = AGREEMENT_LINK,
            message = "This is a demo service agreement for the SMS alert feature of this " +
                "demo application. No real SMS is sent and no real charge is made.",
            primaryText = "OK",
            onPrimary = { agreementOpen = false },
            onDismiss = { agreementOpen = false }
        )
    }
    if (leaveOpen) {
        DisableAlertsDialog(
            onDisable = {
                leaveOpen = false
                repository.setSettings(smsFlags(0))
                onBack()
            },
            onStay = { leaveOpen = false }
        )
    }
}

@Composable
private fun SmsTopBar(
    onBack: () -> Unit,
    onHelp: () -> Unit,
    onDocument: () -> Unit
) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Back"; role = Role.Button }
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
        }
        Text(
            text = "SMS Alert Settings",
            style = OPayType.title,
            color = c.text,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Help"; role = Role.Button }
                .clickable(onClick = onHelp),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Help, size = 20.dp, tint = c.text)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Service agreement"; role = Role.Button }
                .clickable(onClick = onDocument),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Doc, size = 20.dp, tint = c.text)
        }
    }
}

@Composable
private fun SmsOptionCard(
    option: SmsOption,
    selected: Boolean,
    showAddUser: Boolean,
    onClick: () -> Unit,
    onAddUser: () -> Unit
) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(c.card)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = option.title,
                    style = OPayType.body.copy(fontWeight = FontWeight.Bold),
                    color = c.text,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (showAddUser) {
                    Text(
                        text = "+ Add User",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = c.green,
                        modifier = Modifier
                            .clip(OPayShapes.chip)
                            .background(c.greenSoft)
                            .clickable(onClick = onAddUser)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = option.description,
                style = OPayType.caption,
                color = c.muted
            )
        }
        SelectRing(selected = selected)
    }
}

@Composable
internal fun SelectRing(selected: Boolean, modifier: Modifier = Modifier) {
    val c = OPay.colors
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .then(
                if (selected) Modifier.background(c.green)
                else Modifier.border(1.dp, c.muted, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            OPayIconView(icon = OPayIcon.Check, size = 14.dp, tint = c.onGreen)
        }
    }
}

@Composable
private fun AgreementLine(
    agreed: Boolean,
    onToggle: () -> Unit,
    onOpenAgreement: () -> Unit
) {
    val c = OPay.colors
    val text = buildAnnotatedString {
        append("By tapping “Agree & Enable”, you agree to the ")
        pushStringAnnotation(tag = AGREEMENT_TAG, annotation = AGREEMENT_TAG)
        withStyle(
            SpanStyle(color = c.green, textDecoration = TextDecoration.Underline)
        ) {
            append(AGREEMENT_LINK)
        }
        pop()
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp)
                .clip(OPayShapes.chip)
                .then(
                    if (agreed) Modifier.background(c.green)
                    else Modifier.border(1.dp, c.muted, OPayShapes.chip)
                )
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            if (agreed) {
                OPayIconView(icon = OPayIcon.Check, size = 13.dp, tint = c.onGreen)
            }
        }
        ClickableText(
            text = text,
            style = OPayType.caption.copy(color = c.muted),
            modifier = Modifier.weight(1f),
            onClick = { offset ->
                if (text.getStringAnnotations(AGREEMENT_TAG, offset, offset).isNotEmpty()) {
                    onOpenAgreement()
                } else {
                    onToggle()
                }
            }
        )
    }
}

@Composable
private fun DisableAlertsDialog(
    onDisable: () -> Unit,
    onStay: () -> Unit
) {
    val c = OPay.colors
    Dialog(
        onDismissRequest = onStay,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        RemoveWindowDim()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OverlayScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStay
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clip(OPayShapes.card)
                    .background(c.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "Disable Transaction Alerts?",
                    style = OPayType.title,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Inactive SMS alerts mean you won't get real-time transaction " +
                        "notifications .",
                    style = OPayType.body,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OPayButton(
                        text = "Disable",
                        onClick = onDisable,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.Dark
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        PinkTag(text = "70% of users chose this")
                        Spacer(Modifier.height(4.dp))
                        OPayButton(
                            text = "Active Now",
                            onClick = onStay,
                            style = ButtonStyle.Primary
                        )
                    }
                }
            }
        }
    }
}
