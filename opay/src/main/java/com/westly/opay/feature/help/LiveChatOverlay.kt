package com.westly.opay.feature.help

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val REPLY_DELAY_MILLIS = 800L
private const val DEMO_TAG = "(demo response) "
private const val GREETING = "Hello, how can I help you today?"

private data class ChatMessage(val fromUser: Boolean, val text: String)

private val quickReplies = listOf("Transfer issue", "Account limit", "Card", "Other")

@Composable
fun LiveChatOverlay(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = remember { mutableStateListOf(ChatMessage(false, GREETING)) }
    var typing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    val showChips = messages.size == 1
    val itemCount = messages.size + (if (showChips) 1 else 0) + (if (typing) 1 else 0)

    BackHandler(onBack = onClose)
    LaunchedEffect(itemCount) {
        listState.animateScrollToItem(itemCount - 1)
    }

    fun send(text: String, fromChip: Boolean) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        messages.add(ChatMessage(true, trimmed))
        typing = true
        scope.launch {
            delay(REPLY_DELAY_MILLIS)
            val reply = cannedReply(trimmed)
            messages.add(ChatMessage(false, if (fromChip) DEMO_TAG + reply else reply))
            typing = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Close"; role = Role.Button }
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
                }
                Column {
                    Text(text = "Live Chat", style = OPayType.title, color = c.text)
                    Text(text = "Demo assistant", style = OPayType.caption, color = c.muted)
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(messages) { _, message ->
                    ChatBubble(message = message)
                }
                if (showChips) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            quickReplies.forEach { label ->
                                OPayChip(
                                    text = label,
                                    onClick = { send(label, fromChip = true) }
                                )
                            }
                        }
                    }
                }
                if (typing) {
                    item { TypingBubble() }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OPayUnderlineField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = "Type a message",
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.green)
                        .clickable {
                            send(input, fromChip = false)
                            input = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Forward, size = 20.dp, tint = c.onGreen)
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val c = OPay.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Text(
            text = message.text,
            style = OPayType.body,
            color = if (message.fromUser) c.onGreen else c.text,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (message.fromUser) c.green else c.card)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun TypingBubble() {
    val c = OPay.colors
    val transition = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (index in 0..2) {
            val dotAlpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 160)
                ),
                label = "dot$index"
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .alpha(dotAlpha)
                    .clip(CircleShape)
                    .background(c.muted)
            )
        }
    }
}

private fun cannedReply(userText: String): String {
    val text = userText.lowercase()
    return when {
        "transfer" in text ->
            "For a transfer that has not arrived, check the transaction details and share the " +
                "transaction number with the receiving bank. In this demo no real transfer " +
                "is tracked."
        "limit" in text ->
            "Your account limits are shown under Me > Account Limits. In this demo they are " +
                "fixed sample values."
        "card" in text ->
            "Card services such as freezing or replacing a card are not available in this " +
                "demo. No real card is connected to this app."
        "pin" in text ->
            "Never share your PIN with anyone. In this demo the PIN cannot be reset."
        else ->
            "I am a demo assistant with a few scripted answers, so I may not understand that. " +
                "Try asking about transfers, limits, cards or your PIN."
    }
}
