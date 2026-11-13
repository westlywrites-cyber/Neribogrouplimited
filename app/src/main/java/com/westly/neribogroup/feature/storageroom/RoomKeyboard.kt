package com.westly.neribogroup.feature.storageroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One key: [id] is what the controller receives, [label] is what is printed on it. */
private class RoomKey(val id: String, val label: String, val weight: Float = 1f, val kind: Int = NORMAL) {
    companion object {
        const val NORMAL = 0
        const val FUNCTION = 1
        const val ENTER = 2
    }
}

private fun letters(text: String): List<RoomKey> =
    text.map { RoomKey(it.lowercaseChar().toString(), it.toString()) }

private val KEY_ROWS: List<List<RoomKey>> = listOf(
    listOf(
        RoomKey("Escape", "Esc", 1.6f),
        RoomKey("F1", "F1", 1f, RoomKey.FUNCTION),
        RoomKey("F2", "F2", 1f, RoomKey.FUNCTION),
        RoomKey("F3", "F3", 1f, RoomKey.FUNCTION),
        RoomKey("F4", "F4", 1f, RoomKey.FUNCTION),
        RoomKey("F5", "F5", 1f, RoomKey.FUNCTION)
    ),
    letters("1234567890") + listOf(RoomKey(".", "."), RoomKey("Backspace", "⌫", 1.6f)),
    listOf(RoomKey("Tab", "Tab", 1.6f)) + letters("QWERTYUIOP"),
    letters("ASDFGHJKL") + listOf(RoomKey("Enter", "Enter", 1.6f, RoomKey.ENTER)),
    letters("ZXCVBNM") + listOf(
        RoomKey("ArrowLeft", "←"),
        RoomKey("ArrowUp", "↑"),
        RoomKey("ArrowDown", "↓"),
        RoomKey("ArrowRight", "→")
    )
)

private val KeyBody = Color(0xFF262D30)
private val KeyText = Color(0xFFDFE8E7)
private val KeyFunction = Color(0xFF1F3A3A)
private val KeyFunctionText = Color(0xFFBFF3EC)
private val KeyAccent = Color(0xFF7FD1C7)
private val KeyAccentText = Color(0xFF04202A)

/**
 * The full keyboard of the Storage room. A key that works right now is outlined; the others are dimmed and do
 * nothing when tapped. [isLit] says which keys work, [onKey] receives the key id ("a", "7", "Enter", "ArrowUp"...).
 */
@Composable
fun RoomKeyboard(isLit: (String) -> Boolean, onKey: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF15181A))
            .padding(horizontal = 7.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (row in KEY_ROWS) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (key in row) {
                    KeyButton(key, isLit(key.id), onKey)
                }
            }
        }
    }
}

@Composable
private fun RowScope.KeyButton(key: RoomKey, lit: Boolean, onKey: (String) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val background = when (key.kind) {
        RoomKey.ENTER -> KeyAccent
        RoomKey.FUNCTION -> KeyFunction
        else -> KeyBody
    }
    val textColor = when (key.kind) {
        RoomKey.ENTER -> KeyAccentText
        RoomKey.FUNCTION -> KeyFunctionText
        else -> KeyText
    }
    var modifier = Modifier
        .weight(key.weight)
        .height(38.dp)
        .alpha(if (lit) 1f else 0.32f)
        .clip(shape)
        .background(background)
    if (lit) modifier = modifier.border(2.dp, KeyAccent, shape)
    Box(
        modifier = modifier.clickable(enabled = lit) { onKey(key.id) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = key.label,
            color = textColor,
            fontSize = if (key.label.length > 2) 11.sp else 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
