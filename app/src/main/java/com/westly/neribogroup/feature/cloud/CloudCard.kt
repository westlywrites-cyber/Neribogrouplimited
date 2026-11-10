package com.westly.neribogroup.feature.cloud

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.cloud.CloudMode
import com.westly.neribogroup.cloud.CloudStatus
import com.westly.neribogroup.cloud.HistoryDoc
import com.westly.neribogroup.cloud.HistoryRow
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.TintedButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    val unsyncedDocs: Int = 0,
    val backupBusy: Boolean = false,
    val backupMessage: String = "",
    /** Staff photos waiting to sync, or refused by the cloud. Empty when all is well. */
    val mediaMessage: String = ""
)

/** What the "Show cloud history of…" window shows. [open] false = the window is closed. */
data class CloudHistoryUi(
    val open: Boolean = false,
    val docs: List<HistoryDoc> = emptyList(),
    val loadingDocs: Boolean = false,
    val selected: HistoryDoc? = null,
    val rows: List<HistoryRow> = emptyList(),
    val loadingRows: Boolean = false,
    val restoring: Boolean = false,
    val message: String = ""
)

/** The colour of the tiny status dot on the home top bar. */
enum class CloudDot { GREY, GREEN, AMBER, RED }

/**
 * Grey = not connected. Green = up to date. Amber = syncing, changes waiting, or no internet.
 * Red = a problem, or please sign in again. [waiting] is how many changes still wait to be sent.
 */
fun cloudDotFor(
    mode: CloudMode,
    signedIn: Boolean,
    initialized: Boolean,
    status: CloudStatus,
    waiting: Int
): CloudDot {
    if (mode == CloudMode.OFF) return CloudDot.GREY
    if (mode == CloudMode.LINKING) return CloudDot.AMBER
    if (!signedIn || !initialized) return CloudDot.RED
    return when (status) {
        CloudStatus.UP_TO_DATE -> if (waiting > 0) CloudDot.AMBER else CloudDot.GREEN
        CloudStatus.SYNCING -> CloudDot.AMBER
        CloudStatus.WAITING_INTERNET -> CloudDot.AMBER
        CloudStatus.PROBLEM -> CloudDot.RED
        CloudStatus.SIGN_IN_AGAIN -> CloudDot.RED
        CloudStatus.NOT_CONNECTED -> CloudDot.GREY
    }
}

/** A tiny dot with no text. Tapping it opens Settings. It is drawn over a corner, so it moves nothing. */
@Composable
fun CloudStatusDot(dot: CloudDot, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = when (dot) {
        CloudDot.GREEN -> Color(0xFF2E9E5B)
        CloudDot.AMBER -> Color(0xFFE0A100)
        CloudDot.RED -> MaterialTheme.colorScheme.error
        CloudDot.GREY -> MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .size(20.dp)
            .clickable(onClickLabel = "Open cloud settings", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape)
        )
    }
}

enum class CloudCardKind { NOT_CONNECTED, LINKING, LINKED, SIGN_IN_AGAIN, PROBLEM }

fun cloudCardKind(s: CloudCardState): CloudCardKind = when {
    s.working -> CloudCardKind.LINKING
    s.mode == CloudMode.LINKING -> CloudCardKind.PROBLEM
    s.mode == CloudMode.ON && s.signedIn && s.initialized -> CloudCardKind.LINKED
    s.mode == CloudMode.ON || s.initialized -> CloudCardKind.SIGN_IN_AGAIN
    else -> CloudCardKind.NOT_CONNECTED
}

/** The Cloud card of Settings: state, Sync now, Back up now, cloud history, sign out and self-check. */
@Composable
fun CloudCard(
    state: CloudCardState,
    selfCheckLines: List<CheckLine>?,
    onConnect: () -> Unit,
    onTryAgain: () -> Unit,
    onSignOut: () -> Unit,
    onSelfCheck: () -> Unit,
    onCloseSelfCheck: () -> Unit,
    onSyncNow: () -> Unit = {},
    onBackUp: () -> Unit = {},
    history: CloudHistoryUi = CloudHistoryUi(),
    onOpenHistory: () -> Unit = {},
    onPickHistoryDoc: (HistoryDoc) -> Unit = {},
    onHistoryBack: () -> Unit = {},
    onHistoryClose: () -> Unit = {},
    onRestoreVersion: (Int) -> Unit = {}
) {
    var confirmSignOut by remember { mutableStateOf(false) }
    var restoreAsk by remember { mutableStateOf<HistoryRow?>(null) }
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
            val notSynced = if (kind == CloudCardKind.LINKED) {
                " · " + state.unsyncedDocs + (if (state.unsyncedDocs == 1) " document" else " documents") + " not synced yet"
            } else {
                ""
            }
            Text(
                text = "Device " + state.deviceId.takeLast(8) + notSynced,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (kind == CloudCardKind.LINKED && state.mediaMessage.isNotEmpty()) {
                Text(
                    text = state.mediaMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                    val idle = !state.syncing && !state.working && !state.backupBusy && !history.restoring
                    TintedButton(
                        text = if (state.syncing) "Syncing…" else "Sync now",
                        onClick = onSyncNow,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = idle
                    )
                    TintedButton(
                        text = if (state.backupBusy) "Backing up…" else "Back up now",
                        onClick = onBackUp,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = idle
                    )
                    if (state.backupBusy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Saving a copy in the cloud…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (state.backupMessage.isNotEmpty()) {
                        Text(
                            text = state.backupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TintedButton(
                        text = "Show cloud history of…",
                        onClick = onOpenHistory,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = idle
                    )
                    TintedButton(
                        text = "Sign out",
                        onClick = { confirmSignOut = true },
                        modifier = Modifier.fillMaxWidth(),
                        destructive = true,
                        enabled = !state.syncing && !state.backupBusy && !history.restoring
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
            message = "Your data stays on this phone and nothing is deleted. " +
                "The copy in the cloud is kept too. You can sign in again later; nothing is uploaded twice.",
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
    if (history.open && kind == CloudCardKind.LINKED) {
        CloudHistoryDialog(
            h = history,
            onPickDoc = onPickHistoryDoc,
            onBack = onHistoryBack,
            onClose = onHistoryClose,
            onRestore = { restoreAsk = it }
        )
    }
    val ask = restoreAsk
    val picked = history.selected
    if (ask != null && picked != null && history.open) {
        ConfirmDialog(
            heading = "Restore this version?",
            message = "This will make the cloud and this phone go back to version " + ask.version + " of " +
                picked.name + ". The current version stays in history.",
            confirmLabel = "Restore",
            onConfirm = {
                restoreAsk = null
                onRestoreVersion(ask.version)
            },
            onDismiss = { restoreAsk = null },
            destructive = false
        )
    }
    if (selfCheckLines != null) {
        SelfCheckDialog(lines = selfCheckLines, onClose = onCloseSelfCheck)
    }
}

/** Step 1: pick a document. Step 2: the latest 20 versions of it, each with Restore this version. */
@Composable
private fun CloudHistoryDialog(
    h: CloudHistoryUi,
    onPickDoc: (HistoryDoc) -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    onRestore: (HistoryRow) -> Unit
) {
    val sel = h.selected
    NeriboDialog(
        onDismiss = { if (!h.restoring) onClose() },
        title = if (sel == null) "Cloud history" else sel.name,
        subtitle = if (sel == null) "Pick a document" else "Latest versions, newest first"
    ) {
        if (sel == null) {
            if (h.loadingDocs) {
                HistoryProgress("Loading…")
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (d in h.docs) {
                        TintedButton(text = d.name, onClick = { onPickDoc(d) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        } else {
            if (h.restoring) {
                HistoryProgress("Restoring…")
            } else if (h.loadingRows) {
                HistoryProgress("Loading…")
            } else if (h.rows.isEmpty() && h.message.isEmpty()) {
                Text(
                    text = "No versions found in the cloud for this document.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for ((index, row) in h.rows.withIndex()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(
                                text = "Version " + row.version + (if (index == 0) " · current" else ""),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = historyDateText(row.createdAt) + " · " + historySizeText(row.sizeBytes) +
                                    (if (row.deleted) " · removed" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (index > 0 && !row.deleted) {
                                Spacer(modifier = Modifier.height(6.dp))
                                TintedButton(
                                    text = "Restore this version",
                                    onClick = { onRestore(row) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
        if (h.message.isNotEmpty() && !h.restoring) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = h.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DialogActions {
            if (sel != null) {
                TintedButton(text = "Back", onClick = onBack, enabled = !h.restoring)
            }
            TintedButton(text = "Close", onClick = onClose, enabled = !h.restoring)
        }
    }
}

@Composable
private fun HistoryProgress(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
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

/** The server's time text (for example 2026-11-08T14:05:00.123+00:00) as local "08 Nov 2026 14:05". */
internal fun historyDateText(iso: String): String {
    val fallback = iso.take(16).replace('T', ' ')
    if (iso.length < 19) return iso
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val date = parser.parse(iso.substring(0, 19)) ?: return fallback
        var millis = date.time
        val offset = Regex("([+-])(\\d{2}):?(\\d{2})$").find(iso.substring(19))
        if (offset != null) {
            val sign = if (offset.groupValues[1] == "-") -1L else 1L
            val hours = offset.groupValues[2].toLong()
            val minutes = offset.groupValues[3].toLong()
            millis -= sign * (hours * 60L + minutes) * 60_000L
        }
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (e: Exception) {
        fallback
    }
}

/** 900 B, 12 KB or 1.4 MB. */
internal fun historySizeText(bytes: Long): String = when {
    bytes < 1024L -> bytes.toString() + " B"
    bytes < 1024L * 1024L -> ((bytes + 1023L) / 1024L).toString() + " KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
}
