package com.westly.opay.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes

@Composable
fun OPayToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val c = OPay.colors
    val interaction = remember { MutableInteractionSource() }
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        label = "toggleThumbOffset"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) c.green else c.cardAlt,
        label = "toggleTrack"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) c.onGreen else c.muted,
        label = "toggleThumb"
    )
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                role = Role.Switch,
                onValueChange = onCheckedChange
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(24.dp)
                .clip(OPayShapes.pill)
                .background(trackColor)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}
