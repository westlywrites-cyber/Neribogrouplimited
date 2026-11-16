package com.westly.opay.core.ui.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay

@Composable
fun OPayLoader(modifier: Modifier = Modifier, size: Dp = 54.dp) {
    val color = OPay.colors.green
    val transition = rememberInfiniteTransition(label = "loader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1000, easing = LinearEasing)),
        label = "rotation"
    )
    Canvas(modifier = modifier.size(size)) {
        val diameter = this.size.minDimension
        val strokeWidth = diameter * 7f / 54f
        drawArc(
            color = color,
            startAngle = 45f + rotation,
            sweepAngle = 270f,
            useCenter = false,
            topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = Size(diameter - strokeWidth, diameter - strokeWidth),
            style = Stroke(width = strokeWidth)
        )
    }
}
