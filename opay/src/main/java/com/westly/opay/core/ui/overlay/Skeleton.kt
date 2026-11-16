package com.westly.opay.core.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay

@Composable
fun SkeletonBlock(modifier: Modifier, height: Dp) {
    val c = OPay.colors
    val base = c.cardAlt
    val highlight = lerp(c.cardAlt, c.text, 0.12f)
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift = transition.animateFloat(
        initialValue = -400f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1200, easing = LinearEasing)),
        label = "shift"
    )
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .drawBehind {
                val x = shift.value
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(base, highlight, base),
                        start = Offset(x, 0f),
                        end = Offset(x + 400f, 0f)
                    )
                )
            }
    )
}
