package com.westly.opay.core.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay

private val RingInk = Color(0xFF1E1355)
private val AvatarTint = Color(0xFFF4FFFB)

private class MedalTone(val body: Color, val rim: Color, val ink: Color, val ribbon: Color)

private fun medalTone(tier: Int): MedalTone = when (tier) {
    1 -> MedalTone(Color(0xFFE2B08A), Color(0xFFC98A5E), Color(0xFF8E5A36), Color(0xFFB87A4F))
    2 -> MedalTone(Color(0xFFD5DBE1), Color(0xFFB4BCC5), Color(0xFF6F7883), Color(0xFF9AA3AD))
    else -> MedalTone(Color(0xFFE8C48A), Color(0xFFD1A25C), Color(0xFFA9763A), Color(0xFFC58E3F))
}

/** Scalloped rosette with a ribbon tab and the tier number, as on the real OPay avatar. */
@Composable
fun OPayTierMedal(tier: Int, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val tone = medalTone(tier)
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val s = this.size.minDimension
            val cx = s * 0.5f
            val cy = s * 0.6f
            val radius = s * 0.34f
            drawRoundRect(
                color = tone.ribbon,
                topLeft = Offset(cx - s * 0.16f, 0f),
                size = Size(s * 0.32f, s * 0.3f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(s * 0.05f)
            )
            val bumps = 12
            for (i in 0 until bumps) {
                val angle = Math.toRadians(i * 360.0 / bumps)
                drawCircle(
                    color = tone.rim,
                    radius = radius * 0.22f,
                    center = Offset(
                        cx + (radius * 0.92f * Math.cos(angle)).toFloat(),
                        cy + (radius * 0.92f * Math.sin(angle)).toFloat()
                    )
                )
            }
            drawCircle(color = tone.rim, radius = radius * 0.95f, center = Offset(cx, cy))
            drawCircle(color = tone.body, radius = radius * 0.8f, center = Offset(cx, cy))
        }
        Text(
            text = tier.toString(),
            fontSize = (size.value * 0.42f).sp,
            fontWeight = FontWeight.Bold,
            color = tone.ink,
            modifier = Modifier.padding(top = size * 0.22f)
        )
    }
}

/**
 * Home-screen avatar: pale disc with the ring mark and the tier medal on its lower right
 * (bronze 1, silver 2, gold 3). Tapping it opens the tier screen.
 */
@Composable
fun OPayTierAvatar(
    tier: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp
) {
    val c = OPay.colors
    Box(
        modifier = modifier
            .size(size + 8.dp)
            .semantics {
                contentDescription = "Account tier $tier"
                role = Role.Button
            }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(size)
                .clip(CircleShape)
                .background(AvatarTint),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size * 0.68f)) {
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
                    color = RingInk,
                    topLeft = Offset(0f, 21.55f * unit),
                    size = Size(14f * unit, 7.4f * unit)
                )
            }
        }
        OPayTierMedal(
            tier = tier,
            modifier = Modifier.align(Alignment.BottomEnd),
            size = size * 0.68f
        )
    }
}
