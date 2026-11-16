package com.westly.opay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView

@Composable
fun OPayRow(
    title: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    icon: OPayIcon? = null,
    value: String? = null,
    showChevron: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = OPay.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            OPayIconView(icon = icon, size = 18.dp, tint = c.green)
        }
        Text(
            text = title,
            style = OPayType.body,
            color = c.text,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(text = value, style = OPayType.caption, color = c.muted)
        }
        trailing?.invoke()
        if (showChevron) {
            OPayIconView(icon = OPayIcon.Forward, size = 16.dp, tint = c.muted)
        }
    }
}

@Composable
fun OPayRowDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OPay.colors.divider)
    )
}
