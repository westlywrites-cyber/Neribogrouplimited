package com.westly.opay.core.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPayType
import kotlinx.coroutines.delay

@Stable
class OPayToastState {
    internal var message by mutableStateOf("")
        private set
    internal var visible by mutableStateOf(false)
        private set
    internal var shownCount by mutableIntStateOf(0)
        private set

    fun show(message: String) {
        if (message.isBlank()) return
        this.message = message
        visible = true
        shownCount += 1
    }

    internal fun hide() {
        visible = false
    }
}

@Composable
fun rememberOPayToast(): OPayToastState = remember { OPayToastState() }

@Composable
fun OPayToastHost(
    state: OPayToastState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 90.dp
) {
    LaunchedEffect(state.shownCount) {
        if (state.shownCount > 0) {
            delay(1500)
            state.hide()
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = bottomPadding),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = state.visible,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300))
        ) {
            Text(
                text = state.message,
                style = OPayType.body,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            )
        }
    }
}
