package com.westly.neribogroup.feature.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.westly.neribogroup.cloud.CloudMode
import com.westly.neribogroup.cloud.CloudStatus
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.TintedButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the Cloud card shows. */
data class CloudCardState(
    val mode: CloudMode,
    val signedIn: Boolean,
    val initialized: Boolean,
    val working: Boolean,
    val email: String,
    val lastSyncMillis: Long,
    val problem: String,
    val deviceId: String,
    val warning: String,
    val selfCheckRunning: Boolean,
    val syncing: Boolean = false,
    val syncMessage: String = "",
    val status: CloudStatus = CloudStatus.UP_TO_DATE,
    val pendingChanges: Int = 0,
    val unsyncedDocs: Int = 0
)

enum class CloudCardKind { NOT_CONNECTED, LINKING, LINKED, SIGN_IN_AGAIN, PROBLEM }

fun cloudCardKind(s: CloudCardState): CloudCardKind = when {
    s.working -> CloudCardKind.LINKING
    s.mode == CloudMode.LINKING -> CloudCardKind.PROBLEM
    s.mode == CloudMode.ON && s.signedIn && s.initialized -> CloudCardKind.LINKED
    s.mode == CloudMode.ON || s.initialized -> CloudCardKind.SIGN_IN_AGAIN
    else -> CloudCardKind.NOT_CONNECTED
}

/** The basic Cloud card of Settings (3A version): connect, state, sign out, self-check. */
@Composable
fun CloudCard(
    state: CloudCardState,
    selfCheckLines: List<CheckLine>?,
    onConnect: () -> Unit,
    onTryAgain: () -> Unit,
    onSignOut: () -> Unit,
    onSelfCheck: () -> Unit,
    onCloseSelfCheck: () -> Unit,
    onSyncNow: () -> Unit = {}
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    val kind = cloudCardKind(state)

    FlatCard {
        SectionTitle("Cloud")
        Spacer(modifier = Modifier.height(8.dp))

        val stateLine = when (kind) {
            CloudCardKind.NOT_CONNECTED -> "Not connected"
            CloudCardKind.LINKING -> "Linking your data…"
            CloudCardKind.LINKED -> linkedStateLine(state)
            CloudCardKind.SIGN_IN_AGAIN -> "Please sign in again"
            CloudCardKind.PROBLEM -> "Link problem — your data on the phone is safe"
        }
        Text(
            text = stateLine,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (kind == CloudCardKind.LINKED) {
            Spacer(modifier = Modifier.height(4.dp))
            val who = if (state.email.isNotEmpty()) state.email else "your account"
            Text(
                text = "Connected as $who",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (kind == CloudCardKind.LINKED && state.syncMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.syncMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (kind == CloudCardKind.PROBLEM ||
            (kind == CloudCardKind.LINKED && !state.syncing && state.status == CloudStatus.PROBLEM)
        ) {
            TextButton(onClick = { showDetails = true }) { Text("Details") }
        }
        if (state.warning.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = state.warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        if (state.deviceId.isNotEmpty()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Device " + state.deviceId.takeLast(8),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (kind) {
                CloudCardKind.NOT_CONNECTED ->
                    TintedButton(text = "Connect to cloud", onClick = onConnect, modifier = Modifier.fillMaxWidth())
                CloudCardKind.SIGN_IN_AGAIN ->
                    TintedButton(text = "Sign in", onClick = onConnect, modifier = Modifier.fillMaxWidth())
                CloudCardKind.PROBLEM -> {
                    TintedButton(text = "Try again", onClick = onTryAgain, modifier = Modifier.fillMaxWidth())
                    if (state.signedIn) {
                        TintedButton(
                            text = "Sign out",
                            onClick = { confirmSignOut = true },
                            modifier = Modifier.fillMaxWidth(),
                            destructive = true
                        )
                    }
                }
                CloudCardKind.LINKED -> {
                    TintedButton(
                        text = if (state.syncing) "Syncing…" else "Sync now",
                        onClick = onSyncNow,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.syncing && !state.working
                    )
                    TintedButton(
                        text = "Sign out",
                        onClick = { confirmSignOut = true },
                        modifier = Modifier.fillMaxWidth(),
                        destructive = true,
                        enabled = !state.syncing
                    )
                }
                CloudCardKind.LINKING -> {}
            }
            TintedButton(
                text = if (state.selfCheckRunning) "Checking…" else "Run self-check",
                onClick = onSelfCheck,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.selfCheckRunning && !state.working
            )
        }
    }

    if (confirmSignOut) {
        ConfirmDialog(
            heading = "Sign out?",
            message = "Your data stays on this phone and the copy in the cloud is kept. " +
                "You can sign in again later; nothing is uploaded twice.",
            confirmLabel = "Sign out",
            onConfirm = {
                confirmSignOut = false
                onSignOut()
            },
            onDismiss = { confirmSignOut = false },
            destructive = false
        )
    }
    if (showDetails) {
        NeriboDialog(onDismiss = { showDetails = false }, title = "Details") {
            Text(
                text = if (state.problem.isNotEmpty()) state.problem else "The link did not finish. Tap Try again.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DialogActions {
                TintedButton(text = "Close", onClick = { showDetails = false })
            }
        }
    }
    if (selfCheckLines != null) {
        SelfCheckDialog(lines = selfCheckLines, onClose = onCloseSelfCheck)
    }
}

/** The calm one-line state of a linked phone. Reads the status the app already worked out. */
private fun linkedStateLine(s: CloudCardState): String {
    if (s.syncing || s.status == CloudStatus.SYNCING) return "Syncing…"
    return when (s.status) {
        CloudStatus.WAITING_INTERNET -> {
            val n = maxOf(1, maxOf(s.pendingChanges, s.unsyncedDocs))
            "Waiting for internet — " + n + (if (n == 1) " change" else " changes") + " will sync later"
        }
        CloudStatus.PROBLEM -> "Sync problem — your data on the phone is safe"
        CloudStatus.SIGN_IN_AGAIN -> "Please sign in again"
        CloudStatus.NOT_CONNECTED -> "Not connected"
        CloudStatus.UP_TO_DATE ->
            if (s.lastSyncMillis > 0L) "Up to date · last synced " + formatTime(s.lastSyncMillis) else "Up to date"
        CloudStatus.SYNCING -> "Syncing…"
    }
}

/**
 * Asked when this phone and the cloud both changed one document. It has no way to close except a choice
 * (back press and a tap outside do nothing), and it never chooses by itself.
 */
@Composable
fun ConflictDialog(
    name: String,
    onKeepPhone: () -> Unit,
    onUseCloud: () -> Unit
) {
    NeriboDialog(onDismiss = {}, title = "Which version?") {
        Text(
            text = "This phone and the cloud both changed $name. Which one do you want to keep?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TintedButton(text = "Keep this phone's version", onClick = onKeepPhone, modifier = Modifier.fillMaxWidth())
            TintedButton(text = "Use the cloud version", onClick = onUseCloud, modifier = Modifier.fillMaxWidth())
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))
