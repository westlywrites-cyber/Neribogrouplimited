package com.westly.opay.feature.transfer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.model.Transaction
import com.westly.opay.navigation.Routes
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val CONFETTI_MILLIS = 1_500
private const val CONFETTI_COUNT = 30
private const val CONFETTI_COLORS = 5
private const val CONFETTI_SEED = 11
private val ConfettiPurple = Color(0xFFB9A2FF)
private val BonusBackground = Color(0xFF1B0F1C)
private val BonusText = Color(0xFFF2F2F2)
private val BonusMuted = Color(0xFF9B9B9F)
private val BonusDivider = Color(0xFF38383B)
private val BonusTile = Color(0xFFFFFFFF)
private val BonusTileMark = Color(0xFF111111)

private data class BonusOffer(val title: String, val subtitle: String, val route: String)

private val BonusOffers = listOf(
    BonusOffer("Don\u2019t Miss Out", "Balance with Daily Interest", Routes.Owealth),
    BonusOffer("Transfer to OPay", "Enjoy 100% Success & Instant", Routes.TransferOpay),
    BonusOffer("Bet big, win big", "Get Discounts That Matter", Routes.Betting),
    BonusOffer("Up to 6% cashback", "Buy Airtime and get cashback", Routes.Airtime)
)

private class ConfettiPiece(
    val angle: Float,
    val distance: Float,
    val size: Float,
    val spin: Float,
    val colorIndex: Int
)

@Composable
fun TransferSuccessScreen(
    onDone: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selected by SelectedTransaction.current.collectAsStateWithLifecycle()
    val redirect = remember { PaymentExit.consume() }
    val transaction = selected

    if (redirect != null || transaction == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(OPay.colors.bg)
        )
        LaunchedEffect(Unit) {
            if (redirect == null || redirect == Routes.Home) {
                onDone()
            } else {
                onNavigate(redirect)
            }
        }
    } else {
        SuccessContent(
            transaction = transaction,
            onDone = onDone,
            onNavigate = onNavigate,
            modifier = modifier
        )
    }
}

@Composable
private fun SuccessContent(
    transaction: Transaction,
    onDone: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val favourites by repository.favourites.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val account = transaction.counterpartyAccount
    val isFavourite = account != null && favourites.any { it.accountNumber == account }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = "Done",
                style = OPayType.titleSmall,
                color = c.green,
                modifier = Modifier.clickable(onClick = onDone)
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                ConfettiBurst(modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(c.green),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Check, size = 28.dp, tint = Color.White)
                }
            }
            Text(
                text = "Transfer successful",
                fontSize = 19.sp,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = formatNaira(abs(transaction.amountKobo)),
                style = OPayType.display,
                color = c.text,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 14.dp)
            )
            OPayCard {
                Text(
                    text = "The recipient account is expected to be credited within 5 minutes, " +
                        "subject to notification by the bank.",
                    fontSize = 15.sp,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard(
                    icon = OPayIcon.Share,
                    label = "Share Receipt",
                    enabled = true,
                    onClick = { onNavigate(Routes.Receipt) },
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = OPayIcon.Plus,
                    label = "Add Money",
                    enabled = true,
                    onClick = { onNavigate(Routes.AddMoney) },
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionCard(
                    icon = OPayIcon.User,
                    label = if (isFavourite) "Favourites Added" else "Add to favourites",
                    enabled = account != null && !isFavourite,
                    onClick = {
                        val saved = account?.let { repository.findBeneficiary(it) }
                        if (saved != null && !saved.isFavourite) {
                            scope.launch { repository.toggleFavourite(saved.accountNumber, saved.bankCode) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                ActionCard(
                    icon = OPayIcon.Doc,
                    label = "View Details",
                    enabled = true,
                    onClick = { onNavigate(Routes.TxDetails) },
                    modifier = Modifier.weight(1f)
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(OPayShapes.card)
                    .background(BonusBackground)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Special Bonus For You",
                    style = OPayType.body,
                    fontWeight = FontWeight.Bold,
                    color = BonusText
                )
                BonusOffers.forEachIndexed { index, offer ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(BonusDivider)
                        )
                    }
                    BonusRow(offer = offer, onClick = { onNavigate(offer.route) })
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
private fun ActionCard(
    icon: OPayIcon,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Row(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(OPayShapes.card)
            .background(c.card)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OPayIconView(icon = icon, size = 22.dp, tint = if (enabled) c.green else c.muted)
        Text(
            text = label,
            style = OPayType.bodySmall,
            color = if (enabled) c.text else c.muted
        )
    }
}

@Composable
private fun BonusRow(offer: BonusOffer, onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(BonusTile),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Star, size = 18.dp, tint = BonusTileMark)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = offer.title, style = OPayType.body, color = BonusText)
            Text(text = offer.subtitle, style = OPayType.caption, color = BonusMuted)
        }
        Text(
            text = "GO",
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = c.onGreen,
            modifier = Modifier
                .clip(OPayShapes.pill)
                .background(c.green)
                .padding(horizontal = 18.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ConfettiBurst(modifier: Modifier = Modifier) {
    val c = OPay.colors
    val palette = listOf(c.green, c.warn, c.danger, c.text, ConfettiPurple)
    val pieces = remember {
        val random = Random(CONFETTI_SEED)
        List(CONFETTI_COUNT) {
            ConfettiPiece(
                angle = random.nextFloat() * (2.0 * PI).toFloat(),
                distance = 70f + random.nextFloat() * 90f,
                size = 4f + random.nextFloat() * 4f,
                spin = random.nextFloat() * 360f,
                colorIndex = random.nextInt(CONFETTI_COLORS)
            )
        }
    }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(CONFETTI_MILLIS, easing = LinearOutSlowInEasing))
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        if (t < 1f) {
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            pieces.forEach { piece ->
                val travel = piece.distance * density * t
                val x = center.x + cos(piece.angle) * travel
                val y = center.y + sin(piece.angle) * travel + 40f * density * t * t
                val edge = piece.size * density
                rotate(degrees = piece.spin + 360f * t, pivot = Offset(x, y)) {
                    drawRect(
                        color = palette[piece.colorIndex].copy(alpha = alpha),
                        topLeft = Offset(x - edge / 2f, y - edge / 2f),
                        size = Size(edge, edge * 0.6f)
                    )
                }
            }
        }
    }
}
