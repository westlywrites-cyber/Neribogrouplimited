package com.westly.opay.feature.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.LightStatusBarIcons
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.components.OPayToggle
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private val HeaderTop = Color(0xFF0F3A2A)
private val HeaderBottom = Color(0xFF17604A)
private val HeaderMuted = Color(0xFFB7D9CB)
private val BlackCard = Color(0xFF0B0B0B)
private val BlackCardMuted = Color(0xFF9B9B9F)
private val ChipFill = Color(0x26FFFFFF)

private data class SecurityOption(val title: String, val description: String)

private val securityOptions = listOf(
    SecurityOption(
        "New Device Login/Password Change",
        "Enable 2FA for new device logins or password changes."
    ),
    SecurityOption(
        "Transfers to Unfamiliar Accounts",
        "Require extra authentication for transfers to unfamiliar recipients."
    ),
    SecurityOption(
        "Connected to Unsecured Networks",
        "Activate enhanced protection when connected to risky networks."
    ),
    SecurityOption(
        "Login From a New Location",
        "Enable enhanced verification when logging in from an unfamiliar location."
    )
)

private val verificationChips = listOf(
    "Email Verification",
    "SMS Verification",
    "Face ID Verification"
)

private val pleaseNote = listOf(
    "1. After activation, you can cancel at any time.",
    "2. Email verification only works for users who have linked their email to their account.",
    "3. Our system uses AI to intelligently decide when extra verification is needed for a " +
        "transaction, and this may change over time.",
    "4. If charges are incurred for verification services (for example SMS or Face " +
        "Verification) in a given month, they will be debited from your account."
)

private const val ALL_ON_MASK = 15

@Composable
fun SecurityPlusScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LightStatusBarIcons()
    val c = OPay.colors
    val toast = rememberOPayToast()
    var enabledMask by rememberSaveable { mutableStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                SecurityPlusHeader(
                    onBack = onBack,
                    onQa = { toast.show(NOT_AVAILABLE_TOAST) }
                )
                Column(modifier = Modifier.padding(horizontal = 12.dp)) {
                    Spacer(Modifier.height(10.dp))
                    securityOptions.forEachIndexed { index, option ->
                        SecurityOptionCard(
                            option = option,
                            checked = enabledMask and (1 shl index) != 0,
                            onCheckedChange = { on ->
                                enabledMask = if (on) {
                                    enabledMask or (1 shl index)
                                } else {
                                    enabledMask and (1 shl index).inv()
                                }
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Please Note",
                        style = OPayType.body.copy(fontWeight = FontWeight.Bold),
                        color = c.text,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                    Spacer(Modifier.height(6.dp))
                    pleaseNote.forEach { line ->
                        Text(
                            text = line,
                            style = OPayType.caption,
                            color = c.muted,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                OPayButton(
                    text = "Activate All",
                    onClick = { enabledMask = ALL_ON_MASK }
                )
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun SecurityPlusHeader(
    onBack: () -> Unit,
    onQa: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(HeaderTop, HeaderBottom)))
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
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
                text = "Security Plus",
                style = OPayType.title,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Q&A",
                style = OPayType.titleSmall,
                color = HeaderMuted,
                modifier = Modifier.clickable(onClick = onQa)
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Your Account Safety,\nUnder Your Control",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Canvas(modifier = Modifier.size(64.dp)) {
                drawShield(color = ShieldGreen)
                drawShieldCheck(color = HeaderTop)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            verificationChips.forEach { label ->
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .background(ChipFill, OPayShapes.chip)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SecurityOptionCard(
    option: SecurityOption,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(BlackCard, OPayShapes.card)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = OPayType.body.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = option.description,
                style = OPayType.caption,
                color = BlackCardMuted
            )
        }
        OPayToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private val ShieldGreen = Color(0xFF1FE0A4)

internal fun DrawScope.drawShield(color: Color) {
    val w = size.width
    val h = size.height
    val shield = Path().apply {
        moveTo(w * 0.5f, h * 0.04f)
        lineTo(w * 0.9f, h * 0.2f)
        lineTo(w * 0.9f, h * 0.5f)
        cubicTo(w * 0.9f, h * 0.75f, w * 0.7f, h * 0.9f, w * 0.5f, h * 0.97f)
        cubicTo(w * 0.3f, h * 0.9f, w * 0.1f, h * 0.75f, w * 0.1f, h * 0.5f)
        lineTo(w * 0.1f, h * 0.2f)
        close()
    }
    drawPath(path = shield, color = color)
}

private fun DrawScope.drawShieldCheck(color: Color) {
    val w = size.width
    val h = size.height
    val tick = Path().apply {
        moveTo(w * 0.32f, h * 0.5f)
        lineTo(w * 0.45f, h * 0.63f)
        lineTo(w * 0.69f, h * 0.37f)
    }
    drawPath(
        path = tick,
        color = color,
        style = Stroke(width = w * 0.08f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}
