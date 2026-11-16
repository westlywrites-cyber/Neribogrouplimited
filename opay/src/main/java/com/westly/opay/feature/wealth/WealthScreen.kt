package com.westly.opay.feature.wealth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.LightStatusBarIcons
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTile
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.SavingsBook
import com.westly.opay.data.SavingsKind
import com.westly.opay.data.SavingsRates
import com.westly.opay.data.SavingsSummary
import com.westly.opay.navigation.Routes
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private val HeaderGreen = Color(0xFF14513B)
private val BalanceCardGreen = Color(0xFF17664A)
private val BannerBackground = Color(0xFF17402F)
private val LogoBackground = Color(0xFF101614)
private val PillYellow = Color(0xFFFFD43B)
private val PillYellowInk = Color(0xFF3A2A00)
private val TagPink = Color(0xFFFF4F9A)
private val GiftRed = Color(0xFFE5484D)
private val GiftBlue = Color(0xFF3B82F6)
private val RibbonGold = Color(0xFFF5C542)
private val TreeLight = Color(0xFF2FB36F)
private val TreeMid = Color(0xFF23995C)
private val TreeDark = Color(0xFF1B7F4B)
private val TrunkBrown = Color(0xFF8A5A33)

private const val BANNER_COUNT = 2
private const val BANNER_INTERVAL_MILLIS = 4_000L
private val BannerHeight = 138.dp

@Composable
fun WealthScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WealthViewModel = viewModel()
) {
    LightStatusBarIcons()
    val c = OPay.colors
    val toast = rememberOPayToast()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val walletKobo by viewModel.walletKobo.collectAsStateWithLifecycle()
    val totalKobo by viewModel.totalKobo.collectAsStateWithLifecycle()
    val hidden by viewModel.hideBalance.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            WealthHeader(onSettings = { toast.show(NOT_AVAILABLE_MESSAGE) })
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)
            ) {
                BalanceCard(
                    summary = summary,
                    walletKobo = walletKobo,
                    totalKobo = totalKobo,
                    hidden = hidden,
                    expanded = expanded,
                    onToggleHidden = { viewModel.toggleHideBalance() },
                    onToggleExpanded = { expanded = !expanded }
                )
                OPayCard {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        ServiceItem(
                            modifier = Modifier.weight(1f),
                            icon = OPayIcon.Pig,
                            label = "OWealth",
                            tag = null,
                            onClick = { onNavigate(Routes.Owealth) }
                        )
                        ServiceItem(
                            modifier = Modifier.weight(1f),
                            icon = OPayIcon.Star,
                            label = "Targets",
                            tag = SavingsBook.rateLabel(SavingsRates.TARGET_BPS).removeSuffix(" p.a."),
                            onClick = { onNavigate(Routes.Targets) }
                        )
                        ServiceItem(
                            modifier = Modifier.weight(1f),
                            icon = OPayIcon.Lock,
                            label = "SafeBox",
                            tag = null,
                            onClick = { onNavigate(Routes.SafeBox) }
                        )
                        ServiceItem(
                            modifier = Modifier.weight(1f),
                            icon = OPayIcon.Lock,
                            label = "Fixed",
                            tag = null,
                            onClick = { onNavigate(Routes.Fixed) }
                        )
                        ServiceItem(
                            modifier = Modifier.weight(1f),
                            icon = OPayIcon.Save,
                            label = "Spend & Save",
                            tag = null,
                            onClick = { onNavigate(Routes.SpendSave) }
                        )
                    }
                }
                BannerCarousel(onOpenTargets = { onNavigate(Routes.Targets) })
                TileGrid(
                    onJoin = { toast.show(NOT_AVAILABLE_MESSAGE) },
                    onVouchers = { toast.show(NOT_AVAILABLE_MESSAGE) },
                    onBigFriday = { onNavigate(Routes.Fixed) }
                )
                Row(
                    modifier = Modifier.padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(c.green, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "OWealth and Savings are Powered by OPay MicroFinance Bank Ltd.",
                        style = OPayType.caption,
                        color = c.muted
                    )
                }
            }
        }
        OPayToastHost(state = toast, bottomPadding = 24.dp)
    }
}

@Composable
private fun WealthHeader(onSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderGreen)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Wealth",
            style = OPayType.headline,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(Color.White.copy(alpha = 0.16f), CircleShape)
                .semantics { contentDescription = "Settings"; role = Role.Button }
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            GearIcon(tint = Color.White, size = 18.dp)
        }
    }
}

private fun balanceText(kobo: Long, hidden: Boolean) = buildAnnotatedString {
    if (hidden) {
        append("\u20A6 ****")
    } else {
        val formatted = formatNaira(kobo)
        val dot = formatted.lastIndexOf('.')
        append("\u20A6 ")
        append(formatted.substring(1, dot))
        withStyle(SpanStyle(fontSize = 15.sp)) {
            append(formatted.substring(dot))
        }
    }
}

@Composable
private fun BalanceCard(
    summary: SavingsSummary,
    walletKobo: Long,
    totalKobo: Long,
    hidden: Boolean,
    expanded: Boolean,
    onToggleHidden: () -> Unit,
    onToggleExpanded: () -> Unit
) {
    val soft = Color.White.copy(alpha = 0.85f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(BalanceCardGreen, OPayShapes.card)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Total Balance", style = OPayType.caption, color = soft)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = if (hidden) "Show balance" else "Hide balance"; role = Role.Button }
                        .clickable(onClick = onToggleHidden),
                    contentAlignment = Alignment.Center
                ) {
                    EyeIcon(hidden = hidden, tint = soft, size = 15.dp)
                }
            }
            Text(text = "Interest", style = OPayType.caption, color = soft)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = balanceText(totalKobo, hidden),
                style = OPayType.display.copy(fontSize = 24.sp),
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "+" + displayNaira(summary.interestKobo, hidden),
                style = OPayType.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded)
                .padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (expanded) "View Assets Breakdown \u25B4" else "View Assets Breakdown \u25BE",
                style = OPayType.bodySmall,
                color = soft
            )
        }
        if (expanded) {
            Spacer(Modifier.height(6.dp))
            BreakdownRow(label = "Wallet", value = displayNaira(walletKobo, hidden))
            listOf(
                SavingsKind.OWealth,
                SavingsKind.SafeBox,
                SavingsKind.Fixed,
                SavingsKind.Target,
                SavingsKind.SpendSave
            ).forEach { kind ->
                BreakdownRow(label = kind.label, value = displayNaira(summary.of(kind).totalKobo, hidden))
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = OPayType.bodySmall,
            color = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = OPayType.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun ServiceItem(
    modifier: Modifier,
    icon: OPayIcon,
    label: String,
    tag: String?,
    onClick: () -> Unit
) {
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        OPayTile(icon = icon, label = label, onClick = onClick)
        if (tag != null) {
            Text(
                text = tag,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = 20.dp, y = (-4).dp)
                    .background(TagPink, RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun BannerCarousel(onOpenTargets: () -> Unit) {
    val c = OPay.colors
    val pagerState = rememberPagerState(pageCount = { BANNER_COUNT })

    LaunchedEffect(pagerState.currentPage) {
        delay(BANNER_INTERVAL_MILLIS)
        pagerState.animateScrollToPage((pagerState.currentPage + 1) % BANNER_COUNT)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(BannerHeight)
        ) { page ->
            if (page == 0) {
                ChristmasBanner(onClick = onOpenTargets)
            } else {
                LogoBanner()
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(BANNER_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(6.dp)
                        .background(
                            if (index == pagerState.currentPage) c.green else c.muted.copy(alpha = 0.4f),
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun ChristmasBanner(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(OPayShapes.card)
            .background(BannerBackground)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(14.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Christmas Savings Target",
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Save A Little Today, Celebrate Big Tomorrow",
                    style = OPayType.caption,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Start Saving",
                    style = OPayType.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = PillYellowInk,
                    modifier = Modifier
                        .background(PillYellow, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            ChristmasArt(
                modifier = Modifier
                    .width(110.dp)
                    .fillMaxHeight()
            )
        }
        Text(
            text = "15% p.a.",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PillYellowInk,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .background(PillYellow, RoundedCornerShape(10.dp))
                .padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ChristmasArt(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 110f
        val oy = size.height - 130f * s
        fun px(x: Float, y: Float) = Offset(x * s, oy + y * s)

        fun tier(apexY: Float, baseY: Float, half: Float, color: Color) {
            val path = Path().apply {
                moveTo(55f * s, oy + apexY * s)
                lineTo((55f + half) * s, oy + baseY * s)
                lineTo((55f - half) * s, oy + baseY * s)
                close()
            }
            drawPath(path = path, color = color)
        }

        tier(apexY = 20f, baseY = 58f, half = 24f, color = TreeLight)
        tier(apexY = 38f, baseY = 80f, half = 30f, color = TreeMid)
        tier(apexY = 58f, baseY = 104f, half = 38f, color = TreeDark)
        drawRect(
            color = TrunkBrown,
            topLeft = px(50f, 104f),
            size = Size(10f * s, 12f * s)
        )

        val star = Path().apply {
            for (i in 0 until 10) {
                val r = if (i % 2 == 0) 8f else 3.5f
                val a = Math.toRadians((-90 + i * 36).toDouble())
                val x = 55f + r * cos(a).toFloat()
                val y = 18f + r * sin(a).toFloat()
                if (i == 0) moveTo(x * s, oy + y * s) else lineTo(x * s, oy + y * s)
            }
            close()
        }
        drawPath(path = star, color = RibbonGold)

        drawCircle(color = GiftRed, radius = 3f * s, center = px(46f, 70f))
        drawCircle(color = RibbonGold, radius = 3f * s, center = px(66f, 92f))
        drawCircle(color = GiftRed, radius = 3f * s, center = px(40f, 96f))
        drawCircle(color = TagPink, radius = 2.5f * s, center = px(58f, 50f))

        drawRect(color = GiftRed, topLeft = px(6f, 102f), size = Size(28f * s, 22f * s))
        drawRect(color = RibbonGold, topLeft = px(18f, 102f), size = Size(4f * s, 22f * s))
        drawRect(color = RibbonGold, topLeft = px(6f, 110f), size = Size(28f * s, 4f * s))

        drawRect(color = GiftBlue, topLeft = px(78f, 108f), size = Size(26f * s, 16f * s))
        drawRect(color = RibbonGold, topLeft = px(89f, 108f), size = Size(4f * s, 16f * s))
        drawRect(color = RibbonGold, topLeft = px(78f, 114f), size = Size(26f * s, 3.5f * s))
    }
}

@Composable
private fun LogoBanner() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(OPayShapes.card)
            .background(LogoBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "OPay",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = OPay.colors.green
        )
    }
}

@Composable
private fun TileGrid(
    onJoin: () -> Unit,
    onVouchers: () -> Unit,
    onBigFriday: () -> Unit
) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(OPayShapes.card)
                .background(c.card)
                .clickable(onClick = onJoin)
                .padding(12.dp)
        ) {
            Text(
                text = "Over 10M Users",
                style = OPayType.titleSmall,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Save with OPay and fulfil your dreams with ease",
                style = OPayType.caption,
                color = c.muted
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(14.dp))
            Text(
                text = "J\u2026 \u2192",
                style = OPayType.bodySmall,
                fontWeight = FontWeight.Bold,
                color = c.onGreen,
                modifier = Modifier
                    .background(c.green, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(OPayShapes.card)
                    .background(c.card)
                    .clickable(onClick = onVouchers)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Savings Vouchers",
                    style = OPayType.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.text
                )
                Spacer(Modifier.height(4.dp))
                Text(text = "Claim vouchers", style = OPayType.caption, color = c.muted)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(OPayShapes.card)
                    .background(c.card)
                    .clickable(onClick = onBigFriday)
                    .padding(12.dp)
            ) {
                Text(
                    text = "Big Friday",
                    style = OPayType.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = c.text
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Get 25% p.a. \u2192",
                    style = OPayType.caption,
                    fontWeight = FontWeight.Bold,
                    color = c.green
                )
            }
        }
    }
}

@Composable
private fun EyeIcon(hidden: Boolean, tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val lens = Path().apply {
            moveTo(2f * unit, 12f * unit)
            quadraticBezierTo(12f * unit, 2f * unit, 22f * unit, 12f * unit)
            quadraticBezierTo(12f * unit, 22f * unit, 2f * unit, 12f * unit)
            close()
        }
        drawPath(path = lens, color = tint, style = stroke)
        drawCircle(
            color = tint,
            radius = 2.5f * unit,
            center = Offset(12f * unit, 12f * unit),
            style = stroke
        )
        if (hidden) {
            drawLine(
                color = tint,
                start = Offset(4f * unit, 20f * unit),
                end = Offset(20f * unit, 4f * unit),
                strokeWidth = 2f * unit,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun GearIcon(tint: Color, size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val center = Offset(12f * unit, 12f * unit)
        for (i in 0 until 8) {
            val a = Math.toRadians((i * 45).toDouble())
            val dx = cos(a).toFloat()
            val dy = sin(a).toFloat()
            drawLine(
                color = tint,
                start = Offset(center.x + dx * 8f * unit, center.y + dy * 8f * unit),
                end = Offset(center.x + dx * 11f * unit, center.y + dy * 11f * unit),
                strokeWidth = 3f * unit,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = tint,
            radius = 7f * unit,
            center = center,
            style = Stroke(width = 2.2f * unit)
        )
        drawCircle(
            color = tint,
            radius = 2.6f * unit,
            center = center,
            style = Stroke(width = 2f * unit)
        )
    }
}
