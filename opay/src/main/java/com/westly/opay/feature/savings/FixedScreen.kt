package com.westly.opay.feature.savings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.SavingsBook
import com.westly.opay.data.SavingsKind
import com.westly.opay.data.SavingsPosition
import com.westly.opay.data.SavingsRates
import com.westly.opay.feature.wealth.WealthViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FixedDurationDays = listOf(7, 30, 90, 180, 365)

private val MaturityZone: ZoneId = ZoneId.of("Africa/Lagos")
private val MaturityFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
private const val MILLIS_PER_DAY = 86_400_000L

private val CoinGold = Color(0xFFF5C542)
private val CoinRim = Color(0xFFD9A21B)

@Composable
fun FixedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WealthViewModel = viewModel()
) {
    val toast = rememberOPayToast()
    val action = rememberSavingsAction(toast)
    val positions by viewModel.positions.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val plans = SavingsBook.ofKind(positions, SavingsKind.Fixed)
    var planDays by rememberSaveable { mutableStateOf(0L) }
    var withdrawId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPlans by rememberSaveable { mutableStateOf(false) }

    SavingsPage(
        title = "Fixed",
        onBack = onBack,
        toast = toast,
        modifier = modifier,
        overlay = {
            SavingsAmountSheet(
                visible = planDays > 0L,
                title = "Fixed for $planDays days",
                confirmText = "Save",
                action = action,
                onDismiss = { planDays = 0L },
                note = "Locked until maturity at " + SavingsBook.rateLabel(SavingsRates.FIXED_BPS),
                onConfirm = { kobo ->
                    val days = planDays
                    val maturesAt = Instant.ofEpochMilli(System.currentTimeMillis() + days * MILLIS_PER_DAY).toString()
                    action.start(
                        call = { done ->
                            viewModel.create(
                                SavingsKind.Fixed,
                                "Fixed $days days",
                                kobo,
                                maturesAt = maturesAt,
                                onDone = done
                            )
                        },
                        onSuccess = {
                            planDays = 0L
                            toast.show("Fixed savings created")
                        }
                    )
                }
            )
            val target = plans.firstOrNull { it.id == withdrawId }
            SavingsAmountSheet(
                visible = target != null,
                title = "Withdraw from " + (target?.name ?: "Fixed"),
                confirmText = "Withdraw",
                action = action,
                onDismiss = { withdrawId = null },
                note = "Accrued interest is paid out with a withdrawal.",
                allKobo = target?.principalKobo,
                onConfirm = { kobo ->
                    val id = withdrawId
                    if (id != null) {
                        action.start(
                            call = { done -> viewModel.withdraw(id, kobo, done) },
                            onSuccess = {
                                withdrawId = null
                                toast.show("Withdrawal successful")
                            }
                        )
                    }
                }
            )
        }
    ) {
        SavingsHero(
            art = {
                LockIllustration(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 10.dp)
                        .size(84.dp)
                )
            }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fixed",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.width(8.dp))
                HeroChip(text = "up to " + SavingsBook.rateLabel(plans.maxOfOrNull { it.rateBps } ?: SavingsRates.FIXED_BPS))
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier.clickable { toast.show(NOT_AVAILABLE) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transactions",
                        style = OPayType.caption,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                    OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = formatNaira(summary.of(SavingsKind.Fixed).totalKobo),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.clickable { showPlans = !showPlans },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Fixed Plans",
                    style = OPayType.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${plans.size} Active Plans",
                    style = OPayType.caption,
                    color = Color.White.copy(alpha = 0.85f)
                )
                OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = Color.White)
            }
        }

        if (showPlans) {
            SavingsCard {
                if (plans.isEmpty()) {
                    Text(text = "No active plans", style = OPayType.bodySmall, color = SavingsColors.Muted)
                }
                plans.forEachIndexed { index, plan ->
                    if (index > 0) SavingsDivider()
                    PlanRow(plan = plan, onWithdraw = {
                        action.clear()
                        withdrawId = plan.id
                    })
                }
            }
        }

        Text(
            text = "Choose a plan to start",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SavingsColors.Ink,
            modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
        )

        SavingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Big Friday Fixed",
                            style = OPayType.body,
                            fontWeight = FontWeight.Bold,
                            color = SavingsColors.Ink
                        )
                        Spacer(Modifier.width(6.dp))
                        SavingsTag(text = "Sold Out")
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "25% p.a. Interest",
                        style = OPayType.title,
                        fontWeight = FontWeight.Bold,
                        color = SavingsColors.GreenDeep
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "40 Days  Max: \u20A65,000,000.00 each person",
                        style = OPayType.caption,
                        color = SavingsColors.Muted
                    )
                }
                Spacer(Modifier.width(8.dp))
                SavingsPill(text = "Save", onClick = {}, enabled = false)
            }
        }

        SavingsCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Interest Rate",
                    style = OPayType.caption,
                    color = SavingsColors.Muted,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Duration",
                    style = OPayType.caption,
                    color = SavingsColors.Muted,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(64.dp))
            }
            FixedDurationDays.forEach { days ->
                SavingsDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = SavingsBook.rateLabel(SavingsRates.FIXED_BPS),
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = SavingsColors.GreenDeep,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$days Days",
                        style = OPayType.body,
                        color = SavingsColors.Ink,
                        modifier = Modifier.weight(1f)
                    )
                    SavingsPill(text = "Save", onClick = {
                        action.clear()
                        planDays = days.toLong()
                    })
                }
            }
        }
    }
}

@Composable
private fun PlanRow(plan: SavingsPosition, onWithdraw: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = plan.name, style = OPayType.body, fontWeight = FontWeight.Bold, color = SavingsColors.Ink)
            Text(
                text = formatNaira(plan.principalKobo) + "  " + SavingsBook.rateLabel(plan.rateBps),
                style = OPayType.caption,
                color = SavingsColors.Muted
            )
            Text(
                text = "Interest " + formatNaira(plan.accruedKobo) +
                    (plan.maturesAtMillis?.let {
                        "  Matures " + MaturityFormat.format(Instant.ofEpochMilli(it).atZone(MaturityZone))
                    } ?: ""),
                style = OPayType.captionSmall,
                color = SavingsColors.Muted
            )
        }
        SavingsPill(text = "Withdraw", onClick = onWithdraw)
    }
}

@Composable
private fun LockIllustration(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val s = size.minDimension / 100f
        fun o(x: Float, y: Float) = Offset(x * s, y * s)

        drawArc(
            color = Color(0xFFD9E7E0),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = o(32f, 10f),
            size = Size(36f * s, 44f * s),
            style = Stroke(width = 8f * s, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = CoinGold,
            topLeft = o(24f, 38f),
            size = Size(52f * s, 44f * s),
            cornerRadius = CornerRadius(8f * s)
        )
        drawCircle(color = Color(0xFF7A4B00), radius = 6f * s, center = o(50f, 56f))
        drawRect(
            color = Color(0xFF7A4B00),
            topLeft = o(47.5f, 56f),
            size = Size(5f * s, 14f * s)
        )
        drawCircle(color = CoinRim, radius = 13f * s, center = o(20f, 84f))
        drawCircle(color = CoinGold, radius = 9f * s, center = o(20f, 84f))
        drawCircle(color = CoinRim, radius = 11f * s, center = o(82f, 78f))
        drawCircle(color = CoinGold, radius = 7.5f * s, center = o(82f, 78f))
    }
}
