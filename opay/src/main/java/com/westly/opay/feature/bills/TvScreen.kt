package com.westly.opay.feature.bills

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.R
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

private const val BANNER_MILLIS = 500L
private const val MIN_SMARTCARD_DIGITS = 10
private const val MAX_SMARTCARD_DIGITS = 12
private const val MAX_SESSION_CARDS = 2

private val CustomerNames = listOf(
    "Adaeze Obi",
    "Musa Danjuma",
    "Ifeoma Nwosu",
    "Segun Bakare",
    "Halima Yusuf",
    "Chidi Okeke",
    "Bola Adeyemi",
    "Emeka Uche",
    "Ngozi Eze",
    "Tunde Lawal"
)

private data class TvPlan(val name: String, val priceNaira: Int)

private data class TvProvider(
    val name: String,
    val background: Color,
    val content: Color,
    val plans: List<TvPlan>
)

private val TvProviders = listOf(
    TvProvider(
        name = "DStv",
        background = Color(0xFF0072BC),
        content = Color.White,
        plans = listOf(
            TvPlan("Padi", 4_400),
            TvPlan("Yanga", 6_000),
            TvPlan("Confam", 11_000),
            TvPlan("Compact", 19_000)
        )
    ),
    TvProvider(
        name = "GOtv",
        background = Color(0xFF8DC63F),
        content = Color(0xFF1B1B1B),
        plans = listOf(
            TvPlan("Smallie", 1_900),
            TvPlan("Jinja", 3_900),
            TvPlan("Jolli", 5_800),
            TvPlan("Max", 8_500)
        )
    ),
    TvProvider(
        name = "StarTimes",
        background = Color(0xFFF7941D),
        content = Color(0xFF1B1B1B),
        plans = listOf(
            TvPlan("Nova", 1_900),
            TvPlan("Basic", 3_700),
            TvPlan("Smart", 4_700)
        )
    ),
    TvProvider(
        name = "Showmax",
        background = Color(0xFFE6007E),
        content = Color.White,
        plans = listOf(
            TvPlan("Mobile", 2_900),
            TvPlan("Standard", 5_500)
        )
    )
)

/**
 * Keeps the last smartcard numbers used for a TV payment while the app process is alive.
 */
private object SessionCards {
    private val state = MutableStateFlow<List<String>>(emptyList())

    val used: StateFlow<List<String>> = state.asStateFlow()

    fun add(number: String) {
        state.value = (listOf(number) + state.value.filterNot { it == number }).take(MAX_SESSION_CARDS)
    }
}

private fun priceText(naira: Int): String = "\u20A6" + String.format(Locale.US, "%,d", naira)

@Composable
fun TvScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val toast = rememberOPayToast()
    val sessionCards by SessionCards.used.collectAsStateWithLifecycle()

    var bannerVisible by remember { mutableStateOf(true) }
    var providerName by rememberSaveable { mutableStateOf<String?>(null) }
    var smartcard by rememberSaveable { mutableStateOf("") }
    var planName by rememberSaveable { mutableStateOf<String?>(null) }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var beneficiariesOpen by rememberSaveable { mutableStateOf(false) }
    var request by remember { mutableStateOf<PurchaseRequest?>(null) }

    LaunchedEffect(Unit) {
        delay(BANNER_MILLIS)
        bannerVisible = false
    }

    val provider = TvProviders.firstOrNull { it.name == providerName }
    val plan = provider?.plans?.firstOrNull { it.name == planName }
    val customerName = if (smartcard.length >= MIN_SMARTCARD_DIGITS) {
        CustomerNames[smartcard.last() - '0']
    } else {
        null
    }
    val canPay = provider != null && plan != null && customerName != null

    fun openPay() {
        val chosenProvider = provider ?: return
        val chosenPlan = plan ?: return
        request = PurchaseRequest(
            type = TxType.Bill,
            title = chosenProvider.name + " " + chosenPlan.name,
            subtitle = "Smartcard $smartcard",
            counterparty = chosenProvider.name,
            amountKobo = chosenPlan.priceNaira * 100L
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
                title = "TV",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "History",
                onAction = { onNavigate(Routes.Transactions) }
            )
            if (bannerVisible) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .clip(OPayShapes.card)
                            .background(Color.White)
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.opay_logo_full),
                            contentDescription = "OPay",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .width(160.dp)
                                .aspectRatio(3f)
                        )
                    }
                }
            } else {
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
                    }
                    OPayCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Smartcard Number",
                                style = OPayType.body,
                                fontWeight = FontWeight.Bold,
                                color = c.text,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Beneficiaries >",
                                style = OPayType.bodySmall,
                                color = c.green,
                                modifier = Modifier.clickable {
                                    if (sessionCards.isEmpty()) {
                                        toast.show("No beneficiaries yet")
                                    } else {
                                        beneficiariesOpen = !beneficiariesOpen
                                    }
                                }
                            )
                        }
                        OPayUnderlineField(
                            value = smartcard,
                            onValueChange = { input ->
                                smartcard = input.filter { it.isDigit() }
                            },
                            placeholder = "Enter",
                            keyboardType = KeyboardType.Number,
                            maxLength = MAX_SMARTCARD_DIGITS
                        )
                        if (beneficiariesOpen) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                sessionCards.forEachIndexed { index, number ->
                                    if (index > 0) OPayRowDivider()
                                    Text(
                                        text = number,
                                        style = OPayType.body,
                                        color = c.text,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                smartcard = number
                                                beneficiariesOpen = false
                                            }
                                            .padding(vertical = 10.dp)
                                    )
                                }
                            }
                        }
                        if (customerName != null) {
                            Text(
                                text = customerName,
                                style = OPayType.title,
                                fontWeight = FontWeight.Bold,
                                color = c.green,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                    if (provider != null && customerName != null) {
                        OPayCard {
                            provider.plans.forEachIndexed { index, item ->
                                if (index > 0) Spacer(Modifier.height(8.dp))
                                PlanRow(
                                    plan = item,
                                    selected = item.name == planName,
                                    onClick = { planName = item.name }
                                )
                            }
                        }
                    }
                    OPayButton(
                        text = "Pay",
                        onClick = { openPay() },
                        modifier = Modifier.padding(top = 6.dp),
                        enabled = canPay
                    )
                    Spacer(Modifier.navigationBarsPadding().height(8.dp))
                }
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
            TvProviders.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (item.name != providerName) planName = null
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
            },
            onSuccess = { SessionCards.add(smartcard) }
        )
    }
}

@Composable
private fun PlanRow(plan: TvPlan, selected: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.tile)
            .background(if (selected) c.greenSoft else c.cardAlt)
            .border(
                width = 1.dp,
                color = if (selected) c.green else Color.Transparent,
                shape = OPayShapes.tile
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = plan.name,
            style = OPayType.body,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else c.text,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = priceText(plan.priceNaira),
            style = OPayType.body,
            fontWeight = FontWeight.SemiBold,
            color = c.green
        )
    }
}
