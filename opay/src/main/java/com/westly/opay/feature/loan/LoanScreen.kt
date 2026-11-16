package com.westly.opay.feature.loan

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.feature.bills.NOT_AVAILABLE_MESSAGE
import com.westly.opay.navigation.Routes
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val SKELETON_MILLIS = 500L
private const val DEMO_NOTICE = "Demo content: no loans are offered in this app."

private val GradientStart = Color(0xFF2EEAAA)
private val GradientEnd = Color(0xFF22D79C)
private val Ink = Color(0xFF06271B)
private val PlusCard = Color(0xFF17402F)
private val PlusMuted = Color(0xFFB7D9CC)
private val AboutCard = Color(0xFF1E2B26)
private val NewTag = Color(0xFFFF4D8D)

private const val FAQ_TITLE = "Frequently Ask Questions"
private const val GLANCE_TITLE = "Easemoni At A Glance"

private val FaqBullets = listOf(
    "You choose an amount within your limit and send a request in the app.",
    "If the request is approved, the money is paid into your account within minutes.",
    "You pay back the amount, plus any fee shown to you, by the due date."
)

private val GlanceBullets = listOf(
    "Your limit is the most you can borrow at one time.",
    "Each loan has a fixed length and a clear repayment date.",
    "Paying on time can help your limit grow over time."
)

@Composable
fun LoanScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val toast = rememberOPayToast()

    var loading by remember { mutableStateOf(true) }
    var dialogIndex by rememberSaveable { mutableStateOf(-1) }

    LaunchedEffect(Unit) {
        delay(SKELETON_MILLIS)
        loading = false
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
            LoanHeader(onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                if (loading) {
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        height = 150.dp
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        height = 220.dp
                    )
                    SkeletonBlock(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        height = 140.dp
                    )
                } else {
                    EaseMoniCard(onGetLimit = { toast.show(NOT_AVAILABLE_MESSAGE) })
                    EaseMoniPlusCard(
                        onUnlock = { toast.show(NOT_AVAILABLE_MESSAGE) },
                        onAirtime = { onNavigate(Routes.Airtime) },
                        onData = { onNavigate(Routes.Data) },
                        onTv = { onNavigate(Routes.Tv) },
                        onElectricity = { toast.show(NOT_AVAILABLE_MESSAGE) }
                    )
                    Text(
                        text = "About EaseMoni",
                        style = OPayType.title,
                        fontWeight = FontWeight.Bold,
                        color = c.text,
                        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                    )
                    AboutCardContent()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AboutTile(
                            title = FAQ_TITLE,
                            onClick = { dialogIndex = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        AboutTile(
                            title = GLANCE_TITLE,
                            onClick = { dialogIndex = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
        if (dialogIndex >= 0) {
            val title = if (dialogIndex == 0) FAQ_TITLE else GLANCE_TITLE
            val bullets = if (dialogIndex == 0) FaqBullets else GlanceBullets
            OPayDialog(
                title = title,
                message = bullets.joinToString(separator = "\n") { "\u2022 $it" } +
                    "\n\n" + DEMO_NOTICE,
                primaryText = "Close",
                onPrimary = { dialogIndex = -1 },
                onDismiss = { dialogIndex = -1 }
            )
        }
    }
}

@Composable
private fun LoanHeader(onBack: () -> Unit) {
    val c = OPay.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .semantics { contentDescription = "Back"; role = Role.Button }
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
        }
        Text(
            text = "Loan",
            style = OPayType.title,
            color = c.text,
            modifier = Modifier.padding(start = 8.dp)
        )
        Box(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .width(1.dp)
                .height(24.dp)
                .background(c.divider)
        )
        Column {
            Text(
                text = "OPay",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = c.green
            )
            Text(
                text = "Powered by OPay MFB Ltd.",
                style = OPayType.captionSmall,
                color = c.muted
            )
        }
    }
}

@Composable
private fun EaseMoniCard(onGetLimit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Ink),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Loan, size = 16.dp, tint = GradientStart)
            }
            Text(
                text = "EaseMoni",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "New",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .clip(OPayShapes.chip)
                    .background(NewTag)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        Text(
            text = "Cash loan in 1 minute",
            style = OPayType.bodySmall,
            color = Ink,
            modifier = Modifier.padding(top = 10.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Limit up to(\u20A6)",
                    style = OPayType.caption,
                    color = Ink
                )
                Text(
                    text = "3,000,000.00",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink
                )
            }
            Text(
                text = "Get Limit",
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier
                    .clip(OPayShapes.pill)
                    .background(Color.White)
                    .clickable(onClick = onGetLimit)
                    .padding(horizontal = 20.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun EaseMoniPlusCard(
    onUnlock: () -> Unit,
    onAirtime: () -> Unit,
    onData: () -> Unit,
    onTv: () -> Unit,
    onElectricity: () -> Unit
) {
    val c = OPay.colors
    OPayCard(background = PlusCard) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EaseMoni+",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Buy now pay later",
                    style = OPayType.caption,
                    color = PlusMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = "Unlock",
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = c.onGreen,
                modifier = Modifier
                    .clip(OPayShapes.pill)
                    .background(c.green)
                    .clickable(onClick = onUnlock)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        Text(
            text = "Max Credit limit  50,000.00",
            style = OPayType.bodySmall,
            color = Color.White,
            modifier = Modifier.padding(top = 10.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(OPayShapes.tile)
                .background(c.greenSoft)
                .padding(12.dp)
        ) {
            Text(
                text = "Where You Can Use It",
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                UseTile(
                    icon = OPayIcon.Phone,
                    label = "Airtime",
                    onClick = onAirtime,
                    modifier = Modifier.weight(1f)
                )
                UseTile(
                    icon = OPayIcon.Data,
                    label = "Data",
                    onClick = onData,
                    modifier = Modifier.weight(1f)
                )
                UseTile(
                    icon = OPayIcon.Tv,
                    label = "TV",
                    onClick = onTv,
                    modifier = Modifier.weight(1f)
                )
                UseTile(
                    icon = OPayIcon.Zap,
                    label = "Electricity",
                    onClick = onElectricity,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UseTile(
    icon: OPayIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Column(
        modifier = modifier
            .clip(OPayShapes.tile)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(OPayShapes.tile)
                .background(PlusCard),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 22.dp, tint = c.green)
        }
        Text(
            text = label,
            style = OPayType.caption,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Composable
private fun AboutCardContent() {
    val c = OPay.colors
    OPayCard(background = AboutCard) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                AboutLine(
                    icon = OPayIcon.Clock,
                    text = "Company Incorporation: 26/03/2009",
                    tint = c.green
                )
                AboutLine(
                    icon = OPayIcon.User,
                    text = "Has Served Over 30,000,000 Users",
                    tint = c.green
                )
            }
            ShieldIllustration(modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun AboutLine(icon: OPayIcon, text: String, tint: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OPayIconView(icon = icon, size = 16.dp, tint = tint)
        Text(text = text, style = OPayType.bodySmall, color = Color.White)
    }
}

@Composable
private fun AboutTile(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    Column(
        modifier = modifier
            .clip(OPayShapes.card)
            .background(AboutCard)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            style = OPayType.bodySmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Learn More",
            style = OPayType.caption,
            color = c.green,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun ShieldIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 64.dp, height = 72.dp)) {
        val w = size.width
        val h = size.height
        val shield = Path().apply {
            moveTo(w * 0.5f, 0f)
            lineTo(w, h * 0.16f)
            lineTo(w, h * 0.5f)
            cubicTo(w, h * 0.78f, w * 0.74f, h * 0.92f, w * 0.5f, h)
            cubicTo(w * 0.26f, h * 0.92f, 0f, h * 0.78f, 0f, h * 0.5f)
            lineTo(0f, h * 0.16f)
            close()
        }
        drawPath(
            path = shield,
            brush = Brush.verticalGradient(listOf(GradientStart, Color(0xFF1D7A5A)))
        )
        val tick = Path().apply {
            moveTo(w * 0.28f, h * 0.5f)
            lineTo(w * 0.44f, h * 0.65f)
            lineTo(w * 0.74f, h * 0.34f)
        }
        drawPath(
            path = tick,
            color = Color.White,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
