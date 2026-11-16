package com.westly.neribogroup.feature.group

import com.westly.neribogroup.feature.common.rememberKept
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.TintedButton
import java.io.File
import java.util.UUID
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.isHosting
import com.westly.neribogroup.data.isSoftware
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.LocalBiometricGate
import com.westly.neribogroup.feature.common.SearchField
import com.westly.neribogroup.feature.common.SectionScaffold
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatTile
import com.westly.neribogroup.feature.common.matchesQuery
import com.westly.neribogroup.feature.common.profitColor
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.formatFullDate
import com.westly.neribogroup.ui.formatNaira

/** The Apps page: user totals, today's numbers and the list of apps. Tap an app to open its dashboard. */
@Composable
fun AppsScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onOpenApp: (String) -> Unit,
    onSave: (Ledger) -> Unit,
    /** The icon file of an app on this phone (it may not exist), and a number that grows when an icon changes. */
    iconFileFor: (String) -> File,
    iconTick: Int,
    onPickIcon: (String, Uri, (String) -> Unit) -> Unit,
    onDeleteIcon: (String) -> Unit
) {
    val gate = LocalBiometricGate.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ledger) }
    var query by rememberKept("query", "")
    var showAddApp by remember { mutableStateOf(false) }
    var appToDelete by remember { mutableStateOf<AppData?>(null) }
    // The app whose icon is being picked from an existing app card (Edit mode).
    var iconTarget by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val pickIcon = rememberSinglePhotoPicker { uri ->
        val appId = iconTarget
        if (appId != null) {
            onPickIcon(appId, uri) { message ->
                if (message.isEmpty()) {
                    // The picture is already saved on this phone; this marks the app as having one when Save is tapped.
                    draft = draft.copy(apps = draft.apps.map { if (it.id == appId) it.copy(hasIcon = true) else it })
                } else {
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val now = rememberNowMillis()
    val shown = if (editing) draft else ledger
    val searching = query.isNotBlank()
    val visibleApps = shown.apps.filter { matchesQuery(query, it.name) }

    SectionScaffold(
        title = "Apps",
        editing = editing,
        onBack = onBack,
        onStartEdit = {
            draft = ledger
            editing = true
        },
        onCancelEdit = { editing = false },
        onSave = {
            // An app marked for deletion is only removed when you tap Save, so the owner is verified here.
            val removed = ledger.apps.filter { old -> draft.apps.none { it.id == old.id } }
            if (removed.isEmpty()) {
                onSave(draft)
                editing = false
            } else {
                val names = removed.joinToString(", ") { it.name }
                gate.verify(
                    title = if (removed.size == 1) "Delete " + names + "?" else "Delete " + removed.size + " apps?",
                    subtitle = "This cannot be undone.",
                    logText = (if (removed.size == 1) "App deleted: " else "Apps deleted: ") + names
                ) {
                    onSave(draft)
                    editing = false
                }
            }
        }
    ) {
        item { com.westly.neribogroup.feature.storageroom.ServerOfflineBanner() }
        if (editing) {
            item {
                Button(
                    onClick = { showAddApp = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add app") }
            }
        }

        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search apps"
            )
        }

        if (!searching) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Users", formatCount(shown.totalUsers), Modifier.weight(1f))
                    StatTile("Premium", formatCount(shown.premiumUsers), Modifier.weight(1f))
                    StatTile("Pro", formatCount(shown.proUsers), Modifier.weight(1f))
                }
            }

            item { SectionTitle("Today") }

            item {
                Text(
                    text = formatFullDate(now),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("New users", formatCount(shown.newUsersToday(now)), Modifier.weight(1f))
                    StatTile("Premium", formatCount(shown.premiumToday(now)), Modifier.weight(1f))
                    StatTile("Pro", formatCount(shown.proToday(now)), Modifier.weight(1f))
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(
                        "Active users",
                        formatCount(shown.activeUsersToday(now)),
                        Modifier.weight(1f)
                    )
                    StatTile(
                        "Ending in 7 days",
                        formatCount(shown.plansExpiringSoon(now).toLong()),
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                StatTile(
                    label = "Play earnings today (after Google's cut)",
                    value = formatNaira(shown.playEarningsToday(now)),
                    modifier = Modifier.fillMaxWidth(),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }

            item {
                StatTile(
                    label = "Apple earnings today (after Apple's cut)",
                    value = formatNaira(shown.appleEarningsToday(now)),
                    modifier = Modifier.fillMaxWidth(),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }

            item {
                StatTile(
                    label = "Paystack earnings today (after Paystack's fees)",
                    value = formatNaira(shown.paystackEarningsToday(now)),
                    modifier = Modifier.fillMaxWidth(),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }

            item { SectionTitle("Your apps") }
        }

        if (shown.apps.isEmpty()) {
            item {
                Text(
                    text = "No apps yet. Tap the three dots, choose Edit, then Add app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (visibleApps.isEmpty()) {
            item {
                Text(
                    text = "No apps match your search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(visibleApps, key = { it.id }) { app ->
            AppCard(
                app = app,
                editing = editing,
                onClick = { onOpenApp(app.id) },
                onDelete = { if (!app.isSoftware && !app.isHosting) appToDelete = app },
                iconFile = iconFileFor(app.id),
                iconTick = iconTick,
                onChangeIcon = {
                    iconTarget = app.id
                    pickIcon()
                }
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (showAddApp) {
        AddAppDialog(
            iconFileFor = iconFileFor,
            iconTick = iconTick,
            onPickIcon = onPickIcon,
            onDeleteIcon = onDeleteIcon,
            onDismiss = { showAddApp = false },
            onAdd = { newApp ->
                draft = draft.copy(apps = draft.apps + newApp)
                showAddApp = false
            }
        )
    }

    appToDelete?.let { app ->
        ConfirmDialog(
            heading = "Delete ${app.name}?",
            message = if (app.playBalance > 0L) {
                "This app still has " + formatNaira(app.playBalance) +
                    " in the Google Play Balance. Withdraw it first, or it is lost. " +
                    "The app is removed when you tap Save."
            } else {
                "The app and all its entries are removed when you tap Save."
            },
            confirmLabel = "Delete",
            onConfirm = {
                draft = draft.copy(apps = draft.apps.filter { it.id != app.id })
                appToDelete = null
            },
            onDismiss = { appToDelete = null }
        )
    }
}

/**
 * Add app: the name and, if the owner wants, the icon this app gets on the CEO office desktop.
 * The icon is saved on this phone as soon as it is picked (and removed again if the dialog is cancelled).
 */
@Composable
private fun AddAppDialog(
    iconFileFor: (String) -> File,
    iconTick: Int,
    onPickIcon: (String, Uri, (String) -> Unit) -> Unit,
    onDeleteIcon: (String) -> Unit,
    onDismiss: () -> Unit,
    onAdd: (AppData) -> Unit
) {
    val context = LocalContext.current
    val id = remember { UUID.randomUUID().toString() }
    var name by remember { mutableStateOf("") }
    var hasIcon by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(iconTick) }
    val pick = rememberSinglePhotoPicker { uri ->
        onPickIcon(id, uri) { message ->
            if (message.isEmpty()) {
                hasIcon = true
                tick += 1
            } else {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val dismiss = {
        if (hasIcon) onDeleteIcon(id)
        onDismiss()
    }
    NeriboDialog(onDismiss = dismiss, title = "Add app") {
        LabeledField(label = "App name", value = name, onValueChange = { name = it.take(40) })
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (hasIcon) {
                AssetPhoto(iconFileFor(id), tick, Modifier.size(56.dp))
                Spacer(modifier = Modifier.padding(start = 12.dp))
            }
            OutlinedButton(onClick = pick) { Text(if (hasIcon) "Change icon" else "Choose icon") }
        }
        Text(
            text = "The icon shows on the CEO office desktop.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
        DialogActions {
            TextButton(onClick = dismiss) { Text("Cancel") }
            TintedButton(
                text = "Add",
                onClick = { onAdd(AppData(id = id, name = name.trim(), hasIcon = hasIcon)) },
                enabled = name.isNotBlank()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppCard(
    app: AppData,
    editing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    /** The icon file of this app (it may not exist). Only used in Edit mode, with [onChangeIcon]. */
    iconFile: File? = null,
    iconTick: Int = 0,
    onChangeIcon: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        enabled = !editing,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (app.isSoftware) {
                        "Client projects, proposals and a \$ account"
                    } else if (app.isHosting) {
                        "Customers, storage and monthly renewals"
                    } else {
                        formatCount(app.totalUsers) + " users · " +
                            formatCount(app.proUsers) + " Pro · " +
                            formatCount(app.newUsersToday(System.currentTimeMillis())) + " new today"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Profit " + formatNaira(app.profit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = profitColor(app.profit)
                )
                if (editing && onChangeIcon != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (app.hasIcon && iconFile != null) {
                            AssetPhoto(iconFile, iconTick, Modifier.size(48.dp))
                            Spacer(modifier = Modifier.padding(start = 12.dp))
                        }
                        OutlinedButton(onClick = onChangeIcon) { Text(if (app.hasIcon) "Change icon" else "Choose icon") }
                    }
                }
            }
            if (editing && !app.isSoftware && !app.isHosting) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete app")
                }
            }
        }
    }
}
