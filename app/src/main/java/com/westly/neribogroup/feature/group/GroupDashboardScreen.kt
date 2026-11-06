package com.westly.neribogroup.feature.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.westly.neribogroup.data.DEFAULT_COMPANY_NAME
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.feature.common.AddEntryDialog
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.EntryRow
import com.westly.neribogroup.feature.common.NameDialog
import com.westly.neribogroup.feature.common.SaveBar
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatTile
import com.westly.neribogroup.feature.common.profitColor
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.formatNaira

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDashboardScreen(
    ledger: Ledger,
    onOpenApp: (String) -> Unit,
    onSave: (Ledger) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ledger) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAddApp by remember { mutableStateOf(false) }
    var showAddCost by remember { mutableStateOf(false) }
    var appToDelete by remember { mutableStateOf<AppData?>(null) }
    var confirmImport by remember { mutableStateOf(false) }

    val shown = if (editing) draft else ledger
    val companyName = shown.company.name.ifBlank { DEFAULT_COMPANY_NAME }

    BackHandler(enabled = editing) { editing = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(companyName, fontWeight = FontWeight.SemiBold)
                        if (editing) {
                            Text(
                                text = "Editing - tap Save when done",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (!editing) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        menuOpen = false
                                        draft = ledger
                                        editing = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Export backup") },
                                    onClick = {
                                        menuOpen = false
                                        onExport()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Import backup") },
                                    onClick = {
                                        menuOpen = false
                                        confirmImport = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (editing) {
                SaveBar(
                    onCancel = { editing = false },
                    onSave = {
                        val cleanName = draft.company.name.trim().ifBlank { DEFAULT_COMPANY_NAME }
                        onSave(draft.copy(company = draft.company.copy(name = cleanName)))
                        editing = false
                    }
                )
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (editing) {
                item {
                    OutlinedTextField(
                        value = draft.company.name,
                        onValueChange = {
                            draft = draft.copy(company = draft.company.copy(name = it.take(60)))
                        },
                        label = { Text("Company name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item { OverviewCard(shown) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Users", formatCount(shown.totalUsers), Modifier.weight(1f))
                    StatTile("Premium", formatCount(shown.premiumUsers), Modifier.weight(1f))
                    StatTile("Pro", formatCount(shown.proUsers), Modifier.weight(1f))
                }
            }

            item { SectionTitle("Apps") }

            if (shown.apps.isEmpty()) {
                item {
                    Text(
                        text = "No apps yet. Tap the three dots, choose Edit, then Add app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(shown.apps, key = { it.id }) { app ->
                AppCard(
                    app = app,
                    editing = editing,
                    onClick = { onOpenApp(app.id) },
                    onDelete = { appToDelete = app }
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

            item { SectionTitle("Company costs") }

            item {
                Text(
                    text = "Salaries and other company expenses. Total: " +
                        formatNaira(shown.companyExpenses),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (shown.company.expenses.isEmpty()) {
                item {
                    Text(
                        text = "No company costs yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                shown.company.expenses.sortedByDescending { it.dateMillis },
                key = { it.id }
            ) { entry ->
                EntryRow(
                    entry = entry,
                    editing = editing,
                    onDelete = {
                        draft = draft.copy(
                            company = draft.company.copy(
                                expenses = draft.company.expenses.filter { it.id != entry.id }
                            )
                        )
                    }
                )
            }

            if (editing) {
                item {
                    Button(
                        onClick = { showAddCost = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add company cost") }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
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

    if (showAddCost) {
        AddEntryDialog(
            heading = "Add company cost",
            onDismiss = { showAddCost = false },
            onAdd = { title, amount ->
                val entry = Entry(
                    title = title,
                    amount = amount,
                    isIncome = false,
                    dateMillis = System.currentTimeMillis()
                )
                draft = draft.copy(
                    company = draft.company.copy(expenses = draft.company.expenses + entry)
                )
                showAddCost = false
            }
        )
    }

    appToDelete?.let { app ->
        ConfirmDialog(
            heading = "Delete ${app.name}?",
            message = "The app and all its entries are removed when you tap Save.",
            confirmLabel = "Delete",
            onConfirm = {
                draft = draft.copy(apps = draft.apps.filter { it.id != app.id })
                appToDelete = null
            },
            onDismiss = { appToDelete = null }
        )
    }

    if (confirmImport) {
        ConfirmDialog(
            heading = "Import backup?",
            message = "This replaces everything on this phone with the backup file.",
            confirmLabel = "Choose file",
            onConfirm = {
                confirmImport = false
                onImport()
            },
            onDismiss = { confirmImport = false }
        )
    }
}

@Composable
private fun OverviewCard(ledger: Ledger) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Profit",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = formatNaira(ledger.profit),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = profitColor(ledger.profit)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = formatNaira(ledger.totalIncome),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Expenses",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = formatNaira(ledger.totalExpenses),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Apps spend " + formatNaira(ledger.appExpenses) +
                    " + company costs " + formatNaira(ledger.companyExpenses),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppCard(
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
                    text = formatCount(app.totalUsers) + " users · " +
                        formatCount(app.proUsers) + " Pro · " +
                        formatCount(app.dailyNewUsers) + " new today",
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
            if (editing) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete app")
                }
            }
        }
    }
}
