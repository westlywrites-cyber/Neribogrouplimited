package com.westly.opay.feature.me

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val DAILY_LIMIT_KOBO = 500_000_000L
private const val MIN_VISIBLE_FRACTION = 0.02f

private val GoldCard = Color(0xFFE9D58A)
private val GoldInk = Color(0xFF4A3A05)
private val GoldBadge = Color(0xFFC9A227)

private data class TierBenefit(
    val tier: Int,
    val label: String,
    val maxDaily: String,
    val maxBalance: String
)

private val TierBenefits = listOf(
    TierBenefit(1, "Tier 1", "\u20A650,000", "\u20A6300,000"),
    TierBenefit(2, "Tier 2", "\u20A6200,000", "\u20A6500,000"),
    TierBenefit(3, "Tier 3", "\u20A65,000,000", "Unlimited")
)

@Composable
fun AccountLimitsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val profile by repository.profile.collectAsStateWithLifecycle()
    val transactions by repository.transactions.collectAsStateWithLifecycle()

    val zone = ZoneId.of("Africa/Lagos")
    val today = LocalDate.now(zone)
    val usedKobo = transactions
        .filter {
            it.amountKobo < 0 &&
                Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() == today
        }
        .sumOf { -it.amountKobo }
    val fraction = (usedKobo.toFloat() / DAILY_LIMIT_KOBO.toFloat())
        .coerceIn(MIN_VISIBLE_FRACTION, 1f)

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
                title = "Account Limits",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                OPayCard(background = GoldCard) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Account Info",
                                style = OPayType.caption,
                                color = GoldInk
                            )
                            Text(
                                text = groupAccountNumber(profile.accountNumber),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldInk,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            if (profile.fullName.isNotBlank()) {
                                Text(
                                    text = profile.fullName,
                                    style = OPayType.bodySmall,
                                    color = GoldInk,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(GoldBadge),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = profile.tier.toString(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = GoldInk
                            )
                            Text(
                                text = "Tier",
                                fontSize = 10.sp,
                                color = GoldInk
                            )
                        }
                    }
                }
                OPayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Transaction Limit",
                            style = OPayType.body,
                            fontWeight = FontWeight.Bold,
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .height(8.dp)
                            .clip(OPayShapes.pill)
                            .background(c.cardAlt)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction)
                                .clip(OPayShapes.pill)
                                .background(c.green)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Min \u20A60",
                            style = OPayType.caption,
                            color = c.muted,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Max \u20A65,000,000",
                            style = OPayType.caption,
                            color = c.muted
                        )
                    }
                }
                OPayCard {
                    Text(
                        text = "Level Benefit",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    Spacer(Modifier.height(10.dp))
                    BenefitRow(
                        first = "Tier",
                        second = "Maximum Daily transaction",
                        third = "Maximum account balance",
                        header = true,
                        current = false
                    )
                    TierBenefits.forEach { item ->
                        OPayRowDivider()
                        val isCurrent = item.tier == profile.tier
                        BenefitRow(
                            first = if (isCurrent) item.label + " (Current)" else item.label,
                            second = item.maxDaily,
                            third = item.maxBalance,
                            header = false,
                            current = isCurrent
                        )
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
    }
}

@Composable
private fun BenefitRow(
    first: String,
    second: String,
    third: String,
    header: Boolean,
    current: Boolean
) {
    val c = OPay.colors
    val textColor = when {
        header -> c.muted
        current -> c.green
        else -> c.text
    }
    val weight = if (current) FontWeight.Bold else FontWeight.Normal
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (current) c.greenSoft else Color.Transparent)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = first,
            style = OPayType.caption,
            fontWeight = weight,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = second,
            style = OPayType.caption,
            fontWeight = weight,
            color = textColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.1f)
        )
        Text(
            text = third,
            style = OPayType.caption,
            fontWeight = weight,
            color = textColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f)
        )
    }
}
