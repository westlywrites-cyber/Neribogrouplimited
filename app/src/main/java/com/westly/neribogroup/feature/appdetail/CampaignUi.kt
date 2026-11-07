package com.westly.neribogroup.feature.appdetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.CampaignSize
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.dayKey
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.TintedButton
import java.time.Instant
import java.time.ZoneId

private fun sizeBoostText(size: CampaignSize): String = when (size) {
    CampaignSize.SMALL -> "Small (a little more)"
    CampaignSize.MEDIUM -> "Medium (about double)"
    CampaignSize.BIG -> "Big (about triple)"
}

/**
 * The advertising boost. It makes new users, active users and upgrades bigger on the days it runs.
 * Renewals and cancellations are not changed. Starting one costs no money in the app.
 */
@Composable
fun CampaignCard(
    app: AppData,
    now: Long,
    onStart: (CampaignSize, Int, Int) -> Unit,
    onStop: () -> Unit
) {
    val today = dayKey(now)
    val campaign = app.campaign
    var showStart by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    val stopped = campaign != null && campaign.stoppedDayKey.isNotEmpty() &&
        campaign.stoppedDayKey <= today
    val live = campaign != null && campaign.endDayKey >= today && !stopped
    val blockedReason = DiceEngine.campaignError(app, 0, 1, now)

    FlatCard {
        Text(
            text = "Advertising campaign",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (campaign != null && live) {
            val status = if (campaign.startDayKey > today) {
                "Starts " + prettyDay(campaign.startDayKey)
            } else {
                "Running now"
            }
            Text(
                text = campaign.size.label + " campaign · " + status + " · ends " +
                    prettyDay(campaign.endDayKey),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = { confirmStop = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Stop campaign")
            }
        } else {
            Text(
                text = if (stopped && campaign != null) {
                    "The last campaign was stopped. A new one can start after " +
                        prettyDay(campaign.endDayKey) + "."
                } else {
                    "No campaign running. A campaign makes new users, active users and upgrades " +
                        "grow faster for up to " + DiceEngine.MAX_CAMPAIGN_DAYS + " days."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            TintedButton(
                text = "Start campaign",
                onClick = { showStart = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = blockedReason == null
            )
        }
    }

    if (showStart) {
        StartCampaignDialog(
            app = app,
            now = now,
            onDismiss = { showStart = false },
            onStart = { size, startIn, lasts ->
                showStart = false
                onStart(size, startIn, lasts)
            }
        )
    }

    if (confirmStop) {
        ConfirmDialog(
            heading = "Stop this campaign?",
            message = "The boost ends today. A stopped campaign cannot be made longer, " +
                "and a new one can only start after the old end date.",
            confirmLabel = "Stop",
            onConfirm = {
                confirmStop = false
                onStop()
            },
            onDismiss = { confirmStop = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartCampaignDialog(
    app: AppData,
    now: Long,
    onDismiss: () -> Unit,
    onStart: (CampaignSize, Int, Int) -> Unit
) {
    var size by remember { mutableStateOf(CampaignSize.MEDIUM) }
    var startText by remember { mutableStateOf("0") }
    var daysText by remember { mutableStateOf("7") }

    val startIn = startText.toIntOrNull() ?: 0
    val lasts = daysText.toIntOrNull() ?: 0
    val error = DiceEngine.campaignError(app, startIn, lasts, now)

    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    val startDay = today.plusDays(startIn.toLong())
    val endDay = startDay.plusDays((lasts - 1).coerceAtLeast(0).toLong())

    NeriboDialog(
        onDismiss = onDismiss,
        title = "Start a campaign",
        subtitle = "It costs nothing in the app. Renewals and cancellations are not changed."
    ) {
        Text(
            text = "Size",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (choice in CampaignSize.values()) {
                FilterChip(
                    selected = size == choice,
                    onClick = { size = choice },
                    label = { Text(choice.label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = sizeBoostText(size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField(
            label = "Starts in how many days (0 = today)",
            value = startText,
            onValueChange = { raw -> startText = raw.filter { it.isDigit() }.take(2) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(16.dp))
        LabeledField(
            label = "Lasts how many days (1 to " + DiceEngine.MAX_CAMPAIGN_DAYS + ")",
            value = daysText,
            onValueChange = { raw -> daysText = raw.filter { it.isDigit() }.take(2) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = error ?: ("From " + prettyDay(startDay.toString()) + " to " + prettyDay(endDay.toString())),
            style = MaterialTheme.typography.bodyMedium,
            color = if (error != null) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Start",
                onClick = { onStart(size, startIn, lasts) },
                enabled = error == null
            )
        }
    }
}
