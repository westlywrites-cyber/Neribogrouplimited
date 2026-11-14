package com.westly.neribogroup.feature.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.DAY_MILLIS
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.Renewal
import com.westly.neribogroup.feature.common.AddRenewalDialog
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FieldGap
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.SearchField
import com.westly.neribogroup.feature.common.SectionScaffold
import com.westly.neribogroup.feature.common.StatCard
import com.westly.neribogroup.feature.common.StatCardItem
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.matchesQuery
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira

/** The Renewals page: every renewal in one place, with search, Renew and (while editing) Add and Delete. */
@Composable
fun RenewalsScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onRenew: (String) -> Unit,
    onSave: (Ledger) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ledger) }
    var query by remember { mutableStateOf("") }
    var showAddRenewal by remember { mutableStateOf(false) }
    var renewalToDelete by remember { mutableStateOf<Renewal?>(null) }
    var renewalToRenew by remember { mutableStateOf<Renewal?>(null) }
    var renewalToEdit by remember { mutableStateOf<Renewal?>(null) }

    val now = rememberNowMillis()
    val shown = if (editing) draft else ledger
    val visible = shown.renewals
        .filter { matchesQuery(query, it.name) }
        .sortedBy { it.daysLeft(now) }

    SectionScaffold(
        title = "Renewals",
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
        if (editing) {
            item {
                Button(
                    onClick = { showAddRenewal = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add renewal") }
            }
        }

        item {
            SearchField(
                query = query,
                onQueryChange = { query = it },
                placeholder = "Search renewals"
            )
        }

        item {
            StatCard(
                items = listOf(
                    StatCardItem(
                        "Need attention",
                        shown.renewalsNeedingAttention(now).size.toString()
                    ),
                    StatCardItem("Tracked", shown.renewals.size.toString())
                )
            )
        }

        if (shown.renewals.isEmpty()) {
            item {
                Text(
                    text = "Nothing to track yet. Tap the three dots, choose Edit, then Add renewal.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (visible.isEmpty()) {
            item {
                Text(
                    text = "No renewals match your search.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(visible, key = { it.id }) { renewal ->
            RenewalCard(
                renewal = renewal,
                now = now,
                editing = editing,
                onRenew = { renewalToRenew = renewal },
                onEdit = { renewalToEdit = renewal },
                onDelete = { renewalToDelete = renewal }
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (showAddRenewal) {
        AddRenewalDialog(
            onDismiss = { showAddRenewal = false },
            onAdd = { name, amount, days, reminders, recordCost ->
                val stamp = System.currentTimeMillis()
                val renewal = Renewal(
                    name = name,
                    amount = amount,
                    durationDays = days,
                    paidMillis = stamp,
                    expiryMillis = stamp + days * DAY_MILLIS,
                    reminderDays = reminders
                )
                val expenses = if (recordCost) {
                    draft.company.expenses + Entry(
                        title = "Renewal: $name",
                        amount = amount,
                        isIncome = false,
                        dateMillis = stamp,
                        auto = true
                    )
                } else {
                    draft.company.expenses
                }
                draft = draft.copy(
                    renewals = draft.renewals + renewal,
                    company = draft.company.copy(expenses = expenses)
                )
                showAddRenewal = false
            }
        )
    }

    renewalToDelete?.let { item ->
        ConfirmDialog(
            heading = "Delete ${item.name}?",
            message = "It stops being tracked when you tap Save. Costs already recorded stay.",
            confirmLabel = "Delete",
            onConfirm = {
                draft = draft.copy(renewals = draft.renewals.filter { it.id != item.id })
                renewalToDelete = null
            },
            onDismiss = { renewalToDelete = null }
        )
    }

    renewalToEdit?.let { target ->
        key(target.id) {
            EditRenewalDialog(
                renewal = target,
                now = now,
                onDismiss = { renewalToEdit = null },
                onSave = { updated ->
                    onSave(
                        ledger.copy(
                            renewals = ledger.renewals.map { if (it.id == updated.id) updated else it }
                        )
                    )
                    renewalToEdit = null
                }
            )
        }
    }

    renewalToRenew?.let { item ->
        val costNote = if (item.amount > 0L) {
            " and records " + formatNaira(item.amount) + " as a company cost."
        } else {
            "."
        }
        ConfirmDialog(
            heading = "Renew ${item.name}?",
            message = "Adds " + item.durationDays + " more days" + costNote,
            confirmLabel = "Renew",
            onConfirm = {
                renewalToRenew = null
                onRenew(item.id)
            },
            onDismiss = { renewalToRenew = null }
        )
    }
}

internal fun renewalStatus(left: Long): String = when {
    left < 0L -> "Expired " + (-left) + (if (-left == 1L) " day ago" else " days ago")
    left == 0L -> "Expires today"
    left == 1L -> "1 day left"
    else -> "$left days left"
}

@Composable
private fun RenewalCard(
    renewal: Renewal,
    now: Long,
    editing: Boolean,
    onRenew: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val left = renewal.daysLeft(now)
    val level = renewal.alertLevel(now)
    val statusColor = when (level) {
        2 -> MaterialTheme.colorScheme.error
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = renewal.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = renewalStatus(left),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = statusColor
                )
                Text(
                    text = "Expires " + formatDate(renewal.expiryMillis) +
                        if (renewal.amount > 0L) " · " + formatNaira(renewal.amount) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (renewal.reminderDays.isNotEmpty()) {
                    Text(
                        text = "Warns at " + renewal.reminderDays.sortedDescending().joinToString(", ") +
                            " days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!editing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRenew) { Text("Renew") }
                        OutlinedButton(onClick = onEdit) { Text("Edit") }
                    }
                }
            }
            if (editing) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete renewal")
                }
            }
        }
    }
}

/** Opens a renewal again so its name, amount, length, days left and warnings can be changed. */
@Composable
private fun EditRenewalDialog(
    renewal: Renewal,
    now: Long,
    onDismiss: () -> Unit,
    onSave: (Renewal) -> Unit
) {
    val startDaysLeft = renewal.daysLeft(now)
    // Blank for a renewal that has already expired, so saving without touching it keeps the old date.
    val startDaysText = if (startDaysLeft >= 0L) startDaysLeft.toString() else ""

    var name by remember { mutableStateOf(renewal.name) }
    var amount by remember {
        mutableStateOf(if (renewal.amount > 0L) renewal.amount.toString() else "")
    }
    var duration by remember { mutableStateOf(renewal.durationDays.toString()) }
    var daysLeftText by remember { mutableStateOf(startDaysText) }
    var reminders by remember {
        mutableStateOf(renewal.reminderDays.sortedDescending().joinToString(", "))
    }

    val amountValue = amount.toLongOrNull() ?: 0L
    val durationCount = duration.toIntOrNull() ?: 0

    NeriboDialog(
        onDismiss = onDismiss,
        title = "Edit renewal",
        subtitle = "Costs already recorded stay as they are."
    ) {
        LabeledField(
            label = "What did you pay for?",
            value = name,
            onValueChange = { name = it.take(60) }
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Amount (₦)",
            value = amount,
            onValueChange = { raw -> amount = raw.filter { it.isDigit() }.take(15) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Lasts how many days?",
            value = duration,
            onValueChange = { raw -> duration = raw.filter { it.isDigit() }.take(4) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = if (startDaysLeft >= 0L) "Days left until it expires"
            else "Expired. Days left (type a number to restart it)",
            value = daysLeftText,
            onValueChange = { raw -> daysLeftText = raw.filter { it.isDigit() }.take(4) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Warn me this many days before",
            value = reminders,
            onValueChange = {
                reminders = it.filter { c -> c.isDigit() || c == ',' || c == ' ' }.take(30)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Save",
                onClick = {
                    val parsed = reminders.split(',', ' ')
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { it in 1..3650 }
                        .distinct()
                        .sortedDescending()
                        .take(6)
                    val newDays = daysLeftText.toLongOrNull()
                    val newExpiry = if (daysLeftText != startDaysText && newDays != null) {
                        now + newDays * DAY_MILLIS
                    } else {
                        renewal.expiryMillis
                    }
                    onSave(
                        renewal.copy(
                            name = name.trim(),
                            amount = amountValue,
                            durationDays = durationCount,
                            expiryMillis = newExpiry,
                            reminderDays = parsed
                        )
                    )
                },
                enabled = name.isNotBlank() && durationCount > 0
            )
        }
    }
}
