package com.westly.opay.feature.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.westly.opay.core.theme.LightStatusBarIcons
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.OverlayScrim
import com.westly.opay.core.ui.overlay.RemoveWindowDim
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.navigation.Routes
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val CHECK_MILLIS = 3000
private const val CATEGORY_HOLD_MILLIS = 600L
private const val POPUP_DELAY_MILLIS = 350L
private const val NODE_RADIUS_DP = 112f
private const val NODE_SIZE_DP = 46f
private const val NOT_AVAILABLE_TOAST = ""
private const val GUIDE_TIPS =
    "1. Never share your PIN, password or OTP with anyone, even if they say they work " +
        "for OPay.\n\n" +
        "2. Real staff will never ask you to pay a fee or take a loan to verify your " +
        "account.\n\n" +
        "3. If something feels wrong, stop and report it before you send any money."

private val DeepGreen = Color(0xFF0A2F23)
private val PageTop = Color(0xFF0C3A2B)
private val PageBottom = Color(0xFF082219)
private val PanelGreen = Color(0xFF123D2E)
private val PanelMuted = Color(0xFF9DBFB1)
private val GlowGreen = Color(0xFF1FE0A4)
private val PopupInk = Color(0xFF1B1B1B)
private val PopupMuted = Color(0xFF7A7A80)

private object ScamPopupGate {
    var shownThisLaunch = false
}

@Composable
fun SecurityCenterScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LightStatusBarIcons()
    val toast = rememberOPayToast()
    var checking by rememberSaveable { mutableStateOf(true) }
    var popupOpen by rememberSaveable { mutableStateOf(false) }
    var guideOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(checking) {
        if (!checking && !ScamPopupGate.shownThisLaunch) {
            delay(POPUP_DELAY_MILLIS)
            ScamPopupGate.shownThisLaunch = true
            popupOpen = true
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(PageTop, PageBottom)))
    ) {
        if (checking) {
            SecurityCheckingAnimation(onFinished = { checking = false })
        } else {
            SecurityMainPage(
                onBack = onBack,
                onReportScam = { onNavigate(Routes.Help) },
                onNotAvailable = { toast.show(NOT_AVAILABLE_TOAST) },
                onOpenGuide = { guideOpen = true }
            )
        }
        OPayToastHost(state = toast, bottomPadding = 60.dp)
    }

    if (popupOpen) {
        ScamPopup(
            onClose = { popupOpen = false },
            onViewGuide = {
                popupOpen = false
                guideOpen = true
            }
        )
    }
    if (guideOpen) {
        OPayDialog(
            title = "Safety Guide",
            message = GUIDE_TIPS,
            primaryText = "OK",
            onPrimary = { guideOpen = false },
            onDismiss = { guideOpen = false }
        )
    }
}

@Composable
private fun SecurityCheckingAnimation(onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }
    var done by remember { mutableStateOf(false) }
    val categoriesAlpha by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = tween(300),
        label = "categoriesAlpha"
    )

    LaunchedEffect(Unit) {
        progress.animateTo(100f, tween(CHECK_MILLIS, easing = LinearEasing))
        done = true
        delay(CATEGORY_HOLD_MILLIS)
        onFinished()
    }

    val percent = progress.value.toInt().coerceIn(0, 100)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreen)
    ) {
        PerspectiveGrid(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(230.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PentagonShield()
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Security Checking... ($percent%)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OPay.colors.onGreen,
                modifier = Modifier
                    .clip(OPayShapes.pill)
                    .background(GlowGreen)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(28.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(categoriesAlpha)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                CategoryItem(icon = OPayIcon.User, label = "Account Security")
                CategoryItem(icon = OPayIcon.Phone, label = "Environmental Security")
                CategoryItem(icon = OPayIcon.Bell, label = "Security Awareness")
            }
        }
    }
}

@Composable
private fun CategoryItem(icon: OPayIcon, label: String) {
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(PanelGreen)
                .border(1.dp, GlowGreen.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 22.dp, tint = GlowGreen)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PentagonShield() {
    val pulseTransition = rememberInfiniteTransition(label = "pulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
        label = "pulseValue"
    )
    Box(
        modifier = Modifier.size(300.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = NODE_RADIUS_DP.dp.toPx()
            val points = (0 until 5).map { index ->
                val angle = nodeAngle(index)
                Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
            }
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlowGreen.copy(alpha = 0.45f * pulse), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
            points.forEach { point ->
                drawLine(
                    color = Color.White.copy(alpha = 0.15f),
                    start = center,
                    end = point,
                    strokeWidth = 1.dp.toPx()
                )
            }
            for (index in 0 until 5) {
                drawLine(
                    color = Color.White.copy(alpha = 0.35f),
                    start = points[index],
                    end = points[(index + 1) % 5],
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
        Canvas(modifier = Modifier.size(96.dp)) {
            drawShieldBody(GlowGreen)
            drawShieldTick(DeepGreen)
        }
        for (index in 0 until 5) {
            val angle = nodeAngle(index)
            Box(
                modifier = Modifier
                    .offset(
                        x = (NODE_RADIUS_DP * cos(angle)).dp,
                        y = (NODE_RADIUS_DP * sin(angle)).dp
                    )
                    .size(NODE_SIZE_DP.dp)
                    .clip(CircleShape)
                    .background(PanelGreen)
                    .border(1.dp, GlowGreen.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when (index) {
                    0 -> OPayIconView(icon = OPayIcon.Card, size = 22.dp, tint = GlowGreen)
                    1 -> OPayIconView(icon = OPayIcon.Lock, size = 22.dp, tint = GlowGreen)
                    2 -> OPayIconView(icon = OPayIcon.Phone, size = 22.dp, tint = GlowGreen)
                    3 -> FingerprintGlyph(tint = GlowGreen, glyphSize = 24.dp)
                    else -> OPayIconView(icon = OPayIcon.Loan, size = 22.dp, tint = GlowGreen)
                }
            }
        }
    }
}

private fun nodeAngle(index: Int): Float = (-90f + 72f * index) * PI.toFloat() / 180f

@Composable
private fun FingerprintGlyph(tint: Color, glyphSize: Dp) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val side = this.size.minDimension
        val center = Offset(side / 2f, side / 2f)
        val stroke = Stroke(width = side * 0.09f, cap = StrokeCap.Round)
        listOf(0.16f, 0.30f, 0.44f).forEach { fraction ->
            val r = side * fraction
            drawArc(
                color = tint,
                startAngle = -200f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(center.x - r, center.y - r),
                size = Size(r * 2f, r * 2f),
                style = stroke
            )
        }
        drawCircle(color = tint, radius = side * 0.05f, center = center)
    }
}

@Composable
private fun PerspectiveGrid(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val line = Color.White.copy(alpha = 0.22f)
        val w = size.width
        val h = size.height
        val stroke = 1.dp.toPx()
        val rows = 7
        for (row in 1..rows) {
            val t = row / rows.toFloat()
            val y = h * t * t
            drawLine(line, Offset(0f, y), Offset(w, y), stroke)
        }
        for (col in -7..7) {
            drawLine(line, Offset(w / 2f, 0f), Offset(w / 2f + col * (w / 6f), h), stroke)
        }
    }
}

@Composable
private fun SecurityMainPage(
    onBack: () -> Unit,
    onReportScam: () -> Unit,
    onNotAvailable: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
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
                text = "Security Center",
                fontSize = 17.sp,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "Notifications"; role = Role.Button }
                    .clickable(onClick = onNotAvailable),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Bell, size = 22.dp, tint = Color.White)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            StatusHeader()
            PanelCard {
                Row(modifier = Modifier.fillMaxWidth()) {
                    ShortcutTile(
                        icon = OPayIcon.Lock,
                        label = "Transaction Protection",
                        onClick = onNotAvailable,
                        modifier = Modifier.weight(1f)
                    )
                    ShortcutTile(
                        icon = OPayIcon.Key,
                        label = "Reset PIN",
                        onClick = onNotAvailable,
                        modifier = Modifier.weight(1f)
                    )
                    ShortcutTile(
                        icon = OPayIcon.Chat,
                        label = "Report Scam",
                        onClick = onReportScam,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            PanelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Necessary Actions After Operation",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    UncompletedTag()
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "After operating OPay, protecting password security should not be " +
                        "underestimated",
                    fontSize = 12.sp,
                    color = PanelMuted
                )
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    GreenPill(text = "Take Test", onClick = onOpenGuide)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Security Guide",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "More >",
                    fontSize = 12.sp,
                    color = PanelMuted,
                    modifier = Modifier.clickable(onClick = onOpenGuide)
                )
            }
            GuideCard(
                icon = OPayIcon.Gift,
                title = "Love or Scam?",
                line = "Be wary of the \"Sweet Trap\" in online romance",
                onAccess = onOpenGuide
            )
            GuideCard(
                icon = OPayIcon.Phone,
                title = "Beware of \"Old Friends\" Calling",
                line = "How to Spot the Scam of Pretending to be an Old Friend!",
                onAccess = onOpenGuide
            )
            PanelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Security Test",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "Can you be easily scammed? Click here to find out",
                            fontSize = 12.sp,
                            color = PanelMuted
                        )
                    }
                    Text(
                        text = "More",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = GlowGreen,
                        modifier = Modifier.clickable(onClick = onOpenGuide)
                    )
                }
            }
            PanelCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Password Security is No Small Matter",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    UncompletedTag()
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Take action now to protect your OPay account.",
                    fontSize = 12.sp,
                    color = PanelMuted
                )
            }
            Spacer(Modifier.height(16.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun StatusHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(76.dp)) {
            drawShieldBody(GlowGreen)
            drawShieldTick(DeepGreen)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Complete the tasks below to protect your account.",
            fontSize = 12.sp,
            color = PanelMuted,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PanelCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(PanelGreen, OPayShapes.card)
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun ShortcutTile(
    icon: OPayIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(OPayShapes.tile)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.12f), OPayShapes.tile),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 22.dp, tint = GlowGreen)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GuideCard(
    icon: OPayIcon,
    title: String,
    line: String,
    onAccess: () -> Unit
) {
    PanelCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(OPayShapes.card)
                    .background(Brush.linearGradient(listOf(Color(0xFF1D7A5A), Color(0xFF0F3A2A)))),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = icon, size = 32.dp, tint = Color.White)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(Modifier.height(3.dp))
                Text(text = line, fontSize = 12.sp, color = PanelMuted)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    UncompletedTag()
                    Spacer(Modifier.weight(1f))
                    GreenPill(text = "Access", onClick = onAccess)
                }
            }
        }
    }
}

@Composable
private fun UncompletedTag() {
    Text(
        text = "Uncompleted",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(OPayShapes.chip)
            .background(OPay.colors.danger)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun GreenPill(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = OPay.colors.onGreen,
        modifier = Modifier
            .clip(OPayShapes.pill)
            .background(GlowGreen)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun ScamPopup(
    onClose: () -> Unit,
    onViewGuide: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        RemoveWindowDim()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OverlayScrim),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.84f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(OPayShapes.card)
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OPayIconView(icon = OPayIcon.Loan, size = 16.dp, tint = PageTop)
                        Text(
                            text = "OPay Security Center",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = PageTop
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    ScammerCaricature(
                        modifier = Modifier
                            .width(170.dp)
                            .height(120.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Need a Loan to Verify Your Account? It's a Scam!",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = PopupInk,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Scammers posing as staff claim to fix your account — but all " +
                            "they want is your money.",
                        fontSize = 13.sp,
                        color = PopupMuted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "View Safety Guide",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(OPayShapes.pill)
                            .background(Color(0xFF111111))
                            .clickable(onClick = onViewGuide)
                            .padding(vertical = 13.dp)
                    )
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color.White, CircleShape)
                        .semantics { contentDescription = "Close"; role = Role.Button }
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Close, size = 18.dp, tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ScammerCaricature(modifier: Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawCircle(
            color = Color(0xFFD9F5E9),
            radius = h * 0.48f,
            center = Offset(w * 0.5f, h * 0.52f)
        )
        drawRoundRect(
            color = Color(0xFFE0A526),
            topLeft = Offset(w * 0.77f, h * 0.58f),
            size = Size(w * 0.06f, h * 0.10f),
            cornerRadius = CornerRadius(h * 0.02f)
        )
        drawCircle(
            color = Color(0xFFE0A526),
            radius = h * 0.17f,
            center = Offset(w * 0.80f, h * 0.80f)
        )
        drawRoundRect(
            color = Color(0xFF263238),
            topLeft = Offset(w * 0.32f, h * 0.62f),
            size = Size(w * 0.36f, h * 0.38f),
            cornerRadius = CornerRadius(h * 0.12f)
        )
        drawCircle(
            color = Color(0xFF263238),
            radius = h * 0.30f,
            center = Offset(w * 0.5f, h * 0.38f)
        )
        drawCircle(
            color = Color(0xFFF2C7A5),
            radius = h * 0.22f,
            center = Offset(w * 0.5f, h * 0.42f)
        )
        drawRoundRect(
            color = Color(0xFF111111),
            topLeft = Offset(w * 0.38f, h * 0.34f),
            size = Size(w * 0.24f, h * 0.10f),
            cornerRadius = CornerRadius(h * 0.05f)
        )
        drawCircle(Color.White, h * 0.025f, Offset(w * 0.45f, h * 0.39f))
        drawCircle(Color.White, h * 0.025f, Offset(w * 0.55f, h * 0.39f))
        drawArc(
            color = Color(0xFF8A3B2E),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.43f, h * 0.46f),
            size = Size(w * 0.14f, h * 0.10f),
            style = Stroke(width = h * 0.02f, cap = StrokeCap.Round)
        )
        drawRoundRect(
            color = Color(0xFF455A64),
            topLeft = Offset(w * 0.17f, h * 0.60f),
            size = Size(w * 0.10f, h * 0.18f),
            cornerRadius = CornerRadius(h * 0.02f)
        )
    }
}

private fun DrawScope.drawShieldBody(color: Color) {
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

private fun DrawScope.drawShieldTick(color: Color) {
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
