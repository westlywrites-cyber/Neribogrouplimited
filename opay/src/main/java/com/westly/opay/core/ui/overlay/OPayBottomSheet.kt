package com.westly.opay.core.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes

@Composable
fun OPayBottomSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = OPay.colors
    var present by remember { mutableStateOf(visible) }
    val progress = remember { Animatable(0f) }
    var dragPx by remember { mutableFloatStateOf(0f) }
    var sheetHeight by remember { mutableIntStateOf(0) }
    val dragState = rememberDraggableState { delta ->
        dragPx = (dragPx + delta).coerceAtLeast(0f)
    }

    LaunchedEffect(visible) {
        if (visible) {
            dragPx = 0f
            present = true
            progress.animateTo(1f, tween(260))
        } else if (present) {
            progress.animateTo(0f, tween(220))
            present = false
        }
    }

    if (present) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            RemoveWindowDim()
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val dragFraction = if (sheetHeight == 0) 0f else (dragPx / sheetHeight).coerceIn(0f, 1f)
                            alpha = progress.value * (1f - dragFraction)
                        }
                        .background(Color.Black.copy(alpha = 0.67f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss
                        )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { sheetHeight = it.height }
                        .graphicsLayer {
                            if (sheetHeight == 0) {
                                alpha = 0f
                            } else {
                                translationY = (1f - progress.value) * sheetHeight + dragPx
                            }
                        }
                        .draggable(
                            state = dragState,
                            orientation = Orientation.Vertical,
                            onDragStopped = { velocity ->
                                if (dragPx > sheetHeight * 0.3f || velocity > 1200f) {
                                    onDismiss()
                                } else {
                                    animate(dragPx, 0f, animationSpec = tween(180)) { value, _ ->
                                        dragPx = value
                                    }
                                }
                            }
                        )
                        .clip(OPayShapes.sheet)
                        .background(c.card)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                        .navigationBarsPadding()
                        .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 22.dp),
                    content = content
                )
            }
        }
    }
}
