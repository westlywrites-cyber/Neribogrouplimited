package com.westly.neribogroup.feature.dell

import android.view.KeyEvent as AndroidKeyEvent
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

/** One key of the Dell keyboard. [code] is the Android key code that is sent into the app on the screen. */
class DellKey(val label: String, val code: Int, val weight: Float = 1f, val kind: Int = NORMAL) {
    companion object {
        const val NORMAL = 0
        const val SPECIAL = 1
        const val ENTER = 2
        const val POWER = 3
        const val SHIFT = 4
    }
}

private fun letterKeys(text: String): List<DellKey> =
    text.map { DellKey(it.toString(), AndroidKeyEvent.KEYCODE_A + (it - 'A')) }

private fun digitKeys(text: String): List<DellKey> =
    text.map { DellKey(it.toString(), AndroidKeyEvent.KEYCODE_0 + (it - '0')) }

private val DELL_ROWS: List<List<DellKey>> = listOf(
    listOf(DellKey("Esc", AndroidKeyEvent.KEYCODE_ESCAPE, 1.4f, DellKey.SPECIAL)) +
        digitKeys("1234567890") +
        listOf(DellKey("⌫", AndroidKeyEvent.KEYCODE_DEL, 1.6f, DellKey.SPECIAL)),
    listOf(DellKey("Tab", AndroidKeyEvent.KEYCODE_TAB, 1.4f, DellKey.SPECIAL)) + letterKeys("QWERTYUIOP"),
    letterKeys("ASDFGHJKL") + listOf(DellKey("Enter", AndroidKeyEvent.KEYCODE_ENTER, 1.8f, DellKey.ENTER)),
    listOf(DellKey("Shift", AndroidKeyEvent.KEYCODE_SHIFT_LEFT, 1.6f, DellKey.SHIFT)) +
        letterKeys("ZXCVBNM") +
        listOf(DellKey(".", AndroidKeyEvent.KEYCODE_PERIOD), DellKey(",", AndroidKeyEvent.KEYCODE_COMMA)),
    listOf(
        DellKey("Power", -1, 1.6f, DellKey.POWER),
        DellKey("@", AndroidKeyEvent.KEYCODE_AT),
        DellKey("-", AndroidKeyEvent.KEYCODE_MINUS),
        DellKey("Space", AndroidKeyEvent.KEYCODE_SPACE, 4f),
        DellKey("←", AndroidKeyEvent.KEYCODE_DPAD_LEFT, 1f, DellKey.SPECIAL),
        DellKey("↑", AndroidKeyEvent.KEYCODE_DPAD_UP, 1f, DellKey.SPECIAL),
        DellKey("↓", AndroidKeyEvent.KEYCODE_DPAD_DOWN, 1f, DellKey.SPECIAL),
        DellKey("→", AndroidKeyEvent.KEYCODE_DPAD_RIGHT, 1f, DellKey.SPECIAL)
    )
)

// The same look as the Storage room keyboard: dark keys, a teal outline on the keys that work right now,
// a teal Enter, a red Power key, and the other keys dimmed when the Dell is off.
private val BoardColor = Color(0xFF15181A)
private val KeyBody = Color(0xFF262D30)
private val KeySpecial = Color(0xFF1F3A3A)
private val KeyLegend = Color(0xFFDFE8E7)
private val KeySpecialLegend = Color(0xFFBFF3EC)
private val KeyAccent = Color(0xFF7FD1C7)
private val KeyAccentText = Color(0xFF04202A)
private val KeyShiftOn = Color(0xFF2F5D8C)
private val KeyPowerBody = Color(0xFF4A1F1F)
private val KeyPowerLegend = Color(0xFFFFB4AB)
private val KeyPowerRing = Color(0xFFFF6B6B)

/**
 * The keyboard on the CEO desk. Every key except Power types or moves in the app that is on the Dell screen:
 * letters and digits type, Up and Down scroll the page, Left, Right and Tab move between things, Enter presses the picked one and Esc goes back.
 * While the Dell is off ([enabled] is false) only the Power key works.
 */
@Composable
fun DellKeyboard(
    enabled: Boolean,
    shiftOn: Boolean,
    onKey: (DellKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BoardColor)
            .padding(horizontal = 7.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        for (row in DELL_ROWS) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (key in row) {
                    DellKeyButton(key, enabled || key.kind == DellKey.POWER, shiftOn, onKey)
                }
            }
        }
    }
}

@Composable
private fun RowScope.DellKeyButton(key: DellKey, lit: Boolean, shiftOn: Boolean, onKey: (DellKey) -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    val shifted = key.kind == DellKey.SHIFT && shiftOn
    val background = when {
        shifted -> KeyShiftOn
        key.kind == DellKey.ENTER -> KeyAccent
        key.kind == DellKey.POWER -> KeyPowerBody
        key.kind == DellKey.SPECIAL || key.kind == DellKey.SHIFT -> KeySpecial
        else -> KeyBody
    }
    val textColor = when (key.kind) {
        DellKey.ENTER -> KeyAccentText
        DellKey.POWER -> KeyPowerLegend
        DellKey.SPECIAL, DellKey.SHIFT -> KeySpecialLegend
        else -> KeyLegend
    }
    var modifier = Modifier
        .weight(key.weight)
        .height(38.dp)
        .alpha(if (lit) 1f else 0.32f)
        .clip(shape)
        .background(background)
    if (lit) modifier = modifier.border(2.dp, if (key.kind == DellKey.POWER) KeyPowerRing else KeyAccent, shape)
    Box(
        modifier = modifier.clickable(enabled = lit) { onKey(key) },
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
