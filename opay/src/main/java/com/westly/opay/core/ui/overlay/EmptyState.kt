package com.westly.opay.core.ui.overlay

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 80.dp, bottom = 24.dp, start = 16.dp, end = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = message,
            style = OPayType.body,
            color = OPay.colors.muted,
            textAlign = TextAlign.Center
        )
    }
}
