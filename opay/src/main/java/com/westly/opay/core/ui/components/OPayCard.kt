package com.westly.opay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes

@Composable
fun OPayCard(
    modifier: Modifier = Modifier,
    background: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(background ?: OPay.colors.card, OPayShapes.card)
            .padding(12.dp),
        content = content
    )
}
