package com.westly.opay.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay

@Composable
fun OPayRingAvatar(size: Dp = 30.dp) {
    val c = OPay.colors
    Canvas(modifier = Modifier.size(size)) {
        val unit = this.size.minDimension / 50.5f
        val centerX = 26.5f * unit
        val centerY = 25.25f * unit
        val radius = 19.75f * unit
        drawArc(
            color = c.green,
            startAngle = 200f,
            sweepAngle = 320f,
            useCenter = false,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 8.5f * unit)
        )
        drawRect(
            color = c.text,
            topLeft = Offset(0f, 21.55f * unit),
            size = Size(14f * unit, 7.4f * unit)
        )
    }
}
