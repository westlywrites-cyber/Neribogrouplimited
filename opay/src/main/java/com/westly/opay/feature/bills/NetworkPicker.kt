package com.westly.opay.feature.bills

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.R
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import kotlin.math.roundToInt

internal val PickerNetworks = listOf("MTN", "Airtel", "Glo", "T2 Mobile", "Vitel")

internal fun networkDisplayName(name: String): String = when (name) {
    "Glo" -> "GLO"
    "Vitel" -> "VITEL"
    else -> name
}

internal fun networkLogoRes(name: String): Int? = when (name.trim().lowercase()) {
    "mtn" -> R.drawable.net_mtn
    "airtel" -> R.drawable.net_airtel
    "glo" -> R.drawable.net_glo
    "t2 mobile", "9mobile" -> R.drawable.net_t2
    "vitel" -> R.drawable.net_vitel
    else -> null
}

@Composable
internal fun NetworkLogo(name: String, size: Dp = 32.dp) {
    val logo = networkLogoRes(name)
    if (logo != null) {
        Image(
            painter = painterResource(logo),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color(0xFF00793F)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase(),
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ArrowHead(up: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(width = 10.dp, height = 6.dp)) {
        val path = Path().apply {
            if (up) {
                moveTo(0f, size.height)
                lineTo(size.width, size.height)
                lineTo(size.width / 2f, 0f)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path = path, color = color)
    }
}

@Composable
internal fun NetworkSelector(
    network: String?,
    open: Boolean,
    onClick: () -> Unit,
    logoSize: Dp = 28.dp
) {
    val c = OPay.colors
    Row(
        modifier = Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (network != null) {
            NetworkLogo(name = network, size = logoSize)
        } else {
            OPayIconView(icon = OPayIcon.Phone, size = 22.dp, tint = c.green)
        }
        Spacer(Modifier.width(8.dp))
        ArrowHead(up = open, color = if (open) c.green else c.muted)
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(24.dp)
                .background(c.muted.copy(alpha = 0.4f))
        )
    }
}

@Composable
internal fun rememberPickerAnchor(): PickerAnchor = remember { PickerAnchor() }

internal class PickerAnchor {
    var bottomPx by mutableIntStateOf(0)

    fun modifier(): Modifier = Modifier.onGloballyPositioned {
        bottomPx = (it.positionInRoot().y + it.size.height).roundToInt()
    }
}

@Composable
internal fun NetworkPickerOverlay(
    visible: Boolean,
    selected: String?,
    anchor: PickerAnchor,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val c = OPay.colors
    BackHandler(onBack = onDismiss)
    var originY by remember { mutableIntStateOf(0) }
    val top = (anchor.bottomPx - originY).coerceAtLeast(0)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { originY = it.positionInRoot().y.roundToInt() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, top) }
                .background(Color(0x99000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        Column(
            modifier = Modifier
                .offset { IntOffset(0, top) }
                .padding(horizontal = 16.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .padding(start = 56.dp)
                    .size(width = 18.dp, height = 8.dp)
            ) {
                val path = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width, size.height)
                    lineTo(size.width / 2f, 0f)
                    close()
                }
                drawPath(path = path, color = c.bg)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg, RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(horizontal = 12.dp)
            ) {
                PickerNetworks.forEachIndexed { index, name ->
                    if (index > 0) {
                        Spacer(
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(c.muted.copy(alpha = 0.2f))
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(name) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NetworkLogo(name = name, size = 30.dp)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = networkDisplayName(name),
                            fontSize = 20.sp,
                            color = c.text,
                            modifier = Modifier.weight(1f)
                        )
                        if (name == selected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(c.green, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                OPayIconView(icon = OPayIcon.Check, size = 16.dp, tint = Color.White)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .border(2.dp, c.muted, CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
