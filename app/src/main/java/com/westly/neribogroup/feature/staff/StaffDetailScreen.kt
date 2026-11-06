package com.westly.neribogroup.feature.staff

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.Allowance
import com.westly.neribogroup.data.PayItem
import com.westly.neribogroup.data.SALARY_KEY
import com.westly.neribogroup.data.allowanceKey
import com.westly.neribogroup.data.Staff
import com.westly.neribogroup.feature.common.AddEntryDialog
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.MoneyRow
import com.westly.neribogroup.feature.common.NumberField
import com.westly.neribogroup.feature.common.SaveBar
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatColumn
import com.westly.neribogroup.feature.common.StatTile
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatMonth
import com.westly.neribogroup.ui.formatNaira

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffDetailScreen(
    staff: Staff,
    onBack: () -> Unit,
    onSave: (Staff) -> Unit,
    onPay: () -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(staff) }
    var menuOpen by remember { mutableStateOf(false) }
    var addAllowance by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmPay by remember { mutableStateOf(false) }
    var itemToPay by remember { mutableStateOf<PayItem?>(null) }

    val now = System.currentTimeMillis()
    val shown = if (editing) draft else staff

    BackHandler {
        if (editing) editing = false else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = shown.name.ifBlank { staff.name },
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
                                        draft = staff
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
                        onSave(draft.copy(name = draft.name.trim().ifBlank { staff.name }))
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
            item { SectionTitle("Details") }

            if (editing) {
                item {
                    TextInput("Full name", draft.name, KeyboardType.Text) {
                        draft = draft.copy(name = it.take(60))
                    }
                }
                item {
                    TextInput("Role", draft.role, KeyboardType.Text) {
                        draft = draft.copy(role = it.take(60))
                    }
                }
                item {
                    TextInput("Email", draft.email, KeyboardType.Email) {
                        draft = draft.copy(email = it.take(80))
                    }
                }
                item {
                    TextInput("Phone number", draft.phone, KeyboardType.Phone) {
                        draft = draft.copy(phone = it.take(20))
                    }
                }
                item {
                    TextInput("Bank name", draft.bankName, KeyboardType.Text) {
                        draft = draft.copy(bankName = it.take(60))
                    }
                }
                item {
                    TextInput("Account number", draft.accountNumber, KeyboardType.Number) { raw ->
                        draft = draft.copy(accountNumber = raw.filter { c -> c.isDigit() }.take(20))
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            InfoRow("Role", shown.role)
                            InfoRow("Email", shown.email)
                            InfoRow("Phone", shown.phone)
                            InfoRow("Bank", shown.bankName)
                            InfoRow("Account number", shown.accountNumber)
                        }
                    }
                }
            }

            item { SectionTitle("Monthly pay") }

            if (editing) {
                item {
                    NumberField("Monthly salary", draft.monthlySalary, {
                        draft = draft.copy(monthlySalary = it)
                    })
                }
            } else {
                item {
                    val salaryItem = shown.payItems().firstOrNull { it.key == SALARY_KEY }
                    FlatCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatColumn(
                                label = "Salary",
                                value = formatNaira(shown.monthlySalary),
                                modifier = Modifier.weight(1f)
                            )
                            if (salaryItem != null) {
                                PayControl(
                                    paid = shown.isItemPaid(SALARY_KEY, now),
                                    onPay = { itemToPay = salaryItem }
                                )
                            }
                        }
                        val salaryPayment = shown.itemPayment(SALARY_KEY, now)
                        if (salaryItem != null && salaryPayment != null) {
                            Text(
                                text = "Paid on " + formatDate(salaryPayment.dateMillis),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            item { SectionTitle("Allowances") }

            if (shown.allowances.isEmpty()) {
                item {
                    Text(
                        text = "No allowances yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(shown.allowances, key = { it.id }) { allowance ->
                if (editing) {
                    MoneyRow(
                        title = allowance.name,
                        subtitle = null,
                        amountText = formatNaira(allowance.amount),
                        editing = true,
                        onDelete = {
                            draft = draft.copy(
                                allowances = draft.allowances.filter { it.id != allowance.id }
                            )
                        }
                    )
                } else {
                    val key = allowanceKey(allowance.id)
                    val payment = shown.itemPayment(key, now)
                    val payable = allowance.amount > 0L
                    PayableRow(
                        title = allowance.name,
                        subtitle = if (payable && payment != null) {
                            "Paid on " + formatDate(payment.dateMillis)
                        } else {
                            null
                        },
                        amountText = formatNaira(allowance.amount),
                        showPay = payable,
                        paid = payment != null,
                        onPay = {
                            itemToPay = PayItem(key, allowance.name, allowance.amount)
                        }
                    )
                }
            }

            if (editing) {
                item {
                    Button(
                        onClick = { addAllowance = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add allowance") }
                }
            }

            item {
                StatTile(
                    label = "Total per month",
                    value = formatNaira(shown.monthlyTotal),
                    modifier = Modifier.fillMaxWidth(),
                    valueColor = MaterialTheme.colorScheme.primary
                )
            }

            if (!editing) {
                item {
                    if (shown.isPaidIn(now)) {
                        Text(
                            text = "Paid for " + formatMonth(now),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Button(
                            onClick = { confirmPay = true },
                            enabled = shown.outstandingItems(now).isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (shown.paidInMonth(now) > 0L) {
                                    "Mark the rest as paid for " + formatMonth(now)
                                } else {
                                    "Mark as paid for " + formatMonth(now)
                                }
                            )
                        }
                    }
                }
            }

            item { SectionTitle("Payment history") }

            if (shown.payments.isEmpty()) {
                item {
                    Text(
                        text = "No payments recorded yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(shown.payments.sortedByDescending { it.dateMillis }, key = { it.id }) { payment ->
                MoneyRow(
                    title = formatMonth(payment.dateMillis) +
                        if (payment.itemName.isBlank()) "" else " - " + payment.itemName,
                    subtitle = "Paid on " + formatDate(payment.dateMillis),
                    amountText = formatNaira(payment.amount),
                    editing = editing,
                    onDelete = {
                        draft = draft.copy(
                            payments = draft.payments.filter { it.id != payment.id }
                        )
                    }
                )
            }

            if (editing) {
                item {
                    TextButton(
                        onClick = { confirmDelete = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete this staff member", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (addAllowance) {
        AddEntryDialog(
            heading = "Add allowance",
            onDismiss = { addAllowance = false },
            onAdd = { title, amount ->
                draft = draft.copy(
                    allowances = draft.allowances + Allowance(name = title, amount = amount)
                )
                addAllowance = false
            }
        )
    }

    if (confirmPay) {
        ConfirmDialog(
            heading = "Mark as paid?",
            message = "Record a payment of " + formatNaira(staff.outstandingTotal(now)) + " to " +
                staff.name + " for " + formatMonth(now) + ".",
            confirmLabel = "Mark as paid",
            onConfirm = {
                confirmPay = false
                onPay()
            },
            onDismiss = { confirmPay = false }
        )
    }

    itemToPay?.let { item ->
        ConfirmDialog(
            heading = "Mark as paid?",
            message = "Record a payment of " + formatNaira(item.amount) + " to " +
                staff.name + " for " + item.name + ", " + formatMonth(now) + ".",
            confirmLabel = "Mark as paid",
            onConfirm = {
                itemToPay = null
                onSave(staff.paidItem(item.key, System.currentTimeMillis()))
            },
            onDismiss = { itemToPay = null }
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            heading = "Delete ${staff.name}?",
            message = "This removes the staff member and their payment history right away.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** The pay control on the right of a pay row: a "Mark paid" button, or a green "Paid" status once paid. */
@Composable
private fun PayControl(paid: Boolean, onPay: () -> Unit) {
    if (paid) {
        StatusDot(text = "Paid", color = MaterialTheme.colorScheme.primary)
    } else {
        TextButton(onClick = onPay) { Text("Mark paid") }
    }
}

/** One allowance row in normal view: name, optional paid date, amount and its own pay control. */
@Composable
private fun PayableRow(
    title: String,
    subtitle: String?,
    amountText: String,
    showPay: Boolean,
    paid: Boolean,
    onPay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(text = amountText, style = MaterialTheme.typography.titleSmall)
        if (showPay) {
            Spacer(modifier = Modifier.width(8.dp))
            PayControl(paid = paid, onPay = onPay)
        }
    }
}
