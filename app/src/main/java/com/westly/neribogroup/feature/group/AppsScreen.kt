package com.westly.neribogroup.feature.group

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
import com.westly.neribogroup.feature.common.NameDialog
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
    onSave: (Ledger) -> Unit
) {
    val gate = LocalBiometricGate.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ledger) }
    var query by remember { mutableStateOf("") }
    var showAddApp by remember { mutableStateOf(false) }
    var appToDelete by remember { mutableStateOf<AppData?>(null) }

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
                onDelete = { if (!app.isSoftware && !app.isHosting) appToDelete = app }
            )
        }

        if (editing) {
            item {
                Button(
                    onClick = { showAddApp = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add app") }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (showAddApp) {
        NameDialog(
            heading = "Add app",
            label = "App name",
            onDismiss = { showAddApp = false },
            onConfirm = { name ->
                draft = draft.copy(apps = draft.apps + AppData(name = name))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppCard(
    app: AppData,
    editing: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
            }
            if (editing && !app.isSoftware && !app.isHosting) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete app")
                }
            }
        }
    }
}
