package com.westly.opay.feature.register

import android.app.Activity
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast

private const val WAITING_TITLE = "Your account is under review"
private const val WAITING_TEXT =
    "We will notify you as soon as your account is approved. You can close the app and come back later."
private const val SUSPENDED_TEXT = "Your account is suspended. Please contact support."
private const val APPROVED_TITLE = "Your account has been approved"
private const val LOGOUT_TITLE = "Do you want to log out?"
private const val HOURGLASS_MILLIS = 3200

/**
 * Shown after registration until the owner approves the account. It reads the registration state every few seconds
 * and on every Realtime change while it is on screen. An approved account sees a dialog and then goes to Home;
 * a suspended account sees only a notice and Log out.
 */
@Composable
fun WaitingScreen(
    onNavigate: (String) -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val viewModel = rememberWaitingViewModel()
    val registration = rememberRegistrationViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()
    val lifecycleOwner = LocalLifecycleOwner.current
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.watch() }
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is WaitingEvent.ShowToast -> toast.show(event.message)
                is WaitingEvent.GoTo -> onNavigate(event.route)
            }
        }
    }
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (state.suspended) {
                    Text(
                        text = SUSPENDED_TEXT,
                        style = OPayType.title,
                        color = c.text,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    ReviewContent(
                        checking = state.checking,
                        onCheck = viewModel::checkNow
                    )
                }
            }
            Text(
                text = "Log out",
                style = OPayType.titleSmall,
                fontWeight = FontWeight.Bold,
                color = c.muted,
                modifier = Modifier
                    .clickable { confirmLogout = true }
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            )
        }
        if (state.approved) {
            OPayDialog(
                title = APPROVED_TITLE,
                message = null,
                primaryText = "Continue",
                onPrimary = viewModel::onApprovedContinue,
                onDismiss = {}
            )
        }
        if (confirmLogout) {
            OPayDialog(
                title = LOGOUT_TITLE,
                message = null,
                primaryText = "Log out",
                onPrimary = {
                    confirmLogout = false
                    leaving = true
                    registration.leaveRegistration(onLoggedOut)
                },
                onDismiss = { confirmLogout = false },
                secondaryText = "Cancel",
                onSecondary = { confirmLogout = false }
            )
        }
        if (leaving) BusyOverlay()
        OPayToastHost(state = toast)
    }
}

@Composable
private fun rememberWaitingViewModel(): WaitingViewModel {
    val application = LocalContext.current.applicationContext as Application
    val factory = remember(application) { WaitingViewModel.factory(application) }
    return viewModel<WaitingViewModel>(factory = factory)
}

@Composable
private fun ReviewContent(checking: Boolean, onCheck: () -> Unit) {
    val c = OPay.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Hourglass(modifier = Modifier.size(120.dp))
        Spacer(Modifier.height(24.dp))
        Text(
            text = WAITING_TITLE,
            style = OPayType.headline,
            color = c.text,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = WAITING_TEXT,
            style = OPayType.body,
            color = c.muted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        OPayButton(
            text = "Check status",
            onClick = onCheck,
            modifier = Modifier.width(180.dp),
            style = ButtonStyle.Outline,
            enabled = !checking
        )
    }
}

/** An hourglass drawn on a canvas. The sand runs from the top glass to the bottom glass and starts over. */
@Composable
private fun Hourglass(modifier: Modifier = Modifier) {
    val c = OPay.colors
    val transition = rememberInfiniteTransition(label = "hourglass")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = HOURGLASS_MILLIS, easing = LinearEasing)),
        label = "sand"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centre = w * 0.5f
        val half = w * 0.24f
        val glassTop = h * 0.12f
        val neck = h * 0.5f
        val glassBottom = h * 0.88f
        val stroke = 3.dp.toPx()

        val topGlass = Path().apply {
            moveTo(centre - half, glassTop)
            lineTo(centre + half, glassTop)
            lineTo(centre, neck)
            close()
        }
        val bottomGlass = Path().apply {
            moveTo(centre, neck)
            lineTo(centre + half, glassBottom)
            lineTo(centre - half, glassBottom)
            close()
        }
        drawPath(topGlass, c.card)
        drawPath(bottomGlass, c.card)

        val topSurface = glassTop + (neck - glassTop) * progress
        val topHalf = half * (neck - topSurface) / (neck - glassTop)
        val topSand = Path().apply {
            moveTo(centre - topHalf, topSurface)
            lineTo(centre + topHalf, topSurface)
            lineTo(centre, neck)
            close()
        }
        drawPath(topSand, c.green)

        val pileTop = glassBottom - (glassBottom - neck) * progress
        val pileHalf = half * (pileTop - neck) / (glassBottom - neck)
        val pile = Path().apply {
            moveTo(centre - half, glassBottom)
            lineTo(centre + half, glassBottom)
            lineTo(centre + pileHalf, pileTop)
            lineTo(centre - pileHalf, pileTop)
            close()
        }
        drawPath(pile, c.green)
        drawLine(
            color = c.green,
            start = Offset(centre, neck),
            end = Offset(centre, pileTop),
            strokeWidth = stroke / 2f,
            cap = StrokeCap.Round
        )

        val outline = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawPath(topGlass, c.muted, style = outline)
        drawPath(bottomGlass, c.muted, style = outline)

        val barHeight = h * 0.06f
        val barWidth = w * 0.6f
        val radius = CornerRadius(barHeight / 2f, barHeight / 2f)
        drawRoundRect(
            color = c.muted,
            topLeft = Offset(centre - barWidth / 2f, glassTop - barHeight),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
        )
        drawRoundRect(
            color = c.muted,
            topLeft = Offset(centre - barWidth / 2f, glassBottom),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
        )
    }
}
