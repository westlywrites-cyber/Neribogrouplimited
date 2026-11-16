package com.westly.opay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType

enum class ButtonStyle { Primary, Dark, Outline }

@Composable
fun OPayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    enabled: Boolean = true
) {
    val c = OPay.colors
    val (container, content) = if (!enabled) {
        Pair(c.greenDark, lerp(c.greenDark, Color.White, 0.55f))
    } else {
        when (style) {
            ButtonStyle.Primary -> Pair(c.green, c.onGreen)
            ButtonStyle.Dark -> Pair(c.greenDark, lerp(c.green, Color.White, 0.72f))
            ButtonStyle.Outline -> Pair(c.card, c.green)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(OPayShapes.pill)
            .background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = OPayType.title.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
            color = content,
            textAlign = TextAlign.Center
        )
    }
}
