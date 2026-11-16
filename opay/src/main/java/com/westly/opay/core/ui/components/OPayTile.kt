package com.westly.opay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView

@Composable
fun OPayTile(icon: OPayIcon, label: String, onClick: () -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .clip(OPayShapes.tile)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(c.cardAlt, OPayShapes.tile),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = icon, size = 20.dp, tint = c.green)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            style = OPayType.caption,
            color = c.text,
            textAlign = TextAlign.Center
        )
    }
}
