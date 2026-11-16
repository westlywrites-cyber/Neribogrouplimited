package com.westly.opay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType

@Composable
fun OPayChip(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .clip(OPayShapes.chip)
            .background(if (selected) c.green else c.cardAlt)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = OPayType.caption,
            color = if (selected) c.onGreen else c.text
        )
    }
}
