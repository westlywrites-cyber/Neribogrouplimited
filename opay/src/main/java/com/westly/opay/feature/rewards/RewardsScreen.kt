package com.westly.opay.feature.rewards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.LightStatusBarIcons
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayTile
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.navigation.Routes
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val NOT_AVAILABLE = ""
private const val SKELETON_MILLIS = 600L

private val HeaderGreen = Color(0xFF0F3A2A)
private val CoinGold = Color(0xFFF5C542)
private val CoinRim = Color(0xFFD9A21B)
private val NewTagPink = Color(0xFFFF4F9A)

private data class Voucher(
    val tab: String,
    val tileText: String,
    val title: String,
    val subtitle: String,
    val route: String
)

private val Vouchers = listOf(
    Voucher("Airtime", "₦10", "Airtime Voucher", "₦500 available", Routes.Airtime),
    Voucher("Data", "₦100", "₦100 Data Voucher", "₦200 available", Routes.Data),
    Voucher("Betting", "₦50", "₦50 Betting Voucher", "₦300 available", Routes.Betting)
)

@Composable
fun RewardsScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LightStatusBarIcons()
    val c = OPay.colors
    val toast = rememberOPayToast()
    var loading by rememberSaveable { mutableStateOf(true) }
    var voucherTab by rememberSaveable { mutableStateOf(0) }

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
                .verticalScroll(rememberScrollState())
        ) {
            RewardsHeader(
                loading = loading,
                onMenu = { toast.show(NOT_AVAILABLE) }
            )
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 16.dp)) {
                if (loading) {
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 84.dp)
                    Spacer(Modifier.height(10.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 130.dp)
                    Spacer(Modifier.height(10.dp))
                    SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 220.dp)
                } else {
                    OPayCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            OPayTile(
                                icon = OPayIcon.Gift,
                                label = "Friday Bonus",
                                onClick = { toast.show(NOT_AVAILABLE) }
                            )
                            OPayTile(
                                icon = OPayIcon.User,
                                label = "Refer Friends",
                                onClick = { onNavigate(Routes.Invite) }
                            )
                            OPayTile(
                                icon = OPayIcon.Game,
                                label = "Play4Child",
                                onClick = { toast.show(NOT_AVAILABLE) }
                            )
                            OPayTile(
                                icon = OPayIcon.Tag,
                                label = "Vouchers",
                                onClick = { toast.show(NOT_AVAILABLE) }
                            )
                        }
                    }
                    HotVouchersCard(
                        selected = voucherTab,
                        onSelect = { voucherTab = it },
                        onUse = onNavigate
                    )
                    DailyBonusCard(onNavigate = onNavigate)
                }
            }
        }
        OPayToastHost(state = toast, bottomPadding = 24.dp)
    }
}

@Composable
private fun RewardsHeader(loading: Boolean, onMenu: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderGreen)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Rewards",
                style = OPayType.headline,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(Color.White.copy(alpha = 0.16f), CircleShape)
                    .semantics { contentDescription = "Menu"; role = Role.Button }
                    .clickable(onClick = onMenu),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(16.dp)) {
                    val r = 1.6.dp.toPx()
                    drawCircle(Color.White, r, Offset(2.dp.toPx(), size.height / 2f))
                    drawCircle(Color.White, r, Offset(size.width / 2f, size.height / 2f))
                    drawCircle(Color.White, r, Offset(size.width - 2.dp.toPx(), size.height / 2f))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        if (loading) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(), height = 52.dp)
        } else {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Cashback", style = OPayType.body, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.width(4.dp))
                        InfoMark()
                        Spacer(Modifier.width(6.dp))
                        Coin(size = 16.dp)
                    }
                    Spacer(Modifier.height(4.dp))
                    HeaderValue(text = "₦ 0.00")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Voucher", style = OPayType.body, color = Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "₦700",
                            style = OPayType.caption,
                            fontWeight = FontWeight.Bold,
                            color = OPay.colors.onGreen,
                            modifier = Modifier
                                .background(OPay.colors.green, RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 1.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    HeaderValue(text = "13")
                }
            }
        }
    }
}

@Composable
private fun HeaderValue(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = OPayType.headline.copy(fontSize = 20.sp),
            color = Color.White
        )
        Spacer(Modifier.width(2.dp))
        OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun InfoMark() {
    Box(
        modifier = Modifier
            .size(13.dp)
            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "i",
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun Coin(size: Dp) {
    Canvas(modifier = Modifier.size(size)) {
        drawCircle(color = CoinRim)
        drawCircle(color = CoinGold, radius = this.size.minDimension * 0.4f)
    }
}

@Composable
private fun HotVouchersCard(
    selected: Int,
    onSelect: (Int) -> Unit,
    onUse: (String) -> Unit
) {
    val c = OPay.colors
    val voucher = Vouchers[selected]
    OPayCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Hot Vouchers",
                style = OPayType.title,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "NEW",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .background(NewTagPink, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Vouchers.forEachIndexed { index, item ->
                val active = index == selected
                Column(
                    modifier = Modifier.clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = item.tab,
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
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(c.greenSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = voucher.tileText,
                    style = OPayType.title,
                    fontWeight = FontWeight.Bold,
                    color = c.green
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = voucher.title,
                    style = OPayType.body,
                    fontWeight = FontWeight.Bold,
                    color = c.text
                )
                Text(text = voucher.subtitle, style = OPayType.caption, color = c.muted)
            }
            GreenPill(text = "Use", onClick = { onUse(voucher.route) })
        }
    }
}

@Composable
private fun DailyBonusCard(onNavigate: (String) -> Unit) {
    val c = OPay.colors
    OPayCard {
        Text(
            text = "Daily Bonus",
            style = OPayType.title,
            fontWeight = FontWeight.Bold,
            color = c.text
        )
        Spacer(Modifier.height(6.dp))
        BonusRow(
            icon = OPayIcon.Gift,
            title = "Share OPay",
            tag = "+200",
            subtitle = "Help a loved one get an account and get ₦6,300",
            onGo = { onNavigate(Routes.Invite) }
        )
        BonusRow(
            icon = OPayIcon.Phone,
            title = "Glo Airtime",
            tag = "+Up to 6%",
            subtitle = "Buy Airtime and get up to 6% Cashback",
            onGo = { onNavigate(Routes.Airtime) }
        )
        BonusRow(
            icon = OPayIcon.Data,
            title = "9 Mobile",
            tag = "+Up to 5%",
            subtitle = "Buy Data and get cashback",
            onGo = { onNavigate(Routes.Data) }
        )
    }
}

@Composable
private fun BonusRow(
    icon: OPayIcon,
    title: String,
    tag: String,
    subtitle: String,
    onGo: () -> Unit
) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(c.cardAlt, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 20.dp, tint = c.green)
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = OPayType.body,
                    fontWeight = FontWeight.Bold,
                    color = c.text
                )
                Spacer(Modifier.width(6.dp))
                Coin(size = 13.dp)
                Spacer(Modifier.width(3.dp))
                Text(
                    text = tag,
                    style = OPayType.caption,
                    fontWeight = FontWeight.Bold,
                    color = CoinGold
                )
            }
            Text(text = subtitle, style = OPayType.caption, color = c.muted)
        }
        GreenPill(text = "Go", onClick = onGo)
    }
}

@Composable
private fun GreenPill(text: String, onClick: () -> Unit) {
    val c = OPay.colors
    Text(
        text = text,
        style = OPayType.bodySmall,
        fontWeight = FontWeight.Bold,
        color = c.onGreen,
        modifier = Modifier
            .background(c.green, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}
