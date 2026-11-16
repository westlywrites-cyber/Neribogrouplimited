package com.westly.opay.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.animateFloatAsState
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTierAvatar
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.core.ui.icons.OPayGlyph
import com.westly.opay.core.ui.icons.OPayGlyphView
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.util.formatNaira
import com.westly.opay.navigation.Routes

private val BalanceInk = Color(0xFF06271B)
private val BalanceGradient: Brush = SolidColor(Color(0xFF1DCF9F))
private val BalanceShape = RoundedCornerShape(24.dp)
private val PromoGreen = Color(0xFF18332A)
private val PromoText = Color(0xFFF2F2F2)
private val PromoMuted = Color(0xFF9B9B9F)
private val FixedGradient = Brush.linearGradient(
    colors = listOf(Color(0xFF0F3D2D), Color(0xFF175C43)),
    start = Offset.Zero,
    end = Offset.Infinite
)
private val TagPink = Color(0xFFFF4D8D)
private val HelpTagBg = Color(0xFFFFCFE1)
private val TagInk = Color(0xFFE0157A)
private val BadgeCoral = Color(0xFFFF6677)
private val BadgeInk = Color(0xFF3A0C14)
private val StripGreenDark = Color(0xFF172C25)
private val StripGreenLight = Color(0xFFD7F5E9)
private val TileDark = Color(0xFF1B1B1B)

private const val LATEST_TITLE_LENGTH = 14
private val BALANCE_SKELETON_HEIGHT = 112.dp
private val STRIP_SKELETON_HEIGHT = 36.dp

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val c = OPay.colors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val loadError = state.loadError

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .background(Brush.verticalGradient(listOf(c.card, c.bg)))
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 16.dp)
        ) {
            Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            HomeHeader(
                nickname = state.nickname,
                tier = state.tier,
                unread = state.unread,
                onNavigate = onNavigate
            )
            if (state.offline && loadError == null) {
                Text(
                    text = "Offline",
                    style = OPayType.caption,
                    color = c.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                )
            }
            Column(
                modifier = Modifier.padding(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy((-14).dp)
            ) {
                when {
                    state.loading -> {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(1f),
                            height = BALANCE_SKELETON_HEIGHT
                        )
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 22.dp),
                            height = STRIP_SKELETON_HEIGHT
                        )
                    }
                    loadError != null -> LoadErrorCard(
                        message = loadError,
                        onRetry = viewModel::retry
                    )
                    else -> {
                        BalanceCard(
                            balanceKobo = state.balanceKobo,
                            earnedTodayKobo = state.earnedTodayKobo,
                            hidden = state.hideBalance,
                            onToggleHidden = viewModel::toggleHideBalance,
                            onAdd = { onNavigate(Routes.AddMoney) },
                            modifier = Modifier.zIndex(1f)
                        )
                        val latest = state.latest
                        if (latest != null) {
                            LatestTransactionStrip(
                                latest = latest,
                                onClick = { onNavigate(Routes.Transactions) }
                            )
                        } else {
                            NoTransactionsStrip()
                        }
                    }
                }
            }
            BusinessServiceStrip(onClick = { onNavigate(Routes.BizPayment) })
            QuickActionsCard(onNavigate = onNavigate)
            ServicesCard(onNavigate = onNavigate)
            TasksCard(onClick = { onNavigate(Routes.Owealth) })
            BigFridayCard(onClick = { onNavigate(Routes.Owealth) })
        }
        SecurityBadge(
            onClick = { onNavigate(Routes.SecurityCenter) },
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}

@Composable
private fun HomeHeader(nickname: String, tier: Int, unread: Int, onNavigate: (String) -> Unit) {
    val c = OPay.colors
    val greeting = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = c.text)) {
            append(if (nickname.isBlank()) "Hi" else "Hi, ")
            append(nickname)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OPayTierAvatar(
            tier = tier,
            onClick = { onNavigate(Routes.AccountLimits) },
            size = 34.dp
        )
        Text(
            text = greeting,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        HeaderIcon(icon = OPayIcon.Support, onClick = { onNavigate(Routes.Help) }, tag = "HELP")
        HeaderIcon(icon = OPayIcon.Scan, onClick = { onNavigate(Routes.QrScan) })
        HeaderIcon(
            icon = OPayIcon.Bell,
            onClick = { onNavigate(Routes.Notifications) },
            badgeCount = unread
        )
    }
}

private fun headerIconLabel(icon: OPayIcon): String = when (icon) {
    OPayIcon.Support -> "Help"
    OPayIcon.Scan -> "Scan QR code"
    OPayIcon.Bell -> "Notifications"
    else -> "Action"
}

@Composable
private fun HeaderIcon(
    icon: OPayIcon,
    onClick: () -> Unit,
    badgeCount: Int = 0,
    tag: String? = null
) {
    val c = OPay.colors
    Box(modifier = Modifier.size(32.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .semantics { contentDescription = headerIconLabel(icon); role = Role.Button }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 22.dp, tint = c.text.copy(alpha = 0.82f))
        }
        if (badgeCount > 0) {
            Text(
                text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = BadgeInk,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-6).dp)
                    .background(BadgeCoral, CircleShape)
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            )
        }
        if (tag != null) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 9.sp)) { append(tag.take(2)) }
                    withStyle(SpanStyle(fontSize = 7.sp)) { append(tag.drop(2)) }
                },
                fontWeight = FontWeight.ExtraBold,
                color = TagInk,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 9.dp, y = (-8).dp)
                    .wrapContentWidth(align = Alignment.End, unbounded = true)
                    .background(HelpTagBg, RoundedCornerShape(7.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

private fun balanceText(kobo: Long, hidden: Boolean) = buildAnnotatedString {
    if (hidden) {
        withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)) { append("****") }
    } else {
        val full = formatNaira(kobo)
        val dot = full.lastIndexOf('.')
        val whole = if (dot >= 0) full.substring(0, dot) else full
        withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold)) { append(whole) }
        if (dot >= 0) {
            withStyle(SpanStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)) {
                append(full.substring(dot))
            }
        }
    }
    withStyle(SpanStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal)) { append(" \u203A") }
}

@Composable
private fun BalanceCard(
    balanceKobo: Long,
    earnedTodayKobo: Long,
    hidden: Boolean,
    onToggleHidden: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(BalanceGradient, BalanceShape)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Column(modifier = Modifier.align(Alignment.CenterStart)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OPayGlyphView(glyph = OPayGlyph.ShieldTick, size = 16.dp, tint = BalanceInk)
                Spacer(Modifier.width(4.dp))
                Text(text = "Available", style = OPayType.body, color = BalanceInk)
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = if (hidden) "Show balance" else "Hide balance"; role = Role.Button }
                        .clickable(onClick = onToggleHidden),
                    contentAlignment = Alignment.Center
                ) {
                    EyeIcon(hidden = hidden, tint = BalanceInk, size = 16.dp)
                }
            }
            Text(
                text = balanceText(balanceKobo, hidden),
                color = BalanceInk,
                modifier = Modifier.padding(top = 2.dp)
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "Earned Today ", style = OPayType.body, color = BalanceInk)
                Text(
                    text = "+" + formatNaira(earnedTodayKobo),
                    style = OPayType.body,
                    fontWeight = FontWeight.Bold,
                    color = BalanceInk
                )
            }
        }
        Text(
            text = "+ Add",
            style = OPayType.body,
            fontWeight = FontWeight.SemiBold,
            color = BalanceInk,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, BalanceInk.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 22.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun LoadErrorCard(message: String, onRetry: () -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(BalanceShape)
            .background(c.card)
            .clickable(onClick = onRetry)
            .padding(horizontal = 14.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = message, style = OPayType.body, color = c.text, textAlign = TextAlign.Center)
        Text(
            text = "Tap to retry",
            style = OPayType.body,
            fontWeight = FontWeight.SemiBold,
            color = c.green,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun NoTransactionsStrip() {
    val c = OPay.colors
    val dark = c.bg.luminance() < 0.5f
    Text(
        text = "No transactions yet",
        style = OPayType.body,
        color = c.muted,
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.card)
            .background(if (dark) StripGreenDark else StripGreenLight)
            .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 9.dp)
    )
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
private fun LatestTransactionStrip(latest: LatestTransaction, onClick: () -> Unit) {
    val c = OPay.colors
    val dark = c.bg.luminance() < 0.5f
    val title = if (latest.title.length > LATEST_TITLE_LENGTH) {
        latest.title.take(LATEST_TITLE_LENGTH) + "\u2026"
    } else {
        latest.title
    }
    val sign = if (latest.amountKobo < 0) "-" else "+"
    val amount = sign + formatNaira(kotlin.math.abs(latest.amountKobo))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(OPayShapes.card)
            .background(if (dark) StripGreenDark else StripGreenLight)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 12.dp, top = 22.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OPayGlyphView(glyph = OPayGlyph.CircleTick, size = 18.dp, tint = c.green)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "$title $amount",
                style = OPayType.body,
                fontSize = 12.5.sp,
                color = c.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(text = "Transactions \u203A", style = OPayType.body, fontSize = 12.5.sp, color = c.text, maxLines = 1)
    }
}

@Composable
private fun BusinessServiceStrip(onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(c.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(c.greenSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            StoreGlyph(size = 16.dp, tint = c.green)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Business Service - Today\u2019s Sales\u2026",
            style = OPayType.body,
            color = c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "\u203A", style = OPayType.body, color = c.text)
    }
}

@Composable
private fun StoreGlyph(size: Dp, tint: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val stroke = Stroke(width = 1.8f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val awning = Path().apply {
            moveTo(3f * unit, 9f * unit)
            lineTo(5f * unit, 3.5f * unit)
            lineTo(19f * unit, 3.5f * unit)
            lineTo(21f * unit, 9f * unit)
            for (i in 0..2) {
                val x0 = (3f + 6f * i) * unit
                quadraticBezierTo(x0 + 3f * unit, 13f * unit, x0 + 6f * unit, 9f * unit)
            }
        }
        val body = Path().apply {
            moveTo(4.5f * unit, 12.5f * unit)
            lineTo(4.5f * unit, 20.5f * unit)
            lineTo(19.5f * unit, 20.5f * unit)
            lineTo(19.5f * unit, 12.5f * unit)
        }
        val door = Path().apply {
            moveTo(9f * unit, 20.5f * unit)
            lineTo(9f * unit, 15f * unit)
            lineTo(15f * unit, 15f * unit)
            lineTo(15f * unit, 20.5f * unit)
        }
        drawPath(path = awning, color = tint, style = stroke)
        drawPath(path = body, color = tint, style = stroke)
        drawPath(path = door, color = tint, style = stroke)
    }
}

@Composable
private fun HomeTile(
    glyph: OPayGlyph,
    label: String,
    onClick: () -> Unit,
    round: Boolean,
    modifier: Modifier = Modifier,
    tag: String? = null
) {
    val c = OPay.colors
    val dark = c.bg.luminance() < 0.5f
    val background = if (dark) TileDark else c.cardAlt
    val shape = if (round) CircleShape else RoundedCornerShape(14.dp)
    Column(
        modifier = modifier.clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(if (round) 44.dp else 48.dp)
                    .background(background, shape),
                contentAlignment = Alignment.Center
            ) {
                OPayGlyphView(
                    glyph = glyph,
                    size = if (round) 24.dp else 28.dp,
                    tint = if (round) c.text else c.green
                )
            }
            if (tag != null) {
                Text(
                    text = tag,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp, y = (-4).dp)
                        .background(TagPink, RoundedCornerShape(6.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = c.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QuickActionsCard(onNavigate: (String) -> Unit) {
    OPayCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            HomeTile(OPayGlyph.ToOpay, "To OPay", { onNavigate(Routes.TransferOpay) }, false, Modifier.weight(1f))
            HomeTile(OPayGlyph.ToBank, "To Bank", { onNavigate(Routes.TransferBank) }, false, Modifier.weight(1f))
            HomeTile(OPayGlyph.Save, "Save", { onNavigate(Routes.Owealth) }, false, Modifier.weight(1f))
            HomeTile(OPayGlyph.Loan, "Loan", { onNavigate(Routes.Loan) }, false, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ServicesCard(onNavigate: (String) -> Unit) {
    OPayCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            HomeTile(OPayGlyph.Airtime, "Airtime", { onNavigate(Routes.Airtime) }, true, Modifier.weight(1f))
            HomeTile(OPayGlyph.Data, "Data", { onNavigate(Routes.Data) }, true, Modifier.weight(1f))
            HomeTile(OPayGlyph.Betting, "Betting", { onNavigate(Routes.Betting) }, true, Modifier.weight(1f))
            HomeTile(OPayGlyph.Tv, "TV", { onNavigate(Routes.Tv) }, true, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            HomeTile(OPayGlyph.OWealth, "OWealth", { onNavigate(Routes.Owealth) }, true, Modifier.weight(1f))
            HomeTile(
                glyph = OPayGlyph.LoanCentre,
                label = "Loan Centre",
                onClick = { onNavigate(Routes.Loan) },
                round = true,
                modifier = Modifier.weight(1f),
                tag = "Hot"
            )
            HomeTile(OPayGlyph.Invitation, "Invitation", { onNavigate(Routes.Invite) }, true, Modifier.weight(1f))
            HomeTile(OPayGlyph.More, "More", { onNavigate(Routes.AllService) }, true, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TasksCard(onClick: () -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(PromoGreen)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Complete Tasks, Earn Cash",
                style = OPayType.body,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PromoText,
                modifier = Modifier.weight(1f)
            )
            OPayGlyphView(glyph = OPayGlyph.GiftBox, size = 20.dp, tint = c.green)
            Spacer(Modifier.width(6.dp))
            OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = PromoText)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "SafeBox", style = OPayType.body, color = PromoText)
                Text(
                    text = "Activate SafeBox, autosave",
                    style = OPayType.captionSmall,
                    color = PromoMuted
                )
            }
            Text(
                text = "Claim",
                style = OPayType.body,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = BalanceInk,
                modifier = Modifier
                    .background(BalanceGradient, RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun BigFridayCard(onClick: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(FixedGradient)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Big Friday, Bigger Returns!",
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = PromoText
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Fixed savings - Get 25% p.a.",
                style = OPayType.caption,
                color = c.green
            )
        }
        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = PromoText)
    }
}

@Composable
private fun SecurityBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    var parentWidth by remember { mutableStateOf(0) }
    var parentHeight by remember { mutableStateOf(0) }
    var selfWidth by remember { mutableStateOf(0) }
    var selfHeight by remember { mutableStateOf(0) }
    var dragX by rememberSaveable { mutableStateOf(0f) }
    val restingBottomPx = with(LocalDensity.current) { BadgeRestingBottom.toPx() }
    var dragY by rememberSaveable { mutableStateOf(-restingBottomPx) }
    val animatedX by animateFloatAsState(targetValue = dragX, label = "badgeX")
    var dragging by remember { mutableStateOf(false) }
    val shownX = if (dragging) dragX else animatedX
    Column(
        modifier = modifier
            .onGloballyPositioned { coords ->
                selfWidth = coords.size.width
                selfHeight = coords.size.height
                parentWidth = coords.parentLayoutCoordinates?.size?.width ?: parentWidth
                parentHeight = coords.parentLayoutCoordinates?.size?.height ?: parentHeight
            }
            .offset { IntOffset(shownX.roundToInt(), dragY.roundToInt()) }
            .padding(end = 3.dp)
            .pointerInput(parentWidth, parentHeight, selfWidth, selfHeight) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        // Snap to the nearest side edge, like the real floating shield.
                        val maxLeft = -(parentWidth - selfWidth).toFloat()
                        dragX = if (dragX < maxLeft / 2f) maxLeft else 0f
                    },
                    onDragCancel = { dragging = false }
                ) { change, amount ->
                    change.consume()
                    val maxLeft = -(parentWidth - selfWidth).toFloat()
                    val maxUp = -(parentHeight - selfHeight).toFloat()
                    dragX = (dragX + amount.x).coerceIn(maxLeft, 0f)
                    dragY = (dragY + amount.y).coerceIn(maxUp, 0f)
                }
            }
            .semantics { contentDescription = "Security"; role = Role.Button }
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SecurityShieldArt()
        Text(
            text = "Click for Security",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .offset(y = (-6).dp)
                .background(c.green, RoundedCornerShape(16.dp))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

private val ShieldTop = Color(0xFFD2F8E6)
private val ShieldBottom = Color(0xFF3FD5A1)
private val PlatformTop = Color(0xFF8FEBCF)
private val PlatformBottom = Color(0xFF1FB88C)
private val PlatformInner = Color(0xFF0F9473)

/** The floating shield: a light green shield with a white tick, standing on a round glassy green base. */
@Composable
private fun SecurityShieldArt(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 76.dp, height = 78.dp)) {
        val w = size.width
        val h = size.height
        val glowCenter = Offset(w / 2f, h * 0.84f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(PlatformTop.copy(alpha = 0.40f), Color.Transparent),
                center = glowCenter,
                radius = w * 0.58f
            ),
            radius = w * 0.58f,
            center = glowCenter
        )
        val baseHeight = h * 0.30f
        val baseTop = h - baseHeight
        drawOval(
            brush = Brush.verticalGradient(listOf(PlatformTop, PlatformBottom), startY = baseTop, endY = h),
            topLeft = Offset(0f, baseTop),
            size = Size(w, baseHeight)
        )
        drawOval(
            color = PlatformInner,
            topLeft = Offset(w * 0.12f, baseTop + baseHeight * 0.16f),
            size = Size(w * 0.76f, baseHeight * 0.60f)
        )
        drawOval(
            color = Color.White.copy(alpha = 0.45f),
            topLeft = Offset(w * 0.12f, baseTop + baseHeight * 0.16f),
            size = Size(w * 0.76f, baseHeight * 0.60f),
            style = Stroke(width = 1.2.dp.toPx())
        )

        val shieldWidth = w * 0.80f
        val shieldHeight = h * 0.78f
        val left = (w - shieldWidth) / 2f
        val shield = Path().apply {
            fun x(u: Float) = left + u * shieldWidth
            fun y(v: Float) = v * shieldHeight
            moveTo(x(0.5f), y(0f))
            lineTo(x(0.95f), y(0.13f))
            lineTo(x(0.95f), y(0.50f))
            cubicTo(x(0.95f), y(0.75f), x(0.75f), y(0.93f), x(0.5f), y(1f))
            cubicTo(x(0.25f), y(0.93f), x(0.05f), y(0.75f), x(0.05f), y(0.50f))
            lineTo(x(0.05f), y(0.13f))
            close()
        }
        drawPath(
            path = shield,
            brush = Brush.verticalGradient(listOf(ShieldTop, ShieldBottom), startY = 0f, endY = shieldHeight)
        )
        drawPath(path = shield, color = Color.White.copy(alpha = 0.6f), style = Stroke(width = 1.5.dp.toPx()))

        val tick = Path().apply {
            moveTo(left + shieldWidth * 0.30f, shieldHeight * 0.48f)
            lineTo(left + shieldWidth * 0.45f, shieldHeight * 0.63f)
            lineTo(left + shieldWidth * 0.72f, shieldHeight * 0.33f)
        }
        val tickStroke = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(
            path = tick,
            color = Color(0x33007050),
            style = tickStroke
        )
        drawPath(
            path = Path().apply {
                addPath(tick, Offset(0f, -1.5.dp.toPx()))
            },
            color = Color.White,
            style = tickStroke
        )
    }
}

private val BadgeRestingBottom = 64.dp
