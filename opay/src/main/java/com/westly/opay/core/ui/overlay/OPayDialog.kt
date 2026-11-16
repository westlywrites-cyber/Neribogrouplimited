package com.westly.opay.core.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.ButtonStyle
import com.westly.opay.core.ui.components.OPayButton

internal val OverlayScrim = Color.Black.copy(alpha = 0.67f)

@Composable
internal fun RemoveWindowDim() {
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.setDimAmount(0f) }
}

@Composable
fun OPayDialog(
    title: String,
    message: String?,
    primaryText: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    val c = OPay.colors
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        RemoveWindowDim()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(OverlayScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.card)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                    .padding(16.dp)
            ) {
                Text(
                    text = title,
                    style = OPayType.title,
                    color = c.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = OPayType.body,
                        color = c.muted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.height(16.dp))
                if (secondaryText != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OPayButton(
                            text = secondaryText,
                            onClick = onSecondary ?: onDismiss,
                            modifier = Modifier.weight(1f),
                            style = ButtonStyle.Dark
                        )
                        OPayButton(
                            text = primaryText,
                            onClick = onPrimary,
                            modifier = Modifier.weight(1f),
                            style = ButtonStyle.Primary
                        )
                    }
                } else {
                    OPayButton(
                        text = primaryText,
                        onClick = onPrimary,
                        style = ButtonStyle.Primary
                    )
                }
            }
        }
    }
}
