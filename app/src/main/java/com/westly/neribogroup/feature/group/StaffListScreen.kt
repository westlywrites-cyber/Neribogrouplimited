package com.westly.neribogroup.feature.group

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
import androidx.compose.material3.OutlinedButton
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
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.Staff
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.LocalBiometricGate
import com.westly.neribogroup.feature.common.NameDialog
import com.westly.neribogroup.feature.common.SearchField
import com.westly.neribogroup.feature.common.SectionScaffold
import com.westly.neribogroup.feature.common.StatCard
import com.westly.neribogroup.feature.common.StatCardItem
import com.westly.neribogroup.feature.common.matchesQuery
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatMonth
import com.westly.neribogroup.ui.formatNaira

/** The Staff page: payroll summary, search, the list of staff and Pay all. Tap a person to open them. */
@Composable
fun StaffListScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onOpenStaff: (String) -> Unit,
    onPayAllStaff: () -> Unit,
    onSave: (Ledger) -> Unit
) {
    val gate = LocalBiometricGate.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ledger) }
    var query by remember { mutableStateOf("") }
    var showAddStaff by remember { mutableStateOf(false) }
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }
    var confirmPayAll by remember { mutableStateOf(false) }

    val now = rememberNowMillis()
    val shown = if (editing) draft else ledger
    val visible = shown.staff.filter { matchesQuery(query, it.name, it.role, it.email, it.phone) }

    SectionScaffold(
        title = "Staff",
        editing = editing,
        onBack = onBack,
        onStartEdit = {
            draft = ledger
            editing = true
        },
        onCancelEdit = { editing = false },
        onSave = {
            // A staff member marked for deletion is only removed when you tap Save, so the owner is verified here.
            val removed = ledger.staff.filter { old -> draft.staff.none { it.id == old.id } }
            if (removed.isEmpty()) {
                onSave(draft)
                editing = false
            } else {
                val names = removed.joinToString(", ") { it.name }
                gate.verify(
                    title = if (removed.size == 1) "Delete " + names + "?" else "Delete " + removed.size + " staff?",
                    subtitle = "Their payment history is removed too. This cannot be undone.",
                    logText = (if (removed.size == 1) "Staff member deleted: " else "Staff members deleted: ") + names
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
                placeholder = "Search staff"
            )
        }

        item {
            StatCard(
                items = listOf(
                    StatCardItem("Monthly payroll", formatNaira(shown.monthlyPayroll)),
                    StatCardItem("Paid in " + formatMonth(now), formatNaira(shown.staffPaidInMonth(now)))
                )
            )
        }

        if (shown.staff.isEmpty()) {
            item {
                Text(
                    text = "No staff yet. Tap the three dots, choose Edit, then Add staff.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (visible.isEmpty()) {
            item {
                Text(
                    text = "No staff match your search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(visible, key = { it.id }) { person ->
            StaffCard(
                person = person,
                paidThisMonth = person.isPaidIn(now),
                editing = editing,
                onClick = { onOpenStaff(person.id) },
                onDelete = { staffToDelete = person }
            )
        }

        if (editing) {
            item {
                Button(
                    onClick = { showAddStaff = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add staff") }
            }
        } else if (shown.unpaidStaff(now).isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = { confirmPayAll = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Pay all staff for " + formatMonth(now)) }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (showAddStaff) {
        NameDialog(
            heading = "Add staff",
            label = "Full name",
            onDismiss = { showAddStaff = false },
            onConfirm = { name ->
                draft = draft.copy(staff = draft.staff + Staff(name = name))
                showAddStaff = false
            }
        )
    }

    staffToDelete?.let { person ->
        ConfirmDialog(
            heading = "Delete ${person.name}?",
            message = "The staff member and their payment history are removed when you tap Save.",
            confirmLabel = "Delete",
            onConfirm = {
                draft = draft.copy(staff = draft.staff.filter { it.id != person.id })
                staffToDelete = null
            },
            onDismiss = { staffToDelete = null }
        )
    }

    if (confirmPayAll) {
        val unpaid = ledger.unpaidStaff(now)
        ConfirmDialog(
            heading = "Pay all staff?",
            message = "Record " + formatNaira(unpaid.sumOf { it.outstandingTotal(now) }) + " for " +
                unpaid.size + " staff for " + formatMonth(now) + ".",
            confirmLabel = "Pay all",
            onConfirm = {
                confirmPayAll = false
                gate.verify(
                    title = "Pay all staff for " + formatMonth(now) + "?",
                    subtitle = formatNaira(unpaid.sumOf { it.outstandingTotal(now) }) + " for " + unpaid.size + " staff.",
                    logText = "Salaries paid to " + unpaid.size + " staff for " + formatMonth(now) + ": " +
                        formatNaira(unpaid.sumOf { it.outstandingTotal(now) })
                ) { onPayAllStaff() }
            },
            onDismiss = { confirmPayAll = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StaffCard(
    person: Staff,
    paidThisMonth: Boolean,
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
                    text = person.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (person.role.isNotBlank()) {
                    Text(
                        text = person.role,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatNaira(person.monthlyTotal) + " per month · " +
                        if (paidThisMonth) "Paid" else "Not paid yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (paidThisMonth) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
                )
            }
            if (editing) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete staff")
                }
            }
        }
    }
}
