package com.westly.opay.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayTopBar

@Composable
fun PlaceholderScreen(title: String, onBack: (() -> Unit)?) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        OPayTopBar(
            title = title,
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nothing to show here",
                style = OPayType.body,
                color = c.muted
            )
        }
    }
}
