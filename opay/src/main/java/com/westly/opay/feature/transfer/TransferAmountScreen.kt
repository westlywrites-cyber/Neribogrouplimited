package com.westly.opay.feature.transfer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayRingAvatar
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.overlay.OverlayScrim
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import com.westly.opay.core.util.formatNaira
import com.westly.opay.core.util.parseNairaToKobo
import com.westly.opay.data.model.ResolvedAccount
import com.westly.opay.data.model.TransferDraft

private const val BANK_MIN_KOBO = 10_000L
private const val OPAY_MIN_KOBO = 1_000L
private const val MAX_KOBO = 500_000_000L
private const val MAX_INTEGER_DIGITS = 9
private const val MAX_REMARK_LENGTH = 100
private val QuickAmounts = listOf(500L, 1_000L, 2_000L, 5_000L, 10_000L)

@Composable
fun TransferAmountScreen(
    onBack: () -> Unit,
    onRecords: () -> Unit,
    onPaid: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = viewModel<TransferViewModel>(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    var leaving by remember { mutableStateOf(false) }
    val resolved = draft.resolved

    LaunchedEffect(resolved, leaving) {
        if (resolved == null && !leaving) onBack()
    }

    if (resolved == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(OPay.colors.bg)
        )
    } else {
        AmountContent(
            draft = draft,
            resolved = resolved,
            viewModel = viewModel,
            onBack = onBack,
            onRecords = onRecords,
            onPaid = {
                leaving = true
                onPaid()
            },
            modifier = modifier
        )
    }
}

@Composable
private fun AmountContent(
    draft: TransferDraft,
    resolved: ResolvedAccount,
    viewModel: TransferViewModel,
    onBack: () -> Unit,
    onRecords: () -> Unit,
    onPaid: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val focus = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val isOpay = draft.isOpayTransfer
    val minKobo = if (isOpay) OPAY_MIN_KOBO else BANK_MIN_KOBO
    val bankName = draft.bank?.name ?: resolved.bank.name

    var amountText by rememberSaveable { mutableStateOf(koboToInput(draft.amountKobo)) }
    var remark by rememberSaveable { mutableStateOf(draft.remark) }
    var showSheet by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showPay by remember { mutableStateOf(false) }
    var pendingKobo by remember { mutableLongStateOf(0L) }

    val kobo = parseNairaToKobo(amountText.trimEnd('.'))
    val valid = kobo != null && kobo in minKobo..MAX_KOBO
    val showError = amountText.isNotEmpty() && !valid
    val magnitude = kobo?.takeIf { it > 0 }?.let { magnitudeLabel(it / 100) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .imePadding()
    ) {
        OPayTopBar(
            title = if (isOpay) "Transfer to OPay Account" else "Transfer to Bank Account",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp),
            actionText = if (isOpay) "Records" else "History",
            onAction = onRecords
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            SectionCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isOpay) {
                        OPayRingAvatar(size = 36.dp)
                    } else {
                        BankLogo(name = bankName, size = 36.dp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolved.name.uppercase(),
                            style = OPayType.body,
                            fontWeight = FontWeight.Bold,
                            color = c.text
                        )
                        Text(
                            text = if (isOpay) {
                                formatOpayNumber(resolved.accountNumber)
                            } else {
                                "${bankName.uppercase()} ${resolved.accountNumber}"
                            },
                            style = OPayType.caption,
                            color = c.muted
                        )
                    }
                }
            }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Amount",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                    if (isOpay) {
                        Text(
                            text = "No Transaction Fees",
                            style = OPayType.captionSmall,
                            color = c.green,
                            modifier = Modifier
                                .clip(OPayShapes.chip)
                                .background(c.greenSoft)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Box(modifier = Modifier.height(20.dp), contentAlignment = Alignment.BottomStart) {
                    if (magnitude != null) {
                        Text(text = magnitude, style = OPayType.caption, color = c.muted)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "\u20A6", fontSize = 22.sp, color = c.text)
                    Spacer(Modifier.width(6.dp))
                    BasicTextField(
                        value = amountText,
                        onValueChange = { amountText = formatAmountInput(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 8.dp)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 22.sp, color = c.text),
                        cursorBrush = SolidColor(c.green),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        decorationBox = { innerTextField ->
                            Box {
                                if (amountText.isEmpty()) {
                                    Text(
                                        text = if (isOpay) "10.00 - 5,000,000.00" else "100.00 - 5,000,000.00",
                                        fontSize = 16.sp,
                                        color = c.muted
                                    )
                                }
                                innerTextField()
                            }
                        }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(c.divider)
                )
                if (showError) {
                    Text(
                        text = "Amount must be between ${formatNaira(minKobo)} and ${formatNaira(MAX_KOBO)}",
                        style = OPayType.caption,
                        color = c.danger,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    QuickAmounts.forEach { naira ->
                        OPayChip(
                            text = "\u20A6" + formatGrouped(naira.toString()),
                            onClick = { amountText = formatGrouped(naira.toString()) }
                        )
                    }
                }
            }
            SectionCard {
                Text(
                    text = "Remark",
                    style = OPayType.body,
                    fontWeight = FontWeight.Bold,
                    color = c.text
                )
                OPayUnderlineField(
                    value = remark,
                    onValueChange = { remark = it },
                    placeholder = "What\u2019s this for?(Optional)",
                    maxLength = MAX_REMARK_LENGTH
                )
            }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Category",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "only visible to you (optional)",
                        style = OPayType.captionSmall,
                        color = c.muted,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "More >",
                        style = OPayType.body,
                        color = c.green,
                        modifier = Modifier.clickable {
                            focus.clearFocus()
                            showSheet = true
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    quickCategories.forEach { option ->
                        val selected = draft.category == option.label
                        CategoryTile(
                            option = option,
                            selected = selected,
                            onClick = { viewModel.setCategory(if (selected) null else option.label) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        OPayButton(
            text = "Confirm",
            onClick = {
                if (kobo != null) {
                    focus.clearFocus()
                    pendingKobo = kobo
                    viewModel.setAmount(kobo)
                    viewModel.setRemark(remark.trim())
                    showReminder = true
                }
            },
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            enabled = valid
        )
    }

    CategorySheet(
        visible = showSheet,
        selected = draft.category,
        onDismiss = { showSheet = false },
        onConfirm = viewModel::setCategory
    )
    if (showReminder) {
        ReminderDialog(
            name = resolved.name,
            accountNumber = resolved.accountNumber,
            bank = if (isOpay) "OPay" else bankName,
            amountKobo = pendingKobo,
            onRecheck = { showReminder = false },
            onContinue = {
                showReminder = false
                showPay = true
            }
        )
    }
    PaySheetSlot(
        visible = showPay,
        onDismiss = { showPay = false },
        onPaid = {
            showPay = false
            onPaid()
        }
    )
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(OPay.colors.card)
            .padding(12.dp),
        content = content
    )
}

@Composable
private fun ReminderDialog(
    name: String,
    accountNumber: String,
    bank: String,
    amountKobo: Long,
    onRecheck: () -> Unit,
    onContinue: () -> Unit
) {
    val c = OPay.colors
    Dialog(
        onDismissRequest = onRecheck,
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
                    onClick = onRecheck
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = "Reminder",
                    style = OPayType.title,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Double check the transfer details before you proceed. " +
                        "Please note that successful transfers cannot be reversed.",
                    style = OPayType.body,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(OPayShapes.tile)
                        .background(c.cardAlt)
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Transaction Details",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    DetailRow(label = "Name", value = name)
                    DetailRow(label = "Account No.", value = accountNumber)
                    DetailRow(label = "Bank", value = bank)
                    DetailRow(label = "Amount", value = formatNaira(amountKobo))
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OPayButton(
                        text = "Recheck",
                        onClick = onRecheck,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.Dark
                    )
                    OPayButton(
                        text = "Continue",
                        onClick = onContinue,
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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

private fun magnitudeLabel(naira: Long): String = when {
    naira < 100L -> "Tens"
    naira < 1_000L -> "Hundreds"
    naira < 1_000_000L -> "Thousands"
    else -> "Millions"
}

private fun formatGrouped(digits: String): String =
    digits.reversed().chunked(3).joinToString(",").reversed()

private fun formatAmountInput(raw: String): String {
    val cleaned = raw.filter { it.isDigit() || it == '.' }
    val dot = cleaned.indexOf('.')
    val integerDigits = (if (dot >= 0) cleaned.substring(0, dot) else cleaned)
        .filter { it.isDigit() }
        .take(MAX_INTEGER_DIGITS)
    val integerPart = integerDigits.trimStart('0').ifEmpty {
        if (integerDigits.isEmpty() && dot < 0) "" else "0"
    }
    val fraction = if (dot >= 0) cleaned.substring(dot + 1).filter { it.isDigit() }.take(2) else ""
    return formatGrouped(integerPart) + if (dot >= 0) ".$fraction" else ""
}

private fun koboToInput(kobo: Long): String {
    if (kobo <= 0L) return ""
    val whole = formatGrouped((kobo / 100).toString())
    val fraction = kobo % 100
    return if (fraction == 0L) whole else whole + "." + fraction.toString().padStart(2, '0')
}

private fun formatOpayNumber(number: String): String {
    val digits = number.filter { it.isDigit() }
    return if (digits.length >= 10) {
        digits.take(3) + " " + digits.drop(3).take(3) + " " + digits.drop(6)
    } else {
        digits
    }
}
