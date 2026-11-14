package com.westly.neribogroup.feature.storageroom

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A notice shown while the pretend server in the Storage room is off (or starting up). It only informs: editing apps
 * and costs and the normal cloud sync work exactly as before. It reads the power state kept on this phone only.
 */
@Composable
fun ServerOfflineBanner(modifier: Modifier = Modifier) {
    val power by rememberRoomPower()
    if (!power.appsOffline) return
    val starting = power.phase == PowerPhase.BOOTING
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Server offline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = if (starting) {
                    "Your apps are offline while the server starts up (about " + (power.secondsLeft / 60 + 1) +
                        " min left)."
                } else {
                    "Your apps are offline. Your server was turned off in the Storage room. Switch it on there."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "You can still edit apps and costs, and your normal sync keeps working.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
