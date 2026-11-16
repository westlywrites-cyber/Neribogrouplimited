package com.westly.opay.feature.biz

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.LocalOPayColors
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayDarkColors
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTile
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.DailyTotals
import com.westly.opay.data.model.TxType
import com.westly.opay.feature.me.SystemBarIcons
import com.westly.opay.feature.transactions.formatRowDate
import com.westly.opay.navigation.Routes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val NOT_AVAILABLE_MESSAGE = ""

private val HeaderGreen = Color(0xFF1D7A5A)
private val MintCard = Color(0xFFCDF5E5)
private val MintStrip = Color(0xFFB2EAD1)
private val MintInk = Color(0xFF0B3B2A)
private val NewTag = Color(0xFFFF7A45)
private val NumberCard = Color(0xFF17402F)
private val TopTileWidth = 72.dp

private class TileSpec(val icon: OPayIcon, val label: String, val onClick: () -> Unit)

@Composable
fun BizPaymentScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    SystemBarIcons(darkIcons = false)
    CompositionLocalProvider(LocalOPayColors provides OPayDarkColors) {
        BizContent(onBack = onBack, onNavigate = onNavigate, modifier = modifier)
    }
}

@Composable
private fun BizContent(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val transactions by repository.transactions.collectAsStateWithLifecycle()
    val businessNumber by repository.businessNumber.collectAsStateWithLifecycle()
    val profile by repository.profile.collectAsStateWithLifecycle()
    val latestCredit = remember(transactions) {
        transactions
            .filter { it.type == TxType.TransferIn }
            .maxByOrNull { it.timestampMillis }
    }
    val received = remember(transactions) {
        DailyTotals.receivedToday(transactions, System.currentTimeMillis())
    }
    var showWallet by rememberSaveable { mutableStateOf(false) }
    val shownNumber: String? =
        (if (showWallet) profile.accountNumber else businessNumber)?.takeIf { it.isNotBlank() }
    val toast = rememberOPayToast()
    val unavailable: () -> Unit = { toast.show(NOT_AVAILABLE_MESSAGE) }

    val topTiles = listOf(
        TileSpec(OPayIcon.Bag, "My Shop", unavailable),
        TileSpec(OPayIcon.Bell, "Notifications") { onNavigate(Routes.Notifications) },
        TileSpec(OPayIcon.Chart, "EOD", unavailable),
        TileSpec(OPayIcon.Bank, "Large Transfer", unavailable),
        TileSpec(OPayIcon.Doc, "Report", unavailable)
    )
    val financeTiles = listOf(
        TileSpec(OPayIcon.Loan, "Loan") { onNavigate(Routes.Loan) },
        TileSpec(OPayIcon.Chart, "OWealth") { onNavigate(Routes.Owealth) },
        TileSpec(OPayIcon.Star, "Targets") { onNavigate(Routes.Targets) },
        TileSpec(OPayIcon.Lock, "SafeBox") { onNavigate(Routes.SafeBox) }
    )
    val supportTiles = listOf(
        TileSpec(OPayIcon.Scan, "My QR Code", unavailable),
        TileSpec(OPayIcon.Share, "Direct Transfer", unavailable),
        TileSpec(OPayIcon.Game, "Settings", unavailable),
        TileSpec(OPayIcon.Help, "Help") { onNavigate(Routes.Help) }
    )

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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(HeaderGreen, c.bg)))
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
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
                        OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = Color.White)
                    }
                    Text(
                        text = "My BizPayment",
                        style = OPayType.title,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    CircleAction(onClick = unavailable) { HeadsetGlyph(Color.White) }
                    CircleAction(onClick = unavailable) { CompassGlyph(Color.White) }
                }
                ReceivedCard(
                    totalKobo = received.totalKobo,
                    count = received.count,
                    onTrxns = { onNavigate(Routes.BizReceivedToday) }
                )
            }
            Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                OPayCard {
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        topTiles.forEach { tile ->
                            Box(
                                modifier = Modifier.width(TopTileWidth),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                OPayTile(icon = tile.icon, label = tile.label, onClick = tile.onClick)
                            }
                        }
                    }
                }
                OPayCard(background = NumberCard) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Business No.",
                            style = OPayType.titleSmall,
                            fontWeight = if (showWallet) FontWeight.Normal else FontWeight.Bold,
                            color = if (showWallet) c.muted else Color.White,
                            modifier = Modifier
                                .semantics { role = Role.Tab }
                                .clickable { showWallet = false }
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = "Wallet No.",
                            style = OPayType.titleSmall,
                            fontWeight = if (showWallet) FontWeight.Bold else FontWeight.Normal,
                            color = if (showWallet) Color.White else c.muted,
                            modifier = Modifier
                                .semantics { role = Role.Tab }
                                .clickable { showWallet = true }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "History >",
                            style = OPayType.caption,
                            color = c.muted,
                            modifier = Modifier.clickable { onNavigate(Routes.BizReceived) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (shownNumber == null) {
                            Box(modifier = Modifier.weight(1f)) {
                                SkeletonBlock(modifier = Modifier.width(180.dp), height = 32.dp)
                            }
                        } else {
                            Text(
                                text = groupNumber(shownNumber),
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            text = "Set Voice",
                            style = OPayType.caption,
                            fontWeight = FontWeight.Bold,
                            color = c.onGreen,
                            modifier = Modifier
                                .clip(OPayShapes.pill)
                                .background(c.green)
                                .clickable(onClick = unavailable)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                    if (latestCredit != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 14.dp)
                                .background(Color.Black.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = latestCredit.title,
                                    style = OPayType.body,
                                    color = Color.White
                                )
                                Text(
                                    text = formatRowDate(latestCredit.timestampMillis) +
                                        " \u2022 " + latestCredit.counterpartyName.orEmpty(),
                                    style = OPayType.caption,
                                    color = c.muted,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "+" + formatNaira(latestCredit.amountKobo),
                                    style = OPayType.body,
                                    fontWeight = FontWeight.Bold,
                                    color = c.green
                                )
                                Text(
                                    text = latestCredit.status,
                                    style = OPayType.caption,
                                    color = c.green,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }
                TileSection(title = "Finance", tiles = financeTiles)
                TileSection(title = "More Support", tiles = supportTiles)
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun ReceivedCard(totalKobo: Long, count: Int, onTrxns: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MintCard, OPayShapes.card)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Received Today \u24D8", style = OPayType.caption, color = MintInk)
                Text(
                    text = formatNaira(totalKobo),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MintInk,
                    modifier = Modifier.padding(top = 2.dp)
                )
                Text(
                    text = trxnLabel(count),
                    style = OPayType.caption,
                    color = MintInk,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(onClick = onTrxns)
                )
            }
            ShieldBadge()
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .background(MintStrip, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Instant credit to balance, and interest received today",
                style = OPayType.caption,
                color = MintInk,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "NEW",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .background(NewTag, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun TileSection(title: String, tiles: List<TileSpec>) {
    OPayCard {
        Text(
            text = title,
            style = OPayType.titleSmall,
            fontWeight = FontWeight.Bold,
            color = OPay.colors.text,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            tiles.forEach { tile ->
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.TopCenter
                ) {
                    OPayTile(icon = tile.icon, label = tile.label, onClick = tile.onClick)
                }
            }
        }
    }
}

@Composable
private fun CircleAction(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun ShieldBadge() {
    val c = OPay.colors
    Box(
        modifier = Modifier.size(46.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val shield = Path().apply {
                moveTo(w * 0.5f, h * 0.04f)
                lineTo(w * 0.9f, h * 0.18f)
                lineTo(w * 0.9f, h * 0.5f)
                cubicTo(w * 0.9f, h * 0.74f, w * 0.72f, h * 0.88f, w * 0.5f, h * 0.97f)
                cubicTo(w * 0.28f, h * 0.88f, w * 0.1f, h * 0.74f, w * 0.1f, h * 0.5f)
                lineTo(w * 0.1f, h * 0.18f)
                close()
            }
            drawPath(shield, HeaderGreen)
        }
        Text(
            text = "V",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = c.green
        )
    }
}

@Composable
private fun HeadsetGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.15f, h * 0.1f),
            size = Size(w * 0.7f, h * 0.7f),
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.08f, h * 0.45f),
            size = Size(w * 0.2f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.07f)
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.72f, h * 0.45f),
            size = Size(w * 0.2f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.07f)
        )
    }
}

@Composable
private fun CompassGlyph(color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = color,
            radius = w * 0.44f,
            style = Stroke(width = 1.6.dp.toPx())
        )
        val needle = Path().apply {
            moveTo(w * 0.68f, h * 0.32f)
            lineTo(w * 0.55f, h * 0.55f)
            lineTo(w * 0.32f, h * 0.68f)
            lineTo(w * 0.45f, h * 0.45f)
            close()
        }
        drawPath(needle, color)
    }
}
