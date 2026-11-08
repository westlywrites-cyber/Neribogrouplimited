package com.westly.neribogroup.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.cloud.AiJobRules
import com.westly.neribogroup.cloud.AiSettings
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.TintedButton

/** Everything the "Claude (AI)" card shows. */
data class AiCardState(
    /** True when the cloud is linked and signed in. AI mode can only be turned on then. */
    val cloudReady: Boolean,
    val aiMode: Boolean,
    val running: Boolean,
    val message: String,
    val waiting: Int,
    val answered: Int,
    val appliedToday: Int,
    val settings: AiSettings?,
    val settingsBusy: Boolean,
    val settingsMessage: String,
    /** Phase 5: customer cases still waiting for their story. */
    val casesWaiting: Int = 0
)

/** The "Claude (AI)" card of Settings: the AI mode switch, the AI settings and a short how-to. */
@Composable
fun AiCard(
    state: AiCardState,
    onToggle: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onReloadSettings: () -> Unit,
    onSaveSettings: (AiSettings) -> Unit,
    onWriteCasesBuiltIn: () -> Unit = {}
) {
    FlatCard {
        SectionTitle("Claude (AI)")
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "AI mode",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Switch(
                checked = state.aiMode,
                onCheckedChange = { onToggle(it) },
                enabled = state.cloudReady || state.aiMode
            )
        }
        Text(
            text = "When ON, Claude rolls your dice. You say \"roll next\" in Claude.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!state.cloudReady && !state.aiMode) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Connect to the cloud first (the Cloud card above).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (state.message.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.aiMode) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Waiting: " + state.waiting + " · Answered: " + state.answered +
                    " · Applied today: " + state.appliedToday,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (state.waiting >= 3) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = state.waiting.toString() + " dice are waiting. Each 'roll next' does one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.casesWaiting > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (state.casesWaiting == 1) "1 case waiting" else state.casesWaiting.toString() + " cases waiting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val limit = state.settings?.maxJobsPerDay
            if (limit != null && state.waiting + state.answered + state.appliedToday > limit) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "There are many dice today. You may want to raise \"Jobs per day\" below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TintedButton(
                text = if (state.running) "Checking…" else "Check for Claude's answers",
                onClick = onCheck,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.running
            )
            TextButton(
                onClick = onWriteCasesBuiltIn,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.running && state.casesWaiting > 0
            ) { Text("Write cases with the built-in generator") }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "How to use",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "1. Open Claude.\n2. Turn on the Neribo Group connector.\n3. Type: roll next.\n" +
                    "Repeat later for the next dice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            SettingsPart(state, onReloadSettings, onSaveSettings)
        }
    }
}

@Composable
private fun SettingsPart(
    state: AiCardState,
    onReloadSettings: () -> Unit,
    onSaveSettings: (AiSettings) -> Unit
) {
    val server = state.settings
    if (server == null) {
        Text(
            text = if (state.settingsBusy) "Reading the AI settings…" else "The AI settings are not loaded yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.settingsMessage.isNotEmpty()) {
            Text(
                text = state.settingsMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TintedButton(
            text = "Load the AI settings",
            onClick = onReloadSettings,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.settingsBusy
        )
        return
    }

    var paused by remember(server) { mutableStateOf(server.paused) }
    var minutes by remember(server) { mutableStateOf(server.minGapMinutes) }
    var jobs by remember(server) { mutableStateOf(server.maxJobsPerDay) }
    var lease by remember(server) { mutableStateOf(server.leaseMinutes) }
    var domains by remember(server) { mutableStateOf(server.allowedDomains) }
    var newDomain by remember { mutableStateOf("") }
    var domainError by remember { mutableStateOf("") }

    Text(
        text = "AI settings",
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Pause Claude's jobs",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = paused, onCheckedChange = { paused = it })
    }
    Spacer(modifier = Modifier.height(8.dp))
    Stepper("Minutes between jobs", minutes, 1, 1440, 10) { minutes = it }
    Spacer(modifier = Modifier.height(8.dp))
    Stepper("Jobs per day", jobs, 1, 500, 50) { jobs = it }
    Spacer(modifier = Modifier.height(8.dp))
    Stepper("Lease minutes (time Claude has for one job)", lease, 1, 240, 10) { lease = it }

    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Allowed email domains",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    for (d in domains) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = d,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            TextButton(onClick = { domains = domains.filter { it != d } }) { Text("Remove") }
        }
    }
    if (domains.isEmpty()) {
        Text(
            text = "No domain is allowed, so Claude's names will be replaced by made-up ones.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    LabeledField(
        label = "Add a domain, for example example.com",
        value = newDomain,
        onValueChange = {
            newDomain = it.trim().lowercase()
            domainError = ""
        },
        errorText = if (domainError.isEmpty()) null else domainError
    )
    Spacer(modifier = Modifier.height(6.dp))
    TintedButton(
        text = "Add domain",
        onClick = {
            val clean = newDomain.trim().lowercase()
            if (!AiJobRules.validDomain(clean)) {
                domainError = "That does not look like a domain."
            } else if (domains.contains(clean)) {
                domainError = "That domain is already in the list."
            } else {
                domains = domains + clean
                newDomain = ""
                domainError = ""
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = newDomain.isNotEmpty()
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Use only domains you own or example domains. Fake addresses at real domains could reach real people.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(12.dp))
    val changed = paused != server.paused || minutes != server.minGapMinutes || jobs != server.maxJobsPerDay ||
        lease != server.leaseMinutes || domains != server.allowedDomains
    TintedButton(
        text = if (state.settingsBusy) "Please wait…" else "Save AI settings",
        onClick = {
            onSaveSettings(AiSettings(paused, minutes, jobs, lease, domains))
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = changed && !state.settingsBusy
    )
    if (state.settingsMessage.isNotEmpty()) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = state.settingsMessage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A number with small and big minus and plus buttons. */
@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, big: Int, onChange: (Int) -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        TextButton(onClick = { onChange((value - big).coerceIn(min, max)) }, enabled = value > min) {
            Text("−$big")
        }
        TextButton(onClick = { onChange((value - 1).coerceIn(min, max)) }, enabled = value > min) {
            Text("−1")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(4.dp))
        TextButton(onClick = { onChange((value + 1).coerceIn(min, max)) }, enabled = value < max) {
            Text("+1")
        }
        TextButton(onClick = { onChange((value + big).coerceIn(min, max)) }, enabled = value < max) {
            Text("+$big")
        }
    }
}
