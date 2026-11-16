package com.westly.opay.feature.bills

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.model.TxType
import com.westly.opay.navigation.Routes

private const val MIN_CUSTOMER_ID_LENGTH = 6
private const val MAX_CUSTOMER_ID_LENGTH = 20
private const val MIN_AMOUNT_NAIRA = 100L
private const val MAX_AMOUNT_NAIRA = 100_000L
private const val MAX_AMOUNT_DIGITS = 6

private data class BettingProvider(
    val name: String,
    val background: Color,
    val content: Color
)

private val BettingProviders = listOf(
    BettingProvider("Bet9ja", Color(0xFF1B8A3C), Color.White),
    BettingProvider("SportyBet", Color(0xFFE60012), Color.White),
    BettingProvider("1xBet", Color(0xFF1E5AA8), Color.White),
    BettingProvider("BetKing", Color(0xFFF5A300), Color(0xFF1B1B1B)),
    BettingProvider("NairaBet", Color(0xFF0B5D3B), Color.White),
    BettingProvider("MerryBet", Color(0xFFD4145A), Color.White),
    BettingProvider("BangBet", Color(0xFF2D2D8F), Color.White),
    BettingProvider("Betway", Color(0xFF00A826), Color.White)
)

@Composable
internal fun ProviderLogo(
    initial: String,
    background: Color,
    content: Color,
    size: Dp = 32.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            fontSize = (size.value * 0.45f).sp,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

@Composable
fun BettingScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val toast = rememberOPayToast()

    var providerName by rememberSaveable { mutableStateOf<String?>(null) }
    var customerId by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var request by remember { mutableStateOf<PurchaseRequest?>(null) }

    val provider = BettingProviders.firstOrNull { it.name == providerName }
    val amountNaira = amountText.toLongOrNull()
    val amountValid = amountNaira != null &&
        amountNaira >= MIN_AMOUNT_NAIRA &&
        amountNaira <= MAX_AMOUNT_NAIRA
    val idValid = customerId.length >= MIN_CUSTOMER_ID_LENGTH
    val canPay = provider != null && idValid && amountValid

    fun openPay() {
        val chosen = provider ?: return
        val naira = amountNaira ?: return
        request = PurchaseRequest(
            type = TxType.Bill,
            title = "Betting \u2013 " + chosen.name,
            subtitle = "Customer ID $customerId",
            counterparty = chosen.name,
            amountKobo = naira * 100L
        )
    }

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
                title = "Betting",
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { sheetOpen = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (provider != null) {
                            ProviderLogo(
                                initial = provider.name.take(1).uppercase(),
                                background = provider.background,
                                content = provider.content,
                                size = 30.dp
                            )
                        }
                        Text(
                            text = provider?.name ?: "Select provider",
                            style = OPayType.title,
                            color = if (provider != null) c.text else c.muted,
                            modifier = Modifier.weight(1f)
                        )
                        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
                    }
                    OPayRowDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        text = "Customer ID",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    OPayUnderlineField(
                        value = customerId,
                        onValueChange = { input ->
                            customerId = input.filter { it.isLetterOrDigit() }
                        },
                        placeholder = "Enter",
                        maxLength = MAX_CUSTOMER_ID_LENGTH
                    )
                    Text(
                        text = "Select Amount",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                        modifier = Modifier.padding(top = 18.dp)
                    )
                    OPayUnderlineField(
                        value = amountText,
                        onValueChange = { input ->
                            amountText = input.filter { it.isDigit() }
                        },
                        placeholder = "100 - 100,000",
                        keyboardType = KeyboardType.Number,
                        maxLength = MAX_AMOUNT_DIGITS,
                        leading = {
                            Text(
                                text = "\u20A6",
                                style = OPayType.title,
                                fontWeight = FontWeight.Bold,
                                color = c.text
                            )
                        }
                    )
                    if (amountText.isNotEmpty() && !amountValid) {
                        Text(
                            text = "Amount must be between \u20A6100 and \u20A6100,000",
                            style = OPayType.caption,
                            color = c.danger,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    OPayButton(
                        text = "Pay",
                        onClick = { openPay() },
                        modifier = Modifier.padding(top = 20.dp),
                        style = ButtonStyle.Dark,
                        enabled = canPay
                    )
                }
                OPayCard {
                    OPayRow(
                        title = "More Events",
                        onClick = { toast.show(NOT_AVAILABLE_MESSAGE) }
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(OPayShapes.tile)
                            .background(c.greenSoft)
                            .clickable { toast.show(NOT_AVAILABLE_MESSAGE) }
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "Super Voucher Package",
                            style = OPayType.body,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Claim 15 Discounts with \u20A699 on any Bill",
                            style = OPayType.bodySmall,
                            color = Color.White,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
        OPayBottomSheet(
            visible = sheetOpen,
            onDismiss = { sheetOpen = false }
        ) {
            Text(
                text = "Select provider",
                style = OPayType.title,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            )
            BettingProviders.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            providerName = item.name
                            sheetOpen = false
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ProviderLogo(
                        initial = item.name.take(1).uppercase(),
                        background = item.background,
                        content = item.content,
                        size = 34.dp
                    )
                    Text(
                        text = item.name,
                        style = OPayType.title,
                        color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                    if (item.name == providerName) {
                        OPayIconView(icon = OPayIcon.Check, size = 18.dp, tint = c.green)
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
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
            }
        )
    }
}
