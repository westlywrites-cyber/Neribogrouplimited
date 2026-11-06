package com.westly.neribogroup.feature.appdetail

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.feature.common.AddEntryDialog
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.EntryRow
import com.westly.neribogroup.feature.common.NumberField
import com.westly.neribogroup.feature.common.SaveBar
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatTile
import com.westly.neribogroup.feature.common.profitColor
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.formatNaira

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDashboardScreen(
    app: AppData,
    onBack: () -> Unit,
    onSave: (AppData) -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(app) }
    var menuOpen by remember { mutableStateOf(false) }
    var addIncome by remember { mutableStateOf(false) }
    var addExpense by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val shown = if (editing) draft else app

    BackHandler {
        if (editing) editing = false else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = shown.name.ifBlank { app.name },
                            fontWeight = FontWeight.SemiBold
                        )
                        if (editing) {
                            Text(
                                text = "Editing - tap Save when done",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (editing) editing = false else onBack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
                                        draft = app
                                        editing = true
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
                        onSave(draft.copy(name = draft.name.trim().ifBlank { app.name }))
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
            item { SectionTitle("Users") }

            if (editing) {
                item {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it.take(40)) },
                        label = { Text("App name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    NumberField("Total users", draft.totalUsers, {
                        draft = draft.copy(totalUsers = it)
                    })
                }
                item {
                    NumberField("Premium users", draft.premiumUsers, {
                        draft = draft.copy(premiumUsers = it)
                    })
                }
                item {
                    NumberField("Pro users", draft.proUsers, {
                        draft = draft.copy(proUsers = it)
                    })
                }
                item {
                    NumberField("New users today", draft.dailyNewUsers, {
                        draft = draft.copy(dailyNewUsers = it)
                    })
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Total users", formatCount(shown.totalUsers), Modifier.weight(1f))
                        StatTile("New today", formatCount(shown.dailyNewUsers), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Premium", formatCount(shown.premiumUsers), Modifier.weight(1f))
                        StatTile("Pro", formatCount(shown.proUsers), Modifier.weight(1f))
                    }
                }
            }

            item { SectionTitle("Money") }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile("Income", formatNaira(shown.income), Modifier.weight(1f))
                    StatTile("Expenses", formatNaira(shown.expenses), Modifier.weight(1f))
                }
            }
            item {
                StatTile(
                    label = "Profit",
                    value = formatNaira(shown.profit),
                    modifier = Modifier.fillMaxWidth(),
                    valueColor = profitColor(shown.profit)
                )
            }

            item { SectionTitle("Income and expenses") }

            if (shown.entries.isEmpty()) {
                item {
                    Text(
                        text = "Nothing recorded yet. Tap the three dots, choose Edit, then add income or expenses.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(shown.entries.sortedByDescending { it.dateMillis }, key = { it.id }) { entry ->
                EntryRow(
                    entry = entry,
                    editing = editing,
                    onDelete = {
                        draft = draft.copy(entries = draft.entries.filter { it.id != entry.id })
                    }
                )
            }

            if (editing) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { addIncome = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Add income") }
                        OutlinedButton(
                            onClick = { addExpense = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Add expense") }
                    }
                }
                item {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete this app", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (addIncome) {
        AddEntryDialog(
            heading = "Add income",
            onDismiss = { addIncome = false },
            onAdd = { title, amount ->
                draft = draft.copy(
                    entries = draft.entries + Entry(
                        title = title,
                        amount = amount,
                        isIncome = true,
                        dateMillis = System.currentTimeMillis()
                    )
                )
                addIncome = false
            }
        )
    }

    if (addExpense) {
        AddEntryDialog(
            heading = "Add expense",
            onDismiss = { addExpense = false },
            onAdd = { title, amount ->
                draft = draft.copy(
                    entries = draft.entries + Entry(
                        title = title,
                        amount = amount,
                        isIncome = false,
                        dateMillis = System.currentTimeMillis()
                    )
                )
                addExpense = false
            }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            heading = "Delete ${app.name}?",
            message = "This removes the app and all its entries right away.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}
