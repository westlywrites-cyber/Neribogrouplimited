package com.westly.opay.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
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
import com.westly.opay.core.ui.overlay.NumericKeypad
import com.westly.opay.core.ui.overlay.OPayDialog
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val OTP_LENGTH = 6
private const val RESEND_SECONDS = 58
private const val PILL_MILLIS = 2000L
private const val OTP_PHONE_HINT = "Please check the OTP that has been sent to your phone number +234801****678."

@Composable
internal fun OtpVerifyOverlay(
    onClose: () -> Unit,
    onVerified: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    var code by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableIntStateOf(RESEND_SECONDS) }
    var pillKey by remember { mutableIntStateOf(0) }
    var pillVisible by remember { mutableStateOf(true) }
    var exitOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            if (secondsLeft > 0) secondsLeft -= 1
        }
    }
    LaunchedEffect(pillKey) {
        pillVisible = true
        delay(PILL_MILLIS)
        pillVisible = false
    }
    BackHandler(enabled = !exitOpen) { exitOpen = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 14.dp)
                    .size(28.dp)
                    .semantics { contentDescription = "Back"; role = Role.Button }
                    .clickable { exitOpen = true },
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = "Verify Phone Number",
                    style = OPayType.headline,
                    color = c.text,
                    modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 16.dp)
                )
                OPayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fill in the box below with the OTP",
                                style = OPayType.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = c.text
                            )
                            Text(
                                text = OTP_PHONE_HINT,
                                style = OPayType.caption,
                                color = c.muted,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        PhoneOtpIllustration(tint = c.green)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (index in 0 until OTP_LENGTH) {
                        OtpBox(
                            digit = code.getOrNull(index),
                            active = index == code.length,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(start = 4.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Didn't receive the OTP?",
                        style = OPayType.caption,
                        color = c.muted
                    )
                    if (secondsLeft > 0) {
                        Text(
                            text = "Resend (${secondsLeft}s)",
                            style = OPayType.caption,
                            color = c.muted
                        )
                    } else {
                        Text(
                            text = "Resend",
                            style = OPayType.caption,
                            fontWeight = FontWeight.Bold,
                            color = c.green,
                            modifier = Modifier.clickable {
                                secondsLeft = RESEND_SECONDS
                                pillKey += 1
                            }
                        )
                    }
                }
            }
            NumericKeypad(
                onDigit = { digit ->
                    if (code.length < OTP_LENGTH) {
                        code += digit
                        if (code.length == OTP_LENGTH) onVerified()
                    }
                },
                onDelete = { code = code.dropLast(1) },
                onDone = {},
                modifier = Modifier.navigationBarsPadding()
            )
        }
        AnimatedVisibility(
            visible = pillVisible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 70.dp)
        ) {
            Text(
                text = "A verification code has been sent to your mobile phone",
                style = OPayType.caption,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .clip(OPayShapes.pill)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
        if (exitOpen) {
            OPayDialog(
                title = "Verification is not completed",
                message = "Are you sure you want to exit?",
                primaryText = "Continue",
                onPrimary = { exitOpen = false },
                onDismiss = { exitOpen = false },
                secondaryText = "Exit",
                onSecondary = {
                    exitOpen = false
                    onClose()
                }
            )
        }
    }
}

@Composable
private fun OtpBox(digit: Char?, active: Boolean, modifier: Modifier = Modifier) {
    val c = OPay.colors
    Box(
        modifier = modifier
            .height(50.dp)
            .clip(OPayShapes.tile)
            .background(c.card)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) c.green else c.divider,
                shape = OPayShapes.tile
            ),
        contentAlignment = Alignment.Center
    ) {
        if (digit != null) {
            Text(
                text = digit.toString(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = c.text
            )
        } else if (active) {
            BlinkingCursor()
        }
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "otpCursor")
    val cursorAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "otpCursorAlpha"
    )
    Box(
        modifier = Modifier
            .alpha(cursorAlpha)
            .width(2.dp)
            .height(24.dp)
            .background(OPay.colors.green, CircleShape)
    )
}

@Composable
private fun PhoneOtpIllustration(tint: Color) {
    Canvas(modifier = Modifier.size(width = 52.dp, height = 64.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.1f, h * 0.04f),
            size = Size(w * 0.8f, h * 0.92f),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.4f, h * 0.14f),
            end = Offset(w * 0.6f, h * 0.14f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        for (i in 0 until 4) {
            drawCircle(
                color = tint,
                radius = 2.5.dp.toPx(),
                center = Offset(w * (0.28f + 0.15f * i), h * 0.5f)
            )
        }
    }
}
