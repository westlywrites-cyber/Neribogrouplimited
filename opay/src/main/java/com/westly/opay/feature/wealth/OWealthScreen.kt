package com.westly.opay.feature.wealth

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.SavingsBook
import com.westly.opay.data.SavingsKind
import com.westly.opay.data.SavingsRates
import com.westly.opay.feature.savings.SavingsAmountSheet
import com.westly.opay.feature.savings.rememberSavingsAction
import com.westly.opay.navigation.Routes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private val HeroDarkStart = Color(0xFF0F3D2D)
private val HeroDarkEnd = Color(0xFF175C43)
private val MintStart = Color(0xFFE3F7EE)
private val MintEnd = Color(0xFFCDF2E1)
private val MintRow = Color(0xFFE9F8F1)
private val MintInk = Color(0xFF0F4D38)

private val ActivityZone: ZoneId = ZoneId.of("Africa/Lagos")
private val ActivityFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss", Locale.US)

private fun formatActivityDate(millis: Long): String =
    ActivityFormat.format(Instant.ofEpochMilli(millis).atZone(ActivityZone))

@Composable
fun OWealthScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WealthViewModel = viewModel()
) {
    val c = OPay.colors
    val dark = c.bg.luminance() < 0.5f
    val toast = rememberOPayToast()
    val action = rememberSavingsAction(toast)
    val hidden by viewModel.hideBalance.collectAsStateWithLifecycle()
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val activities by remember(viewModel) { viewModel.activities(SavingsKind.OWealth) }.collectAsStateWithLifecycle()
    val wealth = SavingsBook.ofKind(positions, SavingsKind.OWealth).firstOrNull()
    val totals = summary.of(SavingsKind.OWealth)
    var saveOpen by rememberSaveable { mutableStateOf(false) }
    var withdrawOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
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
                    text = "OWealth",
                    style = OPayType.title,
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "More",
                    style = OPayType.titleSmall,
                    color = c.muted,
                    modifier = Modifier.clickable { toast.show(NOT_AVAILABLE_MESSAGE) }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                HeroCard(
                    balanceKobo = totals.totalKobo,
                    interestKobo = totals.accruedKobo,
                    rateLabel = SavingsBook.rateLabel(wealth?.rateBps ?: SavingsRates.OWEALTH_BPS),
                    hidden = hidden,
                    dark = dark,
                    onWithdraw = {
                        if (wealth == null) {
                            toast.show("No OWealth savings yet")
                        } else {
                            action.clear()
                            withdrawOpen = true
                        }
                    },
                    onSave = {
                        action.clear()
                        saveOpen = true
                    }
                )
                Text(
                    text = "Optimize & Get Higher Returns",
                    style = OPayType.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.text,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
                OptimizeRow(
                    title = "Fixed Deposit",
                    chip = "Up to " + SavingsBook.rateLabel(SavingsRates.FIXED_BPS),
                    subtitle = "High returns on fixed-term savings.",
                    dark = dark,
                    onClick = { onNavigate(Routes.Fixed) }
                )
                OptimizeRow(
                    title = "SafeBox",
                    chip = SavingsBook.rateLabel(SavingsRates.SAFEBOX_BPS),
                    subtitle = "Save flexibly with monthly or quarterly withdrawals.",
                    dark = dark,
                    onClick = { onNavigate(Routes.SafeBox) }
                )
                OptimizeRow(
                    title = "Targets",
                    chip = SavingsBook.rateLabel(SavingsRates.TARGET_BPS),
                    subtitle = "Set and achieve savings goals.",
                    dark = dark,
                    onClick = { onNavigate(Routes.Targets) }
                )
                Spacer(Modifier.height(2.dp))
                OPayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Activities",
                            style = OPayType.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            modifier = Modifier.clickable { onNavigate(Routes.Transactions) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "View All", style = OPayType.bodySmall, color = c.muted)
                            OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = c.muted)
                        }
                    }
                    if (activities.isEmpty()) {
                        Text(
                            text = "No recent activities",
                            style = OPayType.bodySmall,
                            color = c.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp)
                        )
                    } else {
                        activities.forEachIndexed { index, item ->
                            if (index > 0) OPayRowDivider()
                            ActivityRow(item = item, hidden = hidden)
                        }
                    }
                }
                Text(
                    text = "Powered by OPay Microfinance Bank Insured by NDIC",
                    style = OPayType.captionSmall,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 20.dp)
                )
            }
        }
        SavingsAmountSheet(
            visible = saveOpen,
            title = "Save to OWealth",
            confirmText = "Save",
            action = action,
            onDismiss = { saveOpen = false },
            onConfirm = { kobo ->
                action.start(
                    call = { done ->
                        if (wealth == null) {
                            viewModel.create(SavingsKind.OWealth, "OWealth", kobo, onDone = done)
                        } else {
                            viewModel.deposit(wealth.id, kobo, done)
                        }
                    },
                    onSuccess = {
                        saveOpen = false
                        toast.show("Saved to OWealth")
                    }
                )
            }
        )
        SavingsAmountSheet(
            visible = withdrawOpen,
            title = "Withdraw from OWealth",
            confirmText = "Withdraw",
            action = action,
            onDismiss = { withdrawOpen = false },
            note = "Accrued interest is paid out with a withdrawal.",
            allKobo = wealth?.principalKobo,
            allText = "Withdraw all",
            onConfirm = { kobo ->
                val id = wealth?.id
                if (id != null) {
                    action.start(
                        call = { done -> viewModel.withdraw(id, kobo, done) },
                        onSuccess = {
                            withdrawOpen = false
                            toast.show("Withdrawal successful")
                        }
                    )
                }
            }
        )
        OPayToastHost(state = toast, bottomPadding = 40.dp)
    }
}

@Composable
private fun HeroCard(
    balanceKobo: Long,
    interestKobo: Long,
    rateLabel: String,
    hidden: Boolean,
    dark: Boolean,
    onWithdraw: () -> Unit,
    onSave: () -> Unit
) {
    val brush = if (dark) {
        Brush.linearGradient(listOf(HeroDarkStart, HeroDarkEnd))
    } else {
        Brush.linearGradient(listOf(MintStart, MintEnd))
    }
    val ink = if (dark) Color.White else MintInk
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(OPayShapes.card)
            .background(brush)
    ) {
        Text(
            text = "W",
            fontSize = 120.sp,
            fontWeight = FontWeight.Bold,
            color = ink.copy(alpha = 0.08f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = (-8).dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "OWealth",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = ink
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = rateLabel,
                    style = OPayType.caption,
                    color = ink,
                    modifier = Modifier
                        .background(ink.copy(alpha = 0.16f), OPayShapes.chip)
                        .padding(horizontal = 9.dp, vertical = 5.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = displayNaira(balanceKobo, hidden),
                style = OPayType.display,
                color = ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Interest " + displayNaira(interestKobo, hidden),
                style = OPayType.caption,
                color = ink.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OPayButton(
                    text = "Withdraw",
                    onClick = onWithdraw,
                    modifier = Modifier.weight(1f),
                    style = ButtonStyle.Dark
                )
                OPayButton(
                    text = "Save",
                    onClick = onSave,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun OptimizeRow(
    title: String,
    chip: String,
    subtitle: String,
    dark: Boolean,
    onClick: () -> Unit
) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(OPayShapes.card)
            .background(if (dark) c.card else MintRow)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = OPayType.body,
                    fontWeight = FontWeight.Bold,
                    color = c.text
                )
                Spacer(Modifier.width(8.dp))
                OPayChip(text = chip)
            }
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = OPayType.caption, color = c.muted)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "Save",
            style = OPayType.bodySmall,
            fontWeight = FontWeight.Bold,
            color = c.onGreen,
            modifier = Modifier
                .background(c.green, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, hidden: Boolean) {
    val c = OPay.colors
    val sign = if (item.amountKobo < 0) "-" else "+"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.title, style = OPayType.body, color = c.text)
            Text(
                text = formatActivityDate(item.timestampMillis),
                style = OPayType.captionSmall,
                color = c.muted
            )
        }
        Text(
            text = sign + displayNaira(abs(item.amountKobo), hidden),
            style = OPayType.body,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
    }
}
