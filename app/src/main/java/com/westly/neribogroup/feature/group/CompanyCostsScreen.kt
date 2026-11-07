package com.westly.neribogroup.feature.group

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.monthKey
import com.westly.neribogroup.feature.common.AddEntryDialog
import com.westly.neribogroup.feature.common.EntryRow
import com.westly.neribogroup.feature.common.SearchField
import com.westly.neribogroup.feature.common.SectionScaffold
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatCard
import com.westly.neribogroup.feature.common.StatCardItem
import com.westly.neribogroup.feature.common.matchesQuery
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatNaira

/** The Company costs dashboard: totals and every company cost, with search. */
@Composable
fun CompanyCostsScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onSave: (Ledger) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ledger) }
    var query by remember { mutableStateOf("") }
    var showAddCost by remember { mutableStateOf(false) }

    val now = rememberNowMillis()
    val shown = if (editing) draft else ledger
    val costs = shown.company.expenses
    val thisMonthKey = monthKey(now)
    val thisMonth = costs.filter { monthKey(it.dateMillis) == thisMonthKey }.sumOf { it.amount }
    val fromRenewals = costs
        .filter { it.auto && it.title.startsWith("Renewal: ") }
        .sumOf { it.amount }
    val visible = costs
        .filter { matchesQuery(query, it.title, it.amount.toString()) }
        .sortedByDescending { it.dateMillis }

    SectionScaffold(
        title = "Company costs",
        editing = editing,
        onBack = onBack,
        onStartEdit = {
            draft = ledger
            editing = true
        },
        onCancelEdit = { editing = false },
        onSave = {
            onSave(draft)
            editing = false
        }
    ) {
        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search company costs"
            )
        }

        item {
            StatCard(
                items = listOf(
                    StatCardItem("This month", formatNaira(thisMonth)),
                    StatCardItem("All time", formatNaira(shown.companyExpenses)),
                    StatCardItem("From renewals", formatNaira(fromRenewals))
                ),
                columns = 2
            )
        }

        item { SectionTitle("All costs") }

        if (costs.isEmpty()) {
            item {
                Text(
                    text = "No company costs yet. Tap the three dots, choose Edit, then Add company cost.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (visible.isEmpty()) {
            item {
                Text(
                    text = "No costs match your search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(visible, key = { it.id }) { entry ->
            EntryRow(
                entry = entry,
                editing = editing,
                // Legal expenses (paid cases) cannot be deleted, so the books always match the cases.
                canDelete = entry.caseId.isEmpty(),
                tag = if (entry.caseId.isNotEmpty()) {
                    "Legal"
                } else if (entry.appId.isNotEmpty()) {
                    ledger.apps.firstOrNull { it.id == entry.appId }?.name ?: "Deleted app"
                } else {
                    null
                },
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
}
