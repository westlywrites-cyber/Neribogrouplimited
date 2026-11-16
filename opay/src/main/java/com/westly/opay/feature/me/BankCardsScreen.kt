package com.westly.opay.feature.me

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.NumericKeypad
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.OPayToastState
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.model.Bank
import com.westly.opay.feature.transfer.BankLogo
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val SKELETON_MILLIS = 500L
private const val CARD_NUMBER_MAX = 19
private const val CARD_NUMBER_MIN = 16
private const val EXPIRY_PART_LENGTH = 2
private const val CVV_MAX = 4
private const val CVV_MIN = 3
private const val CARD_PIN_LENGTH = 4
private const val ACCOUNT_NUMBER_LENGTH = 10
private const val VERIFICATION_TOAST = ""
private const val MASK_CHAR = '\u2022'
private val LabelWidth = 110.dp
private val SheetListHeight = 320.dp

private enum class AddTab(val label: String) {
    Card("Bank Card"),
    Account("Bank Account")
}

private enum class FormField {
    CardNumber, Month, Year, Cvv, Pin, AccountNumber
}

private class FormState {
    var cardNumber by mutableStateOf("")
    var month by mutableStateOf("")
    var year by mutableStateOf("")
    var cvv by mutableStateOf("")
    var pin by mutableStateOf("")
    var bank by mutableStateOf<Bank?>(null)
    var accountNumber by mutableStateOf("")
    var active by mutableStateOf<FormField?>(null)

    val cardComplete: Boolean
        get() = cardNumber.length >= CARD_NUMBER_MIN &&
            month.length == EXPIRY_PART_LENGTH &&
            year.length == EXPIRY_PART_LENGTH &&
            cvv.length >= CVV_MIN &&
            pin.length == CARD_PIN_LENGTH

    val accountComplete: Boolean
        get() = bank != null && accountNumber.length == ACCOUNT_NUMBER_LENGTH

    fun append(digit: Char) {
        when (active) {
            FormField.CardNumber -> if (cardNumber.length < CARD_NUMBER_MAX) cardNumber += digit
            FormField.Month -> if (month.length < EXPIRY_PART_LENGTH) month += digit
            FormField.Year -> if (year.length < EXPIRY_PART_LENGTH) year += digit
            FormField.Cvv -> if (cvv.length < CVV_MAX) cvv += digit
            FormField.Pin -> if (pin.length < CARD_PIN_LENGTH) pin += digit
            FormField.AccountNumber -> if (accountNumber.length < ACCOUNT_NUMBER_LENGTH) accountNumber += digit
            null -> Unit
        }
    }

    fun deleteLast() {
        when (active) {
            FormField.CardNumber -> cardNumber = cardNumber.dropLast(1)
            FormField.Month -> month = month.dropLast(1)
            FormField.Year -> year = year.dropLast(1)
            FormField.Cvv -> cvv = cvv.dropLast(1)
            FormField.Pin -> pin = pin.dropLast(1)
            FormField.AccountNumber -> accountNumber = accountNumber.dropLast(1)
            null -> Unit
        }
    }

    fun clear() {
        cardNumber = ""
        month = ""
        year = ""
        cvv = ""
        pin = ""
        bank = null
        accountNumber = ""
        active = null
    }
}

@Composable
fun BankCardsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val toast = rememberOPayToast()
    var adding by rememberSaveable { mutableStateOf(false) }
    var ready by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!ready) {
            delay(SKELETON_MILLIS)
            ready = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        if (adding) {
            AddFlow(onClose = { adding = false }, toast = toast)
        } else {
            EmptyList(ready = ready, onBack = onBack, onAdd = { adding = true })
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun EmptyList(
    ready: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit
) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        OPayTopBar(
            title = "Bank Cards and Accounts",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            if (ready) {
                Text(
                    text = "No cards or accounts added",
                    style = OPayType.body,
                    color = c.muted,
                    textAlign = TextAlign.Center
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 84.dp)
                    Spacer(Modifier.height(12.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f), height = 16.dp)
                    Spacer(Modifier.height(10.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(0.4f), height = 16.dp)
                }
            }
        }
        if (ready) {
            OPayButton(
                text = "Add a Bank Card/Account",
                onClick = onAdd,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun AddFlow(
    onClose: () -> Unit,
    toast: OPayToastState
) {
    val c = OPay.colors
    val context = LocalContext.current
    val banks = remember {
        (context.applicationContext as OPayApplication).repository.banks
    }
    val form = remember { FormState() }
    var tab by remember { mutableStateOf(AddTab.Card) }
    var pickingBank by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { form.clear() }
    }
    BackHandler(onBack = onClose)

    val complete = if (tab == AddTab.Card) form.cardComplete else form.accountComplete
    val confirm: () -> Unit = {
        form.clear()
        toast.show(VERIFICATION_TOAST)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        OPayTopBar(
            title = "Add a Bank Card/Account",
            onBack = onClose,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(AddTab.Card, AddTab.Account).forEach { item ->
                val selected = item == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            if (!selected) {
                                form.clear()
                                tab = item
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.label,
                        style = OPayType.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) c.green else c.muted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(3.dp)
                            .background(if (selected) c.green else Color.Transparent)
                    )
                }
            }
        }
        OPayRowDivider()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (tab == AddTab.Card) {
                CardFields(form = form)
                Text(
                    text = "Add \u20A61.00 to your OPay account for bank card verification",
                    style = OPayType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                AccountFields(
                    form = form,
                    onSelectBank = {
                        form.active = null
                        pickingBank = true
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
            OPayButton(text = "Confirm", onClick = confirm, enabled = complete)
            Spacer(Modifier.height(16.dp))
        }
        if (form.active != null) {
            Box(
                modifier = Modifier
                    .background(c.bg)
                    .navigationBarsPadding()
            ) {
                NumericKeypad(
                    onDigit = { form.append(it) },
                    onDelete = { form.deleteLast() },
                    onDone = { form.active = null }
                )
            }
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    BankPickerSheet(
        visible = pickingBank,
        banks = banks,
        onDismiss = { pickingBank = false },
        onPick = { bank ->
            form.bank = bank
            pickingBank = false
        }
    )
}

@Composable
private fun CardFields(form: FormState) {
    val c = OPay.colors
    PadField(
        display = form.cardNumber.chunked(4).joinToString(" "),
        placeholder = "Enter 16-19 digit card number",
        active = form.active == FormField.CardNumber,
        onClick = { form.active = FormField.CardNumber },
        onClear = { form.cardNumber = "" }
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Expiry Date",
            style = OPayType.body,
            color = c.text,
            modifier = Modifier.width(LabelWidth)
        )
        PadField(
            display = form.month,
            placeholder = "MM",
            active = form.active == FormField.Month,
            onClick = { form.active = FormField.Month },
            onClear = { form.month = "" },
            showClear = false,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = "/",
            style = OPayType.body,
            color = c.muted,
            modifier = Modifier.padding(horizontal = 10.dp)
        )
        PadField(
            display = form.year,
            placeholder = "YY",
            active = form.active == FormField.Year,
            onClick = { form.active = FormField.Year },
            onClear = { form.year = "" },
            showClear = false,
            modifier = Modifier.width(56.dp)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "CVV \u24D8",
            style = OPayType.body,
            color = c.text,
            modifier = Modifier.width(LabelWidth)
        )
        PadField(
            display = MASK_CHAR.toString().repeat(form.cvv.length),
            placeholder = "Enter CVV",
            active = form.active == FormField.Cvv,
            onClick = { form.active = FormField.Cvv },
            onClear = { form.cvv = "" },
            modifier = Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "PIN",
            style = OPayType.body,
            color = c.text,
            modifier = Modifier.width(LabelWidth)
        )
        PadField(
            display = MASK_CHAR.toString().repeat(form.pin.length),
            placeholder = "Enter Card PIN",
            active = form.active == FormField.Pin,
            onClick = { form.active = FormField.Pin },
            onClear = { form.pin = "" },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun AccountFields(
    form: FormState,
    onSelectBank: () -> Unit
) {
    val c = OPay.colors
    OPayRow(title = "Select Bank", onClick = onSelectBank, value = form.bank?.name)
    OPayRowDivider()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Account Number",
            style = OPayType.body,
            color = c.text,
            modifier = Modifier.width(LabelWidth + 20.dp)
        )
        PadField(
            display = form.accountNumber,
            placeholder = "10-digit number",
            active = form.active == FormField.AccountNumber,
            onClick = { form.active = FormField.AccountNumber },
            onClear = { form.accountNumber = "" },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PadField(
    display: String,
    placeholder: String,
    active: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    showClear: Boolean = true
) {
    val c = OPay.colors
    val fieldStyle = OPayType.body.copy(fontSize = 16.sp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (display.isEmpty()) placeholder else display,
                style = fieldStyle,
                color = if (display.isEmpty()) c.muted else c.text,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (showClear && display.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Clear"; role = Role.Button }
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Close, size = 16.dp, tint = c.muted)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (active) 2.dp else 1.dp)
                .background(if (active) c.green else c.divider)
        )
    }
}

@Composable
private fun BankPickerSheet(
    visible: Boolean,
    banks: List<Bank>,
    onDismiss: () -> Unit,
    onPick: (Bank) -> Unit
) {
    val c = OPay.colors
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        var query by remember { mutableStateOf("") }
        val results = remember(banks, query) {
            banks.filter { it.name.contains(query.trim(), ignoreCase = true) }
        }
        Text(
            text = "Select Bank",
            style = OPayType.title,
            color = c.text,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OPayUnderlineField(
            value = query,
            onValueChange = { query = it },
            placeholder = "Search bank"
        )
        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SheetListHeight),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No bank found", style = OPayType.body, color = c.muted)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SheetListHeight)
            ) {
                items(results, key = { it.code }) { bank ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(bank) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BankLogo(name = bank.name, size = 28.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(text = bank.name, style = OPayType.body, color = c.text)
                    }
                }
            }
        }
    }
}
