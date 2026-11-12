package com.westly.neribogroup.feature.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.StorageRules
import com.westly.neribogroup.data.StorageUnit
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LocalBiometricGate
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.NumberField
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatCount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------------------------------
// Company storage: the total, each app's share, the backup copies and the log
// ---------------------------------------------------------------------------------------------

/**
 * A change that needs the owner's fingerprint or screen lock: what to tell the owner first ([warning]), a short
 * line for the Android prompt ([summary]), and what to do once verified.
 */
private class GuardRequest(
    val title: String,
    val warning: String,
    val summary: String,
    val confirmLabel: String,
    val onConfirmed: () -> Unit
)

private fun whenText(millis: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

private fun fraction(used: Long, of: Long): Float =
    if (of <= 0L) 0f else (used.toFloat() / of.toFloat()).coerceIn(0f, 1f)

private fun mbUp(kb: Long): Long = (maxOf(0L, kb) + 1023L) / 1024L

@Composable
private fun UnitPicker(unit: StorageUnit, onChange: (StorageUnit) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (u in StorageUnit.values()) {
            FilterChip(selected = unit == u, onClick = { onChange(u) }, label = { Text(u.label) })
        }
    }
}

/** A number and a unit (MB, GB or TB). It saves the size in MB. */
@Composable
private fun AmountDialog(
    title: String,
    subtitle: String,
    label: String,
    initialMb: Long,
    allowZero: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    val startUnit = remember { StorageRules.bestUnit(initialMb) }
    var unit by remember { mutableStateOf(startUnit) }
    var value by remember { mutableStateOf(if (initialMb <= 0L) 0L else initialMb / startUnit.mb) }
    val mb = StorageRules.toMb(value, unit)
    NeriboDialog(onDismiss = onDismiss, title = title, subtitle = subtitle) {
        NumberField(label = label, value = value, onChange = { value = it }, maxDigits = 6)
        Spacer(modifier = Modifier.height(8.dp))
        UnitPicker(unit) { unit = it }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "= " + StorageRules.textMb(mb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DialogActions {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(text = "Save", enabled = allowZero || mb > 0L, onClick = { onSave(mb) })
        }
    }
}

/**
 * Tells the owner what a change would do to users' data, then asks the phone to verify the owner (fingerprint,
 * face or screen lock). The change is made only after the verification succeeded; if the owner cancels, this
 * warning stays so the owner can try again or cancel.
 */
@Composable
private fun GuardDialog(
    request: GuardRequest,
    onDismiss: () -> Unit
) {
    val gate = LocalBiometricGate.current
    NeriboDialog(onDismiss = onDismiss, title = request.title, subtitle = request.warning) {
        Text(
            text = "You will confirm with your fingerprint or your phone's screen lock.",
            style = MaterialTheme.typography.bodyMedium
        )
        DialogActions {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = request.confirmLabel,
                destructive = true,
                onClick = {
                    gate.verify(request.title, request.summary) {
                        request.onConfirmed()
                        onDismiss()
                    }
                }
            )
        }
    }
}

@Composable
private fun CopyRow(name: String, detail: String, size: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(size, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CompanyStorageScreen(
    ledger: Ledger,
    message: String,
    onBack: () -> Unit,
    onSetTotal: (totalMb: Long, confirmed: Boolean) -> Unit,
    onSetShare: (appId: String, shareMb: Long, confirmed: Boolean) -> Unit,
    onBackUpOffsite: () -> Unit,
    onClearMessage: () -> Unit
) {
    val assets = ledger.assets
    val liveApps = ledger.apps.filter { it.googlePlayEnabled || it.id != SOFTWARE_APP_ID }
    val liveKb = StorageRules.usedKb(ledger.apps)
    val offsiteKb = if (assets.offsiteMillis > 0L) assets.offsiteKb else 0L
    val copiesKb = liveKb + liveKb + offsiteKb
    val totalMb = assets.storageTotalMb
    val totalKb = totalMb * 1024L
    val sharesMb = StorageRules.sharesMb(ledger.apps)

    var editTotal by remember { mutableStateOf(false) }
    var shareAppId by remember { mutableStateOf<String?>(null) }
    var guard by remember { mutableStateOf<GuardRequest?>(null) }
    var notice by remember { mutableStateOf("") }

    val saveTotal: (Long) -> Unit = { newMb ->
        editTotal = false
        notice = ""
        when {
            newMb < sharesMb -> {
                notice = "The apps hold " + StorageRules.textMb(sharesMb) +
                    " between them. Lower their shares first, then lower the company total."
            }
            newMb < totalMb && newMb < mbUp(copiesKb) -> {
                guard = GuardRequest(
                    title = "Lower company storage?",
                    warning = "Company storage would go from " + StorageRules.textMb(totalMb) + " to " +
                        StorageRules.textMb(newMb) + ", but the live, standby and off-site copies already use " +
                        StorageRules.text(copiesKb) + ". " + StorageRules.text(copiesKb - newMb * 1024L) +
                        " would no longer fit, and users' data could be lost.",
                    summary = "Company storage: " + StorageRules.textMb(totalMb) + " to " + StorageRules.textMb(newMb),
                    confirmLabel = "Lower storage",
                    onConfirmed = { onSetTotal(newMb, true) }
                )
            }
            else -> {
                onSetTotal(newMb, false)
            }
        }
    }

    val saveShare: (AppData, Long) -> Unit = { app, newMb ->
        shareAppId = null
        notice = ""
        val oldMb = app.storageShareMb
        val usedMb = mbUp(app.storageAllocatedKb)
        val roomMb = maxOf(0L, totalMb - (sharesMb - oldMb))
        when {
            totalMb <= 0L -> {
                notice = "Set the company's total storage first."
            }
            newMb > roomMb -> {
                notice = "Only " + StorageRules.textMb(roomMb) + " is left to give " + app.name +
                    ". Raise the company storage or lower another app's share first."
            }
            newMb < oldMb && newMb < usedMb -> {
                guard = GuardRequest(
                    title = "Lower " + app.name + "'s storage?",
                    warning = app.name + " would go from " + StorageRules.textMb(oldMb) + " to " +
                        StorageRules.textMb(newMb) + ", but its users already use " +
                        StorageRules.text(app.storageAllocatedKb) + ". " +
                        StorageRules.text(app.storageAllocatedKb - newMb * 1024L) +
                        " would no longer fit. This affects " + formatCount(app.totalUsers) +
                        " users: uploads can be blocked and the data above the limit can be lost.",
                    summary = app.name + " storage: " + StorageRules.textMb(oldMb) + " to " + StorageRules.textMb(newMb),
                    confirmLabel = "Lower storage",
                    onConfirmed = { onSetShare(app.id, newMb, true) }
                )
            }
            else -> {
                onSetShare(app.id, newMb, false)
            }
        }
    }

    PageFrame("Storage", onBack) {
        // ---- The company ----
        item {
            FlatCard {
                Text(
                    "Company storage",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    StorageRules.textMb(totalMb),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (totalMb > 0L) {
                    LinearProgressIndicator(
                        progress = { fraction(copiesKb, totalKb) },
                        color = if (copiesKb > totalKb) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text("Used by users (live): " + StorageRules.text(liveKb))
                Text("Used by all copies: " + StorageRules.text(copiesKb))
                Text("Given to apps: " + StorageRules.textMb(sharesMb))
                Text(
                    if (totalMb > 0L) {
                        if (copiesKb > totalKb) "Over the company total by " + StorageRules.text(copiesKb - totalKb)
                        else "Free: " + StorageRules.text(totalKb - copiesKb)
                    } else {
                        "Type the total storage the company has."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (totalMb > 0L && copiesKb > totalKb) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sharesMb > 0L) {
                    Text(
                        "With the standby and off-site copies, the apps' shares could need up to " +
                            StorageRules.textMb(sharesMb * 3L) + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { editTotal = true }) { Text("Set total storage") }
            }
        }
        if (notice.isNotEmpty()) {
            item {
                FlatCard {
                    Text(notice, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { notice = "" }) { Text("OK") }
                }
            }
        }

        // ---- The apps ----
        item { SectionTitle("Each app") }
        if (liveApps.isEmpty()) {
            item {
                Text(
                    "No live app yet. Turn on a channel (Google Play, Apple Store or Website) in an app, then give it " +
                        "a share here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        for (app in liveApps.sortedByDescending { it.storageAllocatedKb }) {
            item(key = "storage-" + app.id) {
                val shareKb = app.storageShareMb * 1024L
                val over = StorageRules.overShareKb(app)
                FlatCard {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            app.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(StorageRules.text(app.storageAllocatedKb), fontWeight = FontWeight.Bold)
                    }
                    Text(
                        if (app.storageShareMb > 0L) "of its share of " + StorageRules.textMb(app.storageShareMb)
                        else "No share set yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (app.storageShareMb > 0L) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { fraction(app.storageAllocatedKb, shareKb) },
                            color = if (over > 0L) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            if (over > 0L) "Over its share by " + StorageRules.text(over)
                            else "Free: " + StorageRules.text(shareKb - app.storageAllocatedKb),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (over > 0L) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val custom = app.customStorageUsers
                    val plainFree = maxOf(0L, app.freeUsers - custom.size.toLong())
                    Text(
                        "Free " + formatCount(plainFree) + " × " + StorageRules.textMb(app.storageFreeMb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (app.proUsers > 0L || app.storageProMb > 0L) {
                        Text(
                            "Pro " + formatCount(app.proUsers) + " × " + StorageRules.textMb(app.storageProMb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (app.premiumUsers > 0L || app.storagePremiumMb > 0L) {
                        Text(
                            "Premium " + formatCount(app.premiumUsers) + " × " + StorageRules.textMb(app.storagePremiumMb),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (custom.isNotEmpty()) {
                        Text(
                            "Customize " + custom.size + (if (custom.size == 1) " user: " else " users: ") +
                                StorageRules.textMb(custom.sumOf { it.customStorageMb }),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (app.deployTracking) {
                        Text(
                            formatCount(app.totalSites) + " sites share their accounts' storage",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { shareAppId = app.id }) { Text("Edit share") }
                }
            }
        }

        // ---- Backups ----
        item { SectionTitle("Backups") }
        item {
            FlatCard {
                CopyRow("Live copy", "What the users have now", StorageRules.text(liveKb))
                Spacer(modifier = Modifier.height(10.dp))
                CopyRow("Standby copy", "A second copy that follows the live one in real time. In sync.", StorageRules.text(liveKb))
                Spacer(modifier = Modifier.height(10.dp))
                val gap = StorageRules.offsiteGapKb(assets, liveKb)
                CopyRow(
                    "Off-site copy",
                    if (assets.offsiteMillis <= 0L) "Never made"
                    else "Made " + whenText(assets.offsiteMillis) + (if (gap == 0L) ". Up to date." else ". " +
                        StorageRules.text(gap) + " different from live."),
                    StorageRules.text(offsiteKb)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onBackUpOffsite) { Text("Back up off-site now") }
                if (message.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(message, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onClearMessage) { Text("OK") }
                }
            }
        }

        // ---- The log ----
        item { SectionTitle("Storage log") }
        if (assets.storageLog.isEmpty()) {
            item {
                Text(
                    "Nothing changed yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        for ((index, line) in assets.storageLog.take(30).withIndex()) {
            item(key = "log-" + index + "-" + line.millis) {
                FlatCard {
                    Text(line.text, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        whenText(line.millis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (editTotal) {
        AmountDialog(
            title = "Total storage",
            subtitle = "How much storage the whole company has. Type a number and choose MB, GB or TB.",
            label = "Total storage",
            initialMb = totalMb,
            allowZero = false,
            onDismiss = { editTotal = false },
            onSave = saveTotal
        )
    }
    val shareApp = shareAppId?.let { id -> liveApps.firstOrNull { it.id == id } }
    if (shareApp != null) {
        AmountDialog(
            title = shareApp.name + " storage",
            subtitle = "This app's share of the company storage. Its users now use " +
                StorageRules.text(shareApp.storageAllocatedKb) + ". Type 0 to remove the share.",
            label = "Storage for this app",
            initialMb = shareApp.storageShareMb,
            allowZero = true,
            onDismiss = { shareAppId = null },
            onSave = { mb -> saveShare(shareApp, mb) }
        )
    }
    val pending = guard
    if (pending != null) {
        GuardDialog(
            request = pending,
            onDismiss = { guard = null }
        )
    }
}
