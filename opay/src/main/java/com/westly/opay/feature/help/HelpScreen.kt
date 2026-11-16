package com.westly.opay.feature.help

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.LightStatusBarIcons
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTile
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.navigation.Routes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val NOT_AVAILABLE_TOAST = ""
private const val NO_TOPIC = ""

private val HeaderTop = Color(0xFF0F3A2A)
private val HeaderBottom = Color(0xFF17604A)
private val HeaderMuted = Color(0xFFB7D9CB)

private data class HelpTile(val icon: OPayIcon, val label: String, val route: String?)

private val helpTiles = listOf(
    HelpTile(OPayIcon.Doc, "Transaction Disputes", null),
    HelpTile(OPayIcon.Zap, "Erroneous Transfer", null),
    HelpTile(OPayIcon.Clock, "Account Limits", Routes.AccountLimits),
    HelpTile(OPayIcon.Lock, "Password & PIN", null),
    HelpTile(OPayIcon.Phone, "Phone No. Change", null),
    HelpTile(OPayIcon.Loan, "Report Scam", null),
    HelpTile(OPayIcon.Chat, "SMS Alert Settings", Routes.SmsAlerts),
    HelpTile(OPayIcon.List, "More", null)
)

private val helpTabs = listOf("Hot Issues", "Transaction", "Account")

private val helpLists = listOf(
    listOf("Office Address", "How to increase the limit?", "How to change mobile number?"),
    listOf("Successful but not credited", "Erroneous transfer", "Transfer limit"),
    listOf("Reset password", "Change phone number", "Close account")
)

@Composable
fun HelpScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LightStatusBarIcons()
    val c = OPay.colors
    val toast = rememberOPayToast()
    var tab by rememberSaveable { mutableStateOf(0) }
    var topic by rememberSaveable { mutableStateOf(NO_TOPIC) }
    var chatOpen by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HelpHeader(
                onBack = onBack,
                onMenu = { toast.show(NOT_AVAILABLE_TOAST) }
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                OPayCard {
                    helpTiles.chunked(4).forEachIndexed { rowIndex, rowTiles ->
                        if (rowIndex > 0) Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowTiles.forEach { tile ->
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    OPayTile(
                                        icon = tile.icon,
                                        label = tile.label,
                                        onClick = {
                                            if (tile.route != null) onNavigate(tile.route)
                                            else topic = tile.label
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                OPayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "My Feedback",
                            style = OPayType.body.copy(fontWeight = FontWeight.Bold),
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "More >",
                            style = OPayType.caption,
                            color = c.muted,
                            modifier = Modifier.clickable { toast.show(NOT_AVAILABLE_TOAST) }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Successful but not credited",
                        style = OPayType.body,
                        color = c.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { topic = "Successful but not credited" }
                            .padding(vertical = 4.dp)
                    )
                }
                OPayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Help you may need",
                            style = OPayType.body.copy(fontWeight = FontWeight.Bold),
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Change ⟳",
                            style = OPayType.caption,
                            color = c.muted,
                            modifier = Modifier.clickable { toast.show(NOT_AVAILABLE_TOAST) }
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        helpTabs.forEachIndexed { index, label ->
                            HelpTab(
                                label = label,
                                selected = tab == index,
                                onClick = { tab = index },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OPayRowDivider()
                    helpLists[tab].forEachIndexed { index, title ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { topic = title }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = OPayType.body.copy(fontWeight = FontWeight.Bold),
                                color = c.green
                            )
                            Text(
                                text = title,
                                style = OPayType.body,
                                color = c.text,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(80.dp))
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .clip(OPayShapes.pill)
                .background(c.green)
                .clickable { chatOpen = true }
                .padding(horizontal = 26.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OPayIconView(icon = OPayIcon.Chat, size = 18.dp, tint = c.onGreen)
            Text(
                text = "Live Chat",
                style = OPayType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                color = c.onGreen
            )
        }
        if (chatOpen) {
            LiveChatOverlay(onClose = { chatOpen = false })
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }

    if (topic != NO_TOPIC) {
        OPayDialog(
            title = "Help topic",
            message = helpText(topic),
            primaryText = "OK",
            onPrimary = { topic = NO_TOPIC },
            onDismiss = { topic = NO_TOPIC }
        )
    }
}

@Composable
private fun HelpHeader(
    onBack: () -> Unit,
    onMenu: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HeaderTop, HeaderBottom)))
            .statusBarsPadding()
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
            OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Customer Service Center",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "7*24h service for you",
                fontSize = 12.sp,
                color = HeaderMuted
            )
        }
        AgentIllustration(modifier = Modifier.size(60.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Menu"; role = Role.Button }
                .clickable(onClick = onMenu),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(width = 6.dp, height = 20.dp)) {
                val r = size.width / 2f
                for (i in 0..2) {
                    drawCircle(
                        color = Color.White,
                        radius = r,
                        center = Offset(size.width / 2f, r + i * (size.height - 2f * r) / 2f)
                    )
                }
            }
        }
    }
}

@Composable
private fun AgentIllustration(modifier: Modifier) {
    Canvas(modifier = modifier.clip(CircleShape)) {
        val s = size.minDimension
        val mid = s / 2f
        val ink = Color(0xFF111111)
        drawCircle(color = Color(0xFFBFEBD9), radius = mid, center = Offset(mid, mid))
        drawCircle(
            color = Color(0xFF1D7A5A),
            radius = s * 0.30f,
            center = Offset(mid, s * 0.95f)
        )
        drawCircle(
            color = Color(0xFFF2C7A5),
            radius = s * 0.20f,
            center = Offset(mid, s * 0.46f)
        )
        drawArc(
            color = Color(0xFF2E2E2E),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(mid - s * 0.21f, s * 0.46f - s * 0.21f),
            size = Size(s * 0.42f, s * 0.42f)
        )
        drawArc(
            color = ink,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(mid - s * 0.26f, s * 0.46f - s * 0.26f),
            size = Size(s * 0.52f, s * 0.52f),
            style = Stroke(width = s * 0.05f)
        )
        drawCircle(color = ink, radius = s * 0.06f, center = Offset(mid - s * 0.26f, s * 0.46f))
        drawCircle(color = ink, radius = s * 0.06f, center = Offset(mid + s * 0.26f, s * 0.46f))
        drawLine(
            color = ink,
            start = Offset(mid + s * 0.26f, s * 0.50f),
            end = Offset(mid + s * 0.10f, s * 0.64f),
            strokeWidth = s * 0.03f,
            cap = StrokeCap.Round
        )
        drawCircle(color = ink, radius = s * 0.035f, center = Offset(mid + s * 0.10f, s * 0.64f))
    }
}

@Composable
private fun HelpTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = OPayType.body.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            ),
            color = if (selected) c.text else c.muted,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(2.dp)
                .background(if (selected) c.green else Color.Transparent)
        )
    }
}

private fun helpText(topic: String): String = when (topic.lowercase()) {
    "transaction disputes" ->
        "If a transaction looks wrong, open it from your transaction history and check its " +
            "status and number. In this demo no dispute is sent to anyone."
    "erroneous transfer" ->
        "If you sent money to the wrong account, contact the receiving bank and keep your " +
            "receipt. In this demo no money can be recalled."
    "password & pin" ->
        "Keep your password and PIN private and change them if you think someone knows them. " +
            "In this demo they cannot be reset."
    "phone no. change", "how to change mobile number?", "change phone number" ->
        "Changing your phone number needs identity checks in a real banking app. In this " +
            "demo your number stays the same."
    "report scam" ->
        "Never share your PIN, password or OTP, and stop if someone asks for money to verify " +
            "your account. In this demo no report is sent."
    "more" ->
        "More help topics would appear here in a real banking app. In this demo only the " +
            "topics on this page are shown."
    "office address" ->
        "A real banking app would show its office address here. This demo has no real office."
    "how to increase the limit?" ->
        "Account limits depend on your verification tier. You can see the sample limits under " +
            "Account Limits in this demo."
    "successful but not credited" ->
        "If a transfer shows successful but the receiver cannot see it, ask them to check " +
            "again after a few minutes. In this demo no real transfer is made."
    "transfer limit" ->
        "Transfer limits depend on your account tier. In this demo the limits are fixed " +
            "sample values."
    "reset password" ->
        "A real banking app lets you reset your password after identity checks. In this " +
            "demo passwords cannot be reset."
    "close account" ->
        "Closing an account needs identity checks in a real banking app. In this demo no " +
            "account is ever closed."
    else ->
        "This help topic explains $topic. In this demo no real support request is created."
}
