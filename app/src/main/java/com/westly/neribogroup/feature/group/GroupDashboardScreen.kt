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
import androidx.compose.material3.OutlinedButton
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
import com.westly.neribogroup.data.DAY_MILLIS
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.Renewal
import com.westly.neribogroup.data.Staff
import com.westly.neribogroup.feature.common.AddEntryDialog
import com.westly.neribogroup.feature.common.AddRenewalDialog
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.EntryRow
import com.westly.neribogroup.feature.common.NameDialog
import com.westly.neribogroup.feature.common.SaveBar
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatTile
import com.westly.neribogroup.feature.common.profitColor
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatFullDate
import com.westly.neribogroup.ui.formatMonth
import com.westly.neribogroup.ui.formatNaira

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDashboardScreen(
    ledger: Ledger,
    onOpenApp: (String) -> Unit,
    onOpenStaff: (String) -> Unit,
    onPayAllStaff: () -> Unit,
    onRenew: (String) -> Unit,
    onSave: (Ledger) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ledger) }
    var menuOpen by remember { mutableStateOf(false) }
    var showAddApp by remember { mutableStateOf(false) }
    var showAddCost by remember { mutableStateOf(false) }
    var showAddStaff by remember { mutableStateOf(false) }
    var appToDelete by remember { mutableStateOf<AppData?>(null) }
    var staffToDelete by remember { mutableStateOf<Staff?>(null) }
    var confirmPayAll by remember { mutableStateOf(false) }
    var showAddRenewal by remember { mutableStateOf(false) }
    var renewalToDelete by remember { mutableStateOf<Renewal?>(null) }
    var renewalToRenew by remember { mutableStateOf<Renewal?>(null) }
    var confirmImport by remember { mutableStateOf(false) }

    val now = System.currentTimeMillis()
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

            val attention = shown.renewalsNeedingAttention(now)
            if (attention.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (attention.size == 1) "1 renewal needs attention"
                                else attention.size.toString() + " renewals need attention",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            for (r in attention.take(4)) {
                                Text(
                                    text = r.name + " · " + renewalStatus(r.daysLeft(now)),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
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
                StatTile(
                    label = "Subscription income today",
                    value = formatNaira(shown.subscriptionIncomeToday(now)),
                    modifier = Modifier.fillMaxWidth(),
                    valueColor = MaterialTheme.colorScheme.primary
                )
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

            item { SectionTitle("Staff") }

            item {
                Text(
                    text = "Monthly payroll " + formatNaira(shown.monthlyPayroll) +
                        ". Paid in " + formatMonth(now) + ": " +
                        formatNaira(shown.staffPaidInMonth(now)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            }

            items(shown.staff, key = { it.id }) { person ->
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

            item { SectionTitle("Renewals") }

            if (shown.renewals.isEmpty()) {
                item {
                    Text(
                        text = "Nothing to track yet. Tap the three dots, choose Edit, then Add renewal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(shown.renewals.sortedBy { it.daysLeft(now) }, key = { it.id }) { item ->
                RenewalCard(
                    renewal = item,
                    now = now,
                    editing = editing,
                    onRenew = { renewalToRenew = item },
                    onDelete = { renewalToDelete = item }
                )
            }

            if (editing) {
                item {
                    Button(
                        onClick = { showAddRenewal = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add renewal") }
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
            message = "Record " + formatNaira(unpaid.sumOf { it.monthlyTotal }) + " for " +
                unpaid.size + " staff for " + formatMonth(now) + ".",
            confirmLabel = "Pay all",
            onConfirm = {
                confirmPayAll = false
                onPayAllStaff()
            },
            onDismiss = { confirmPayAll = false }
        )
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

    renewalToRenew?.let { item ->
        ConfirmDialog(
            heading = "Renew ${item.name}?",
            message = "Adds " + item.durationDays + " more days" +
                if (item.amount > 0L) " and records " + formatNaira(item.amount) + " as a company cost." else ".",
            confirmLabel = "Renew",
            onConfirm = {
                renewalToRenew = null
                onRenew(item.id)
            },
            onDismiss = { renewalToRenew = null }
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
                    " + company costs " + formatNaira(ledger.companyExpenses) +
                    " + staff pay " + formatNaira(ledger.staffPaid),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun renewalStatus(left: Long): String = when {
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
                    OutlinedButton(onClick = onRenew) { Text("Renew") }
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
                        formatCount(app.newUsersToday(System.currentTimeMillis())) + " new today",
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
