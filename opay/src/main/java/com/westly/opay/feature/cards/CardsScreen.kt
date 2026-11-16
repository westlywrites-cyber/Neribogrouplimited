package com.westly.opay.feature.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.core.ui.overlay.rememberOPayToast
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDateTime

private const val NOT_AVAILABLE = ""
private const val SKELETON_MILLIS = 600L
private const val CARD_RATIO = 1.586f

private val OfferDeadline: LocalDateTime = LocalDateTime.of(2026, 11, 15, 23, 59)
private val BadgePink = Color(0xFFFF4F9A)
private val Gold = Color(0xFFE8C766)

private data class CardSpec(val name: String, val start: Color, val end: Color)

private val CardSpecs = listOf(
    CardSpec("Glow Up Card", Color(0xFF1C6B4A), Color(0xFFC98A2B)),
    CardSpec("Classic Card", Color(0xFF2B3A55), Color(0xFF10151F)),
    CardSpec("Premium Card", Color(0xFF5A3E8E), Color(0xFF1E1535))
)

private fun offerText(now: LocalDateTime = LocalDateTime.now()): String {
    val remaining = Duration.between(now, OfferDeadline)
    if (remaining.isNegative || remaining.isZero) return "Offer ended"
    val days = remaining.toDays()
    val hours = remaining.toHours() % 24
    return "25% Discount expires in $days Days ${hours}h"
}

@Composable
fun CardsScreen(modifier: Modifier = Modifier) {
    val c = OPay.colors
    val toast = rememberOPayToast()
    var tab by rememberSaveable { mutableStateOf(0) }
    var loading by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (loading) {
            delay(SKELETON_MILLIS)
            loading = false
        }
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cards",
                    style = OPayType.headline,
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                Text(text = "Q&A", style = OPayType.body, color = c.green)
            }
            SegmentedTabs(selected = tab, onSelect = { tab = it })
            if (loading) {
                CardsSkeleton(modifier = Modifier.weight(1f))
            } else if (tab == 0) {
                VirtualCardView(
                    onGetIt = { toast.show(NOT_AVAILABLE) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                PhysicalCardView(
                    onContinue = { toast.show(NOT_AVAILABLE) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun SegmentedTabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        SegmentLabel(text = "Virtual Card", active = selected == 0, onClick = { onSelect(0) })
        Box {
            SegmentLabel(text = "Physical Card", active = selected == 1, onClick = { onSelect(1) })
            Text(
                text = "25% OFF",
                style = OPayType.captionSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 26.dp, y = (-12).dp)
                    .background(BadgePink, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SegmentLabel(text: String, active: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = OPayType.titleSmall,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) c.text else c.muted
        )
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (active) c.green else Color.Transparent)
        )
    }
}

@Composable
private fun CardsSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 12.dp)) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 190.dp)
        Spacer(Modifier.height(12.dp))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 18.dp)
        Spacer(Modifier.height(16.dp))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 210.dp)
    }
}

@Composable
private fun VirtualCardView(onGetIt: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    val pagerState = rememberPagerState(pageCount = { CardSpecs.size })
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 40.dp),
                pageSpacing = 10.dp
            ) { page ->
                VirtualCardFace(spec = CardSpecs[page])
            }
            Text(
                text = "Available till Nov 15, 2026",
                style = OPayType.caption,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 12.dp)
            )
            OPayCard(modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(
                    text = "Glow Up Card",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = c.text
                )
                Spacer(Modifier.height(10.dp))
                BenefitRow(
                    icon = OPayIcon.Star,
                    title = "Made for the Rising Ones",
                    subtitle = highlighted(
                        before = "Get the Glow Up Card Instantly — Apply Till ",
                        highlight = "November 15, 2026",
                        after = "",
                        color = c.green
                    )
                )
                BenefitRow(
                    icon = OPayIcon.Bag,
                    title = "Accepted at 40K+ Online Merchants",
                    subtitle = highlighted(
                        before = "Including ",
                        highlight = "GooglePlay, Netflix, Glovo, Shein, Jumia, Konga",
                        after = ", and more",
                        color = c.green
                    )
                )
                BenefitRow(
                    icon = OPayIcon.Tag,
                    title = "Up to 15% cashback",
                    subtitle = highlighted(
                        before = "",
                        highlight = "Earn cashback",
                        after = " on card payments",
                        color = c.green
                    )
                )
            }
        }
        OPayButton(
            text = "Get It Now",
            onClick = onGetIt,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

private fun highlighted(
    before: String,
    highlight: String,
    after: String,
    color: Color
): AnnotatedString = buildAnnotatedString {
    append(before)
    withStyle(SpanStyle(color = color)) { append(highlight) }
    append(after)
}

@Composable
private fun BenefitRow(icon: OPayIcon, title: String, subtitle: AnnotatedString) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(c.cardAlt, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 18.dp, tint = c.green)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = title,
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = OPayType.caption, color = c.muted)
        }
    }
}

@Composable
private fun VirtualCardFace(spec: CardSpec, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .clip(RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(spec.start, spec.end),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = size.height * 0.55f,
                center = Offset(size.width * 0.88f, size.height * 0.15f)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.height * 0.40f,
                center = Offset(size.width * 0.12f, size.height)
            )
            val chipWidth = size.width * 0.14f
            drawRoundRect(
                color = Gold,
                topLeft = Offset(size.width * 0.07f, size.height * 0.40f),
                size = Size(chipWidth, chipWidth * 0.75f),
                cornerRadius = CornerRadius(6.dp.toPx())
            )
        }
        Text(
            text = spec.name,
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        )
        Text(
            text = "Verve",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun PhysicalCardView(onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    val offer by produceState(initialValue = offerText()) {
        while (true) {
            delay(60_000L)
            value = offerText()
        }
    }
    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = "You Have An Incomplete Application.",
                style = OPayType.body,
                color = c.danger,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.danger.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            )
            Spacer(Modifier.height(14.dp))
            PhysicalCardFace()
            Spacer(Modifier.height(22.dp))
            StepsRow()
            Spacer(Modifier.height(18.dp))
            Text(
                text = offer,
                style = OPayType.caption,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        OPayButton(
            text = "Continue to apply",
            onClick = onContinue,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun PhysicalCardFace(modifier: Modifier = Modifier) {
    val bridgeColor = Color(0xFF1D7A5A)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(CARD_RATIO)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)

            val chipWidth = w * 0.14f
            drawRoundRect(
                color = Gold,
                topLeft = Offset(w * 0.07f, h * 0.22f),
                size = Size(chipWidth, chipWidth * 0.75f),
                cornerRadius = CornerRadius(6.dp.toPx())
            )

            val left = w * 0.07f
            val span = w * 0.50f
            val deckY = h * 0.84f
            val towerTop = h * 0.52f
            val towerA = left + span * 0.30f
            val towerB = left + span * 0.70f
            val sagControlY = h * 0.84f

            drawLine(
                color = bridgeColor,
                start = Offset(left, deckY),
                end = Offset(left + span, deckY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = bridgeColor,
                start = Offset(towerA, deckY),
                end = Offset(towerA, towerTop),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = bridgeColor,
                start = Offset(towerB, deckY),
                end = Offset(towerB, towerTop),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )

            val cables = Path().apply {
                moveTo(left, deckY)
                quadraticTo(left + span * 0.12f, deckY, towerA, towerTop)
                quadraticTo((towerA + towerB) / 2f, sagControlY, towerB, towerTop)
                quadraticTo(left + span * 0.88f, deckY, left + span, deckY)
            }
            drawPath(path = cables, color = bridgeColor, style = stroke)

            for (i in 1..5) {
                val t = i / 6f
                val x = towerA + t * (towerB - towerA)
                val y = (1f - t) * (1f - t) * towerTop +
                    2f * t * (1f - t) * sagControlY +
                    t * t * towerTop
                drawLine(
                    color = bridgeColor,
                    start = Offset(x, y),
                    end = Offset(x, deckY),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        Text(
            text = "Verve",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF0B2A4A),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )
    }
}

@Composable
private fun StepsRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Step(label = "Apply", icon = OPayIcon.Doc, active = true)
        StepConnector(modifier = Modifier.weight(1f))
        Step(label = "Activate", icon = OPayIcon.Zap, active = false)
        StepConnector(modifier = Modifier.weight(1f))
        Step(label = "Transact", icon = OPayIcon.Card, active = false)
    }
}

@Composable
private fun StepConnector(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 18.dp, start = 6.dp, end = 6.dp)
            .height(1.dp)
            .background(OPay.colors.divider)
    )
}

@Composable
private fun Step(label: String, icon: OPayIcon, active: Boolean) {
    val c = OPay.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(if (active) c.green else c.cardAlt, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(
                icon = icon,
                size = 18.dp,
                tint = if (active) c.onGreen else c.muted
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = OPayType.caption,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) c.green else c.muted
        )
    }
}
