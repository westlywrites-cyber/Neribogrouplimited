package com.westly.opay.feature.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.core.util.parseNairaToKobo
import com.westly.opay.data.model.TxType
import com.westly.opay.navigation.Routes
import java.util.Locale

private const val GRID_COLUMNS = 3
private const val PHONE_FIELD_MAX = 13
private const val MIN_OTHER_KOBO = 5_000L
private const val MAX_OTHER_KOBO = 5_000_000L
private val AirtimeAmountsNaira = listOf(50L, 100L, 200L, 500L, 1_000L, 2_000L)

@Composable
fun AirtimeScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val profile by repository.profile.collectAsStateWithLifecycle()
    val sessionNumbers by SessionNumbers.used.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()

    var typed by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var chosenNetwork by rememberSaveable { mutableStateOf<String?>(null) }
    val anchor = rememberPickerAnchor()
    var selectedNaira by rememberSaveable { mutableStateOf(0L) }
    var other by rememberSaveable { mutableStateOf("") }
    var request by remember { mutableStateOf<PurchaseRequest?>(null) }

    val digits = typed ?: localPhoneDigits(profile.phone)
    val shown = formatPhoneDisplay(digits)
    val network = chosenNetwork ?: detectNetwork(digits)
    val otherKobo = if (other.isBlank()) null else parseNairaToKobo(other)
    val otherInvalid = other.isNotBlank() && (otherKobo == null || otherKobo !in MIN_OTHER_KOBO..MAX_OTHER_KOBO)
    val amountKobo: Long? = when {
        selectedNaira > 0L -> selectedNaira * 100L
        otherKobo != null && !otherInvalid -> otherKobo
        else -> null
    }
    val canPay = isCompleteNumber(digits) && amountKobo != null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            OPayTopBar(
                title = "Airtime",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "History",
                onAction = { onNavigate(Routes.Transactions) }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                OPayCard {
                    OPayUnderlineField(
                        value = shown,
                        onValueChange = { raw ->
                            var next = raw.filter { it in '0'..'9' }.take(11)
                            if (raw.length < shown.length && next == digits) {
                                next = next.dropLast(1)
                            }
                            typed = next
                        },
                        placeholder = "Phone number",
                        modifier = anchor.modifier(),
                        keyboardType = KeyboardType.Phone,
                        maxLength = PHONE_FIELD_MAX,
                        leading = {
                            NetworkSelector(
                                network = network,
                                open = pickerOpen,
                                onClick = { pickerOpen = !pickerOpen }
                            )
                        },
                        trailing = {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c.green)
                                    .clickable { toast.show(NOT_AVAILABLE_MESSAGE) },
                                contentAlignment = Alignment.Center
                            ) {
                                OPayIconView(icon = OPayIcon.User, size = 16.dp, tint = c.onGreen)
                            }
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (network != null) {
                            OPayChip(text = network)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Beneficiaries \u25BE",
                            style = OPayType.bodySmall,
                            color = c.green,
                            modifier = Modifier.clickable { menuOpen = !menuOpen }
                        )
                    }
                    if (menuOpen) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            if (sessionNumbers.isEmpty()) {
                                Text(
                                    text = "No beneficiaries yet",
                                    style = OPayType.caption,
                                    color = c.muted,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                sessionNumbers.forEachIndexed { index, number ->
                                    if (index > 0) OPayRowDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                typed = number
                                                menuOpen = false
                                            }
                                            .padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        NetworkLogo(name = detectNetwork(number) ?: "MTN", size = 24.dp)
                                        Text(
                                            text = formatPhoneDisplay(number),
                                            style = OPayType.body,
                                            color = c.text
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OPayCard {
                    AmountGrid(
                        selectedNaira = selectedNaira,
                        onSelect = {
                            selectedNaira = it
                            other = ""
                        }
                    )
                }
                OPayCard {
                    OPayUnderlineField(
                        value = other,
                        onValueChange = {
                            other = it
                            selectedNaira = 0L
                        },
                        placeholder = "Other amount (\u20A650 - \u20A650,000)",
                        keyboardType = KeyboardType.Decimal,
                        leading = {
                            Text(text = "\u20A6", style = OPayType.title, color = c.text)
                        }
                    )
                    if (otherInvalid) {
                        Text(
                            text = "Amount must be between \u20A650 and \u20A650,000",
                            style = OPayType.caption,
                            color = c.danger,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            OPayButton(
                text = "Pay",
                onClick = {
                    val amount = amountKobo
                    if (amount != null) {
                        val carrier = network ?: "MTN"
                        request = PurchaseRequest(
                            type = TxType.Airtime,
                            title = "Airtime",
                            subtitle = "$carrier $shown",
                            counterparty = digits,
                            amountKobo = amount,
                            storedTitle = "$carrier Airtime"
                        )
                    }
                },
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .navigationBarsPadding(),
                enabled = canPay
            )
        }
        NetworkPickerOverlay(
            visible = pickerOpen,
            selected = network,
            anchor = anchor,
            onSelect = {
                chosenNetwork = it
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false }
        )
        OPayToastHost(state = toast, bottomPadding = 90.dp)
        PurchaseFlow(
            request = request,
            onFinished = { request = null },
            onViewDetails = {
                request = null
                onNavigate(Routes.TxDetails)
            },
            onAddMoney = {
                request = null
                onNavigate(Routes.AddMoney)
            },
            onSuccess = { SessionNumbers.add(digits) }
        )
    }
}

@Composable
private fun AmountGrid(
    selectedNaira: Long,
    onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AirtimeAmountsNaira.chunked(GRID_COLUMNS).forEach { rowAmounts ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowAmounts.forEach { naira ->
                    AmountTile(
                        naira = naira,
                        selected = naira == selectedNaira,
                        onClick = { onSelect(naira) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountTile(
    naira: Long,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Column(
        modifier = modifier
            .clip(OPayShapes.tile)
            .background(c.cardAlt)
            .then(
                if (selected) Modifier.border(1.5.dp, c.green, OPayShapes.tile) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "\u20A6" + String.format(Locale.US, "%,d", naira),
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Text(
            text = "Up to 3% cashback",
            fontSize = 10.sp,
            color = c.green,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
