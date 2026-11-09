package com.westly.neribogroup.feature.appdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.cloud.AiRollKind
import com.westly.neribogroup.cloud.AiRollState
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatFullDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 4B: the calm "Today's roll" card that replaces the dice screen when AI mode is ON.
 * It shows no dice faces and no roll buttons, only how far Claude is.
 * [canRollBuiltIn] is true while today's roll is still possible.
 */
@Composable
fun AiRollCard(
    state: AiRollState,
    now: Long,
    canRollBuiltIn: Boolean,
    checking: Boolean,
    onCheck: () -> Unit,
    onRollBuiltIn: () -> Unit
) {
    var confirm by remember { mutableStateOf(false) }

    Column {
    FlatCard {
        Text(
            text = "Today's roll",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = formatFullDate(now),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        val timeText = if (state.timeMillis > 0L) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(state.timeMillis))
        } else {
            ""
        }
        val headline = when (state.kind) {
            AiRollKind.WAITING -> "Waiting for Claude — " + state.answered + " of " + state.total + " answers received"
            AiRollKind.ROLLED_CLAUDE -> "Rolled today by Claude ✔" + (if (timeText.isNotEmpty()) " at $timeText" else "")
            AiRollKind.ROLLED_BUILTIN -> "Rolled with built-in dice"
            AiRollKind.ROLLED_OTHER -> "Rolled today ✔"
            AiRollKind.NO_JOBS -> "Not rolled yet — open Claude and say \"roll next\". It rolls one whole app each time."
            AiRollKind.OFFLINE -> "Waiting for internet"
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (state.kind == AiRollKind.WAITING && state.total > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val dots = minOf(state.total, 7)
                for (i in 0 until dots) {
                    val filled = i < state.answered
                    Spacer(
                        modifier = Modifier
                            .size(12.dp)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }
        }

        if (state.kind == AiRollKind.ROLLED_CLAUDE || state.kind == AiRollKind.ROLLED_BUILTIN ||
            state.kind == AiRollKind.ROLLED_OTHER
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "The results are in the app's screens and the roll history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            TintedButton(
                text = if (checking) "Checking…" else "Check for Claude's answers",
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth(),
                enabled = !checking
            )
        }
    }

    if (canRollBuiltIn) {
        TextButton(onClick = { confirm = true }) {
            Text("Roll with built-in dice instead")
        }
    }

    if (confirm) {
        ConfirmDialog(
            heading = "Roll with built-in dice?",
            message = "The app rolls today's dice by itself now, the way it did before. " +
                "Claude's waiting jobs for this app are dropped.",
            confirmLabel = "Roll now",
            onConfirm = {
                confirm = false
                onRollBuiltIn()
            },
            onDismiss = { confirm = false }
        )
    }
    }
}
