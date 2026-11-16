package com.westly.opay.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView

@Composable
fun OPayTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    val c = OPay.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .semantics { contentDescription = "Back"; role = Role.Button }
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
            }
        }
        Text(
            text = title,
            style = OPayType.title,
            color = c.text,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() }
        )
        if (actionText != null) {
            Text(
                text = actionText,
                style = OPayType.titleSmall,
                color = c.green,
                modifier = Modifier.then(
                    if (onAction != null) {
                        Modifier
                            .minimumInteractiveComponentSize()
                            .clickable(role = Role.Button, onClick = onAction)
                    } else {
                        Modifier
                    }
                )
            )
        }
    }
}
