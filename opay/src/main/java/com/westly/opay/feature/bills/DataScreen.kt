package com.westly.opay.feature.bills

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.model.TxType
import com.westly.opay.navigation.Routes
import java.util.Locale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val PLAN_COLUMNS = 3
private const val CASHBACK_RATE = 0.027
private const val SAVED_NUMBER = "09134567890"
private val PlanTabs = listOf("HOT", "Daily", "Weekly", "Weekend", "Monthly")

private data class DataPlan(
    val size: String,
    val duration: String,
    val priceNaira: Int,
    val footer: String? = null
)

private val PlansByTab: Map<String, List<DataPlan>> = mapOf(
    "HOT" to listOf(
        DataPlan("500MB", "30 Days", 280),
        DataPlan("1GB", "3 Days", 400),
        DataPlan("3GB", "3 Days", 1_170),
        DataPlan("5GB", "3 Days", 1_960),
        DataPlan("1GB", "7 Days", 420),
        DataPlan("3GB", "7 Days", 1_230),
        DataPlan("5GB", "7 Days", 2_060),
        DataPlan("1GB", "14 Days", 480, "1GB Night"),
        DataPlan("3GB", "14 Days", 1_230, "3GB Night"),
        DataPlan("5GB", "14 Days", 2_060, "5GB Night"),
        DataPlan("10GB", "14 Days", 4_160, "10GB Night")
    ),
    "Daily" to listOf(
        DataPlan("100MB", "1 Day", 100),
        DataPlan("200MB", "1 Day", 200),
        DataPlan("1GB", "1 Day", 350),
        DataPlan("2GB", "1 Day", 550),
        DataPlan("3.2GB", "2 Days", 1_000),
        DataPlan("5GB", "2 Days", 1_500)
    ),
    "Weekly" to listOf(
        DataPlan("750MB", "7 Days", 450),
        DataPlan("1.5GB", "7 Days", 600),
        DataPlan("6GB", "7 Days", 1_500),
        DataPlan("11GB", "7 Days", 3_000),
        DataPlan("18GB", "7 Days", 3_500),
        DataPlan("25GB", "7 Days", 5_000)
    ),
    "Weekend" to listOf(
        DataPlan("1GB", "Sat-Sun", 300),
        DataPlan("3GB", "Sat-Sun", 600),
        DataPlan("7GB", "Sat-Sun", 1_200),
        DataPlan("15GB", "Sat-Sun", 2_500)
    ),
    "Monthly" to listOf(
        DataPlan("1.5GB", "30 Days", 1_000),
        DataPlan("3GB", "30 Days", 1_500),
        DataPlan("6GB", "30 Days", 2_500),
        DataPlan("10GB", "30 Days", 3_500),
        DataPlan("20GB", "30 Days", 5_500),
        DataPlan("40GB", "30 Days", 10_000)
    )
)

private fun priceText(naira: Int): String = "\u20A6" + String.format(Locale.US, "%,d", naira)

private fun cashbackText(naira: Int): String =
    String.format(Locale.US, "+\u20A6%.2f Cashback", naira * CASHBACK_RATE)

@Composable
fun DataScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val profile by repository.profile.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()

    var typed by rememberSaveable { mutableStateOf<String?>(null) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var chosenNetwork by rememberSaveable { mutableStateOf<String?>(null) }
    val anchor = rememberPickerAnchor()
    var savedDeleted by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var gridView by rememberSaveable { mutableStateOf(true) }
    var request by remember { mutableStateOf<PurchaseRequest?>(null) }

    val ownDigits = localPhoneDigits(profile.phone)
    val digits = typed ?: ownDigits
    val shown = formatPhoneDisplay(digits)
    val network = chosenNetwork ?: detectNetwork(digits) ?: "MTN"
    val plans = PlansByTab.getValue(PlanTabs[tab])

    fun openPlan(plan: DataPlan) {
        val planName = plan.size + " " + plan.duration
        request = PurchaseRequest(
            type = TxType.Data,
            title = "Mobile Data",
            subtitle = planName + " \u00B7 " + shown,
            counterparty = digits,
            amountKobo = plan.priceNaira * 100L,
            storedTitle = network + " Data " + planName,
            remark = planName
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
        ) {
            OPayTopBar(
                title = "Mobile Data",
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
                            .then(anchor.modifier()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        NetworkSelector(
                            network = network,
                            open = pickerOpen,
                            onClick = { pickerOpen = !pickerOpen },
                            logoSize = 34.dp
                        )
                        Text(
                            text = "$shown \u25BE",
                            style = OPayType.title,
                            color = c.text,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { menuOpen = !menuOpen }
                        )
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(c.green)
                                .clickable { toast.show(NOT_AVAILABLE_MESSAGE) },
                            contentAlignment = Alignment.Center
                        ) {
                            OPayIconView(icon = OPayIcon.User, size = 18.dp, tint = c.onGreen)
                        }
                    }
                    if (menuOpen) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            SavedNumberRow(
                                text = formatPhoneDisplay(ownDigits) + " (me)",
                                digits = ownDigits,
                                onClick = {
                                    typed = null
                                    menuOpen = false
                                }
                            )
                            if (!savedDeleted) {
                                OPayRowDivider()
                                SavedNumberRow(
                                    text = formatPhoneDisplay(SAVED_NUMBER),
                                    digits = SAVED_NUMBER,
                                    onClick = {
                                        typed = SAVED_NUMBER
                                        menuOpen = false
                                    }
                                )
                            }
                            OPayRowDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        savedDeleted = true
                                        if (typed == SAVED_NUMBER) typed = null
                                        menuOpen = false
                                        toast.show("Saved numbers deleted")
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                TrashIcon(tint = c.muted)
                                Text(text = "Delete All", style = OPayType.bodySmall, color = c.muted)
                            }
                        }
                    }
                }
                OPayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Voucher",
                            style = OPayType.body,
                            fontWeight = FontWeight.Bold,
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                        OPayChip(text = "\u20A640", onClick = { toast.show(NOT_AVAILABLE_MESSAGE) })
                        OPayChip(text = "\u20A630", onClick = { toast.show(NOT_AVAILABLE_MESSAGE) })
                        OPayChip(text = "More", onClick = { toast.show(NOT_AVAILABLE_MESSAGE) })
                    }
                }
                OPayCard(background = c.greenSoft) {
                    Text(
                        text = "Top-up Data anytime, anywhere. Simply Dial *955*4# mobile no#",
                        style = OPayType.bodySmall,
                        color = Color.White
                    )
                }
                OPayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Data Plans",
                            style = OPayType.body,
                            fontWeight = FontWeight.Bold,
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                        ViewToggleIcon(
                            grid = false,
                            active = !gridView,
                            onClick = { gridView = false }
                        )
                        ViewToggleIcon(
                            grid = true,
                            active = gridView,
                            onClick = { gridView = true }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        PlanTabs.forEachIndexed { index, label ->
                            PlanTab(
                                label = label,
                                selected = index == tab,
                                onClick = { tab = index }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (gridView) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            plans.chunked(PLAN_COLUMNS).forEach { rowPlans ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowPlans.forEach { plan ->
                                        PlanGridCard(
                                            plan = plan,
                                            onClick = { openPlan(plan) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(PLAN_COLUMNS - rowPlans.size) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            plans.forEach { plan ->
                                PlanListRow(plan = plan, onClick = { openPlan(plan) })
                            }
                        }
                    }
                }
                OPayCard {
                    Text(
                        text = "Mobile Data Service",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    OPayRow(
                        title = "USSD enquiry",
                        onClick = { onNavigate(Routes.Ussd) },
                        icon = OPayIcon.Phone,
                        value = "Check phone balance and more"
                    )
                }
                OPayCard {
                    OPayRow(
                        title = "More Events",
                        onClick = { toast.show(NOT_AVAILABLE_MESSAGE) }
                    )
                }
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
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
            }
        )
    }
}

@Composable
private fun SavedNumberRow(text: String, digits: String, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NetworkLogo(name = detectNetwork(digits) ?: "MTN", size = 24.dp)
        Text(text = text, style = OPayType.body, color = c.text)
    }
}

@Composable
private fun PlanTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = OPayType.body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) c.green else c.muted
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(2.dp)
                .background(if (selected) c.green else Color.Transparent)
        )
    }
}

@Composable
private fun PlanGridCard(plan: DataPlan, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    Column(
        modifier = modifier
            .clip(OPayShapes.tile)
            .background(c.cardAlt)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = plan.size,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
            Text(text = plan.duration, style = OPayType.caption, color = c.text)
            Text(
                text = priceText(plan.priceNaira),
                style = OPayType.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = c.green,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = cashbackText(plan.priceNaira),
                fontSize = 10.sp,
                color = c.green,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (plan.footer != null) {
            Text(
                text = plan.footer,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.onGreen,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.warn)
                    .padding(vertical = 3.dp)
            )
        }
    }
}

@Composable
private fun PlanListRow(plan: DataPlan, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.tile)
            .background(c.cardAlt)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = plan.size,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
            Text(text = plan.duration, style = OPayType.caption, color = c.muted)
        }
        if (plan.footer != null) {
            Text(
                text = plan.footer,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = c.onGreen,
                modifier = Modifier
                    .clip(OPayShapes.chip)
                    .background(c.warn)
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = priceText(plan.priceNaira),
                style = OPayType.body,
                fontWeight = FontWeight.SemiBold,
                color = c.green
            )
            Text(
                text = cashbackText(plan.priceNaira),
                fontSize = 10.sp,
                color = c.green
            )
        }
    }
}

@Composable
private fun ViewToggleIcon(grid: Boolean, active: Boolean, onClick: () -> Unit) {
    val tint = if (active) OPay.colors.green else OPay.colors.muted
    Box(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = if (grid) "Grid view" else "List view"; role = Role.Button }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(16.dp)) {
            val unit = size.minDimension / 16f
            if (grid) {
                val cell = 6f * unit
                val corner = CornerRadius(1.5f * unit, 1.5f * unit)
                listOf(0f, 10f).forEach { x ->
                    listOf(0f, 10f).forEach { y ->
                        drawRoundRect(
                            color = tint,
                            topLeft = Offset(x * unit, y * unit),
                            size = Size(cell, cell),
                            cornerRadius = corner
                        )
                    }
                }
            } else {
                listOf(3f, 8f, 13f).forEach { y ->
                    drawLine(
                        color = tint,
                        start = Offset(1f * unit, y * unit),
                        end = Offset(15f * unit, y * unit),
                        strokeWidth = 2f * unit,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round)
        drawLine(
            color = tint,
            start = Offset(4f * unit, 6f * unit),
            end = Offset(20f * unit, 6f * unit),
            strokeWidth = 2f * unit,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(9f * unit, 3f * unit),
            end = Offset(15f * unit, 3f * unit),
            strokeWidth = 2f * unit,
            cap = StrokeCap.Round
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(6f * unit, 8f * unit),
            size = Size(12f * unit, 13f * unit),
            cornerRadius = CornerRadius(2f * unit, 2f * unit),
            style = stroke
        )
    }
}
