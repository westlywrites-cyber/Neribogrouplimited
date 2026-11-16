package com.westly.opay.feature.login

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType

private data class RidgeArc(val radius: Float, val start: Float, val sweep: Float)

private val RIDGES = listOf(
    RidgeArc(radius = 9f, start = 195f, sweep = 150f),
    RidgeArc(radius = 6.5f, start = 160f, sweep = 220f),
    RidgeArc(radius = 4f, start = 150f, sweep = 240f)
)

/** A fingerprint drawn as ridge arcs on a 24-unit grid. */
@Composable
internal fun FingerprintGlyph(size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 24f
        val centreX = 12f * unit
        val centreY = 11f * unit
        val stroke = Stroke(width = 1.6f * unit, cap = StrokeCap.Round)
        RIDGES.forEach { ridge ->
            val r = ridge.radius * unit
            drawArc(
                color = tint,
                startAngle = ridge.start,
                sweepAngle = ridge.sweep,
                useCenter = false,
                topLeft = Offset(centreX - r, centreY - r),
                size = Size(2f * r, 2f * r),
                style = stroke
            )
        }
        drawLine(
            color = tint,
            start = Offset(centreX, centreY - 1f * unit),
            end = Offset(centreX, centreY + 8f * unit),
            strokeWidth = 1.6f * unit,
            cap = StrokeCap.Round
        )
    }
}

/** The small button on the login password screen (C3) that opens the fingerprint screen (C1). */
@Composable
internal fun FingerprintLoginButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    Row(
        modifier = modifier
            .clip(OPayShapes.pill)
            .border(1.dp, c.green, OPayShapes.pill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FingerprintGlyph(size = 22.dp, tint = c.green)
        Text(
            text = "Use fingerprint",
            style = OPayType.bodySmall,
            fontWeight = FontWeight.Bold,
            color = c.green
        )
    }
}
