package com.westly.opay.feature.me

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.backend.SecureKeys
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRingAvatar
import com.westly.opay.core.ui.components.OPayRow
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTile
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.DailyTotals
import com.westly.opay.feature.login.LoginFormat
import com.westly.opay.navigation.Routes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private val TierChipBackground = Color(0xFF105E48)
private val TierChipText = Color(0xFF3FE0B0)
private val AssistantCard = Color(0xFF123D2E)
private val AssistantChip = Color(0xFF2A5A47)
private val CashNowTag = Color(0xFFFF4D8D)

@Composable
fun MeScreen(
    onNavigate: (String) -> Unit,
    onSignedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val signedInName = remember {
        val store = (context.applicationContext as OPayApplication).backend.secureStore
        LoginFormat.displayName(store.getString(SecureKeys.REMEMBERED_NAME_MASKED))
    }
    val profile by repository.profile.collectAsStateWithLifecycle()
    val balanceKobo by repository.balanceKobo.collectAsStateWithLifecycle()
    val hideBalance by repository.hideBalance.collectAsStateWithLifecycle()
    val transactions by repository.transactions.collectAsStateWithLifecycle()
    val earnedTodayKobo = remember(transactions) {
        DailyTotals.interestToday(transactions, System.currentTimeMillis())
    }
    var signOutOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OPayRingAvatar(size = 34.dp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = (signedInName ?: profile.nickname) + " \u203A",
                        fontSize = 17.sp,
                        color = c.text,
                        modifier = Modifier.clickable { onNavigate(Routes.Profile) }
                    )
                    TierChip(
                        tier = profile.tier,
                        modifier = Modifier.padding(top = 3.dp),
                        onClick = { onNavigate(Routes.AccountLimits) }
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Help"; role = Role.Button }
                        .clickable { onNavigate(Routes.Help) },
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Support, size = 22.dp, tint = c.text.copy(alpha = 0.82f))
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .semantics { contentDescription = "Settings"; role = Role.Button }
                        .clickable { onNavigate(Routes.Settings) },
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.SettingsHex, size = 22.dp, tint = c.text.copy(alpha = 0.82f))
                }
            }
            OPayCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Balance",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = c.text
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(28.dp)
                            .semantics { contentDescription = if (hideBalance) "Show balance" else "Hide balance"; role = Role.Button }
                            .clickable { repository.setHideBalance(!hideBalance) },
                        contentAlignment = Alignment.Center
                    ) {
                        EyeIcon(hidden = hideBalance, tint = c.muted)
                    }
                    Text(
                        text = if (hideBalance) "****" else formatNaira(balanceKobo),
                        style = OPayType.title,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text(
                    text = "Earned Today +" + (if (hideBalance) "\u20A6****" else formatNaira(earnedTodayKobo)) + " \u203A",
                    style = OPayType.caption,
                    color = c.muted,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            OPayCard {
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        OPayTile(
                            icon = OPayIcon.Doc,
                            label = "Transactions",
                            onClick = { onNavigate(Routes.Transactions) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        OPayTile(
                            icon = OPayIcon.Clock,
                            label = "Account Limits",
                            onClick = { onNavigate(Routes.AccountLimits) }
                        )
                    }
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                        OPayTile(
                            icon = OPayIcon.Loan,
                            label = "Security Center",
                            onClick = { onNavigate(Routes.SecurityCenter) }
                        )
                    }
                }
            }
            OPayCard {
                OPayRow(
                    title = "My Loan",
                    onClick = { onNavigate(Routes.Loan) },
                    icon = OPayIcon.Loan,
                    trailing = {
                        Text(
                            text = "Cash now",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .clip(OPayShapes.chip)
                                .background(CashNowTag)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                )
                OPayRowDivider()
                OPayRow(
                    title = "Bank Card/Account",
                    onClick = { onNavigate(Routes.BankCards) },
                    icon = OPayIcon.Card
                )
                OPayRowDivider()
                OPayRow(
                    title = "My BizPayment",
                    onClick = { onNavigate(Routes.BizPayment) },
                    icon = OPayIcon.Bag
                )
                OPayRowDivider()
                OPayRow(
                    title = "OJunior",
                    onClick = { onNavigate(Routes.Ojunior) },
                    icon = OPayIcon.User
                )
                OPayRowDivider()
                OPayRow(
                    title = "OPay USSD",
                    onClick = { onNavigate(Routes.Ussd) },
                    icon = OPayIcon.Phone
                )
            }
            OPayCard(background = AssistantCard) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OPay Smart Assistant",
                        style = OPayType.body,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(AssistantChip)
                            .clickable { onNavigate(Routes.Help) },
                        contentAlignment = Alignment.Center
                    ) {
                        OPayIconView(icon = OPayIcon.Forward, size = 14.dp, tint = Color.White)
                    }
                }
                AssistantChipView(
                    text = "Successful but not credited",
                    onClick = { onNavigate(Routes.Help) },
                    modifier = Modifier.padding(top = 10.dp)
                )
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistantChipView(
                        text = "Erroneous Transfer",
                        onClick = { onNavigate(Routes.Help) }
                    )
                    AssistantChipView(
                        text = "Internet Fraud",
                        onClick = { onNavigate(Routes.Help) }
                    )
                }
            }
            OPayCard {
                OPayRow(
                    title = "Invitation",
                    onClick = { onNavigate(Routes.Invite) },
                    icon = OPayIcon.Gift,
                    value = "Earn up to \u20A66,300 Bonus"
                )
                OPayRowDivider()
                OPayRow(
                    title = "Profile Info",
                    onClick = { onNavigate(Routes.Profile) },
                    icon = OPayIcon.User,
                    value = "Name/NIN/BVN"
                )
                OPayRowDivider()
                OPayRow(
                    title = "Settings",
                    onClick = { onNavigate(Routes.Settings) },
                    icon = OPayIcon.Game,
                    value = "Password/Biometrics"
                )
            }
            OPayCard {
                Text(
                    text = "Sign Out",
                    style = OPayType.title,
                    color = c.danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { signOutOpen = true }
                        .padding(vertical = 6.dp)
                )
            }
        }
        if (signOutOpen) {
            OPayDialog(
                title = "Are you sure to log out of OPay?",
                message = null,
                primaryText = "Yes",
                onPrimary = {
                    signOutOpen = false
                    onSignedOut()
                },
                onDismiss = { signOutOpen = false },
                secondaryText = "No",
                onSecondary = { signOutOpen = false }
            )
        }
    }
}

@Composable
internal fun TierChip(tier: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Text(
        text = "Tier $tier",
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        color = TierChipText,
        modifier = modifier
            .clip(OPayShapes.pill)
            .background(TierChipBackground)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun AssistantChipView(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = OPayType.caption,
        color = Color.White,
        modifier = modifier
            .clip(OPayShapes.chip)
            .background(AssistantChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    )
}

@Composable
internal fun EyeIcon(hidden: Boolean, tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val unit = size.minDimension / 24f
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round)
        val eye = Path().apply {
            moveTo(2f * unit, 12f * unit)
            quadraticBezierTo(12f * unit, 2f * unit, 22f * unit, 12f * unit)
            quadraticBezierTo(12f * unit, 22f * unit, 2f * unit, 12f * unit)
            close()
        }
        drawPath(path = eye, color = tint, style = stroke)
        drawCircle(
            color = tint,
            radius = 3f * unit,
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
