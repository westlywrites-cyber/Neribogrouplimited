package com.westly.neribogroup.feature.hosting

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.HOSTING_APP_ID
import com.westly.neribogroup.data.HostingCustomer
import com.westly.neribogroup.data.HostingEngine
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.StorageRules
import com.westly.neribogroup.data.StorageUnit
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FieldGap
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatCard
import com.westly.neribogroup.feature.common.StatCardItem
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatMoney
import com.westly.neribogroup.ui.formatNaira

/**
 * Client Hosting: the customers you host. You paste their details, give them storage and a price, and type how
 * often they renew. When you tap Renew, the next period starts and the price goes into the company income.
 * A customer who does not renew is only flagged; nothing is stopped for you.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HostingScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onClock: (Long) -> Unit,
    onAdd: (HostingCustomer, Boolean) -> Unit,
    onEdit: (HostingCustomer) -> Unit,
    onRenew: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val now = rememberNowMillis()
    val eff = maxOf(now, ledger.hosting.clockMillis)
    val rate = HostingEngine.ngnPerUsd(ledger)
    val customers = ledger.hosting.customers.sortedBy { it.expiryMillis }
    val app = ledger.apps.firstOrNull { it.id == HOSTING_APP_ID }
    val overdue = HostingEngine.overdueCount(ledger, eff)

    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HostingCustomer?>(null) }
    var renewing by remember { mutableStateOf<HostingCustomer?>(null) }
    var deleting by remember { mutableStateOf<HostingCustomer?>(null) }

    BackHandler { onBack() }
    LaunchedEffect(now) { onClock(System.currentTimeMillis()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Client Hosting",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "summary") {
                val stats = ArrayList<StatCardItem>()
                stats.add(StatCardItem("Customers", customers.size.toString()))
                stats.add(StatCardItem("Every period", formatNaira(HostingEngine.monthlyNgn(ledger))))
                stats.add(StatCardItem("Storage given", StorageRules.textMb(HostingEngine.storageGivenMb(ledger))))
                stats.add(StatCardItem("Earned from renewals", formatNaira(app?.income ?: 0L)))
                if (overdue > 0) {
                    stats.add(StatCardItem("Overdue", overdue.toString(), MaterialTheme.colorScheme.error))
                }
                StatCard(items = stats, columns = 2)
            }

            item(key = "add") {
                TintedButton(
                    text = "Add customer",
                    onClick = { showAdd = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item(key = "title") { SectionTitle("Customers") }

            if (customers.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No customers yet. When you accept a proposal, tap Add customer, paste their " +
                            "details, give them storage and a price.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(customers, key = { "customer-" + it.id }) { customer ->
                CustomerCard(
                    customer = customer,
                    now = eff,
                    ngnPerUsd = rate,
                    onRenew = { renewing = customer },
                    onEdit = { editing = customer },
                    onDelete = { deleting = customer }
                )
            }

            item(key = "end") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (showAdd) {
        CustomerDialog(
            existing = null,
            onDismiss = { showAdd = false },
            onSave = { customer, paidNow ->
                showAdd = false
                onAdd(customer, paidNow)
            }
        )
    }

    editing?.let { current ->
        CustomerDialog(
            existing = current,
            onDismiss = { editing = null },
            onSave = { customer, _ ->
                editing = null
                onEdit(customer)
            }
        )
    }

    renewing?.let { current ->
        val newDate = current.renewedAt(eff).expiryMillis
        val priceText = priceLine(current, rate)
        ConfirmDialog(
            heading = "Renew " + current.name + "?",
            message = priceText + " is added to the company income now, and the next renewal is " +
                formatDate(newDate) + ".",
            confirmLabel = "Renew",
            onConfirm = {
                renewing = null
                onRenew(current.id)
            },
            onDismiss = { renewing = null }
        )
    }

    deleting?.let { current ->
        ConfirmDialog(
            heading = "Delete customer?",
            message = "Delete " + current.name + " from Client Hosting? Income already added to the company " +
                "stays. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                deleting = null
                onDelete(current.id)
            },
            onDismiss = { deleting = null },
            destructive = true
        )
    }
}

/** "₦25,000" or "$20 ≈ ₦30,000 at ₦1,500/$". */
private fun priceLine(customer: HostingCustomer, ngnPerUsd: Double): String {
    if (customer.currency != "USD") return formatMoney(customer.priceUnits, "NGN")
    val ngn = HostingEngine.ngnFor(customer.priceUnits, "USD", ngnPerUsd)
    return formatMoney(customer.priceUnits, "USD") + " ≈ " + formatNaira(ngn) +
        " at " + formatNaira(Math.round(ngnPerUsd)) + "/\$"
}

@Composable
private fun CustomerCard(
    customer: HostingCustomer,
    now: Long,
    ngnPerUsd: Double,
    onRenew: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val left = customer.daysLeft(now)
    val level = customer.alertLevel(now)
    val statusColor: Color = when (level) {
        2 -> MaterialTheme.colorScheme.error
        1 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    FlatCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = customer.name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            StatusDot(text = HostingEngine.statusText(left), color = statusColor)
        }

        val contact = listOf(customer.contactName, customer.email, customer.phone)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        if (contact.isNotEmpty()) {
            Text(
                text = contact,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (customer.notes.isNotBlank()) {
            Text(
                text = customer.notes,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = priceLine(customer, ngnPerUsd) + " every " + customer.durationDays +
                (if (customer.durationDays == 1) " day" else " days"),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = StorageRules.textMb(customer.storageMb) + " storage · renews " +
                formatDate(customer.expiryMillis),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = if (customer.paidCount == 0) {
                "No renewal recorded yet"
            } else {
                "Renewed " + customer.paidCount + (if (customer.paidCount == 1) " time" else " times") +
                    " · last " + formatDate(customer.paidMillis)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        TintedButton(
            text = "Renew",
            onClick = onRenew,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onDelete) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/** Add or edit one customer. Paste their details at the top and the fields fill themselves. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDialog(
    existing: HostingCustomer?,
    onDismiss: () -> Unit,
    onSave: (HostingCustomer, Boolean) -> Unit
) {
    val isEdit = existing != null
    var paste by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var contact by remember { mutableStateOf(existing?.contactName ?: "") }
    var email by remember { mutableStateOf(existing?.email ?: "") }
    var phone by remember { mutableStateOf(existing?.phone ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }
    val startUnit = if (existing != null) StorageRules.bestUnit(existing.storageMb) else StorageUnit.GB
    var unit by remember { mutableStateOf(startUnit) }
    var storageText by remember {
        mutableStateOf(if (existing != null) (existing.storageMb / startUnit.mb).toString() else "")
    }
    var currency by remember { mutableStateOf(existing?.currency ?: "NGN") }
    var priceText by remember { mutableStateOf(existing?.priceUnits?.toString() ?: "") }
    var daysText by remember { mutableStateOf((existing?.durationDays ?: 30).toString()) }
    var remindersText by remember {
        mutableStateOf((existing?.reminderDays ?: listOf(7, 2)).joinToString(", "))
    }
    var paidNow by remember { mutableStateOf(false) }

    val storageMb = StorageRules.toMb(storageText.toLongOrNull() ?: 0L, unit)
    val price = priceText.toLongOrNull() ?: 0L
    val days = daysText.toIntOrNull() ?: 0
    val valid = name.isNotBlank() && storageMb > 0L && price > 0L && days in 1..3650

    NeriboDialog(
        onDismiss = onDismiss,
        title = if (isEdit) "Edit customer" else "Add customer"
    ) {
        if (!isEdit) {
            LabeledField(
                label = "Paste customer details",
                value = paste,
                onValueChange = { paste = it.take(600) },
                singleLine = false,
                minLines = 3
            )
            Spacer(modifier = Modifier.height(8.dp))
            TintedButton(
                text = "Fill the fields from this text",
                onClick = {
                    val parsed = HostingEngine.parsePasted(paste)
                    if (parsed.name.isNotEmpty()) name = parsed.name
                    if (parsed.email.isNotEmpty()) email = parsed.email
                    if (parsed.phone.isNotEmpty()) phone = parsed.phone
                    if (parsed.notes.isNotEmpty()) notes = parsed.notes
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = paste.isNotBlank()
            )
            Spacer(modifier = Modifier.height(FieldGap))
        }

        LabeledField(
            label = "Company or customer name",
            value = name,
            onValueChange = { name = it.take(60) }
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Contact person (optional)",
            value = contact,
            onValueChange = { contact = it.take(60) }
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Email (optional)",
            value = email,
            onValueChange = { email = it.take(80) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Phone (optional)",
            value = phone,
            onValueChange = { phone = it.take(25) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "What you built or host for them (optional)",
            value = notes,
            onValueChange = { notes = it.take(300) },
            singleLine = false,
            minLines = 2
        )

        Spacer(modifier = Modifier.height(FieldGap))
        Text(
            text = "Storage for this customer",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (u in StorageUnit.values()) {
                FilterChip(
                    selected = unit == u,
                    onClick = { unit = u },
                    label = { Text(u.label) }
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LabeledField(
            label = "Storage (" + unit.label + ")",
            value = storageText,
            onValueChange = { raw -> storageText = raw.filter { it.isDigit() }.take(6) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Text(
            text = "= " + StorageRules.textMb(storageMb),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(FieldGap))
        Text(
            text = "Price per renewal",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = currency == "NGN",
                onClick = { currency = "NGN" },
                label = { Text("₦ Naira") }
            )
            FilterChip(
                selected = currency == "USD",
                onClick = { currency = "USD" },
                label = { Text("\$ Dollar") }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        LabeledField(
            label = "Price (" + (if (currency == "USD") "\$" else "₦") + ")",
            value = priceText,
            onValueChange = { raw -> priceText = raw.filter { it.isDigit() }.take(12) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        if (currency == "USD") {
            Text(
                text = "A \$ price is added to the company income in naira at the rate of the moment you tap Renew.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Renews every how many days?",
            value = daysText,
            onValueChange = { raw -> daysText = raw.filter { it.isDigit() }.take(4) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(modifier = Modifier.height(FieldGap))
        LabeledField(
            label = "Warn me this many days before",
            value = remindersText,
            onValueChange = {
                remindersText = it.filter { c -> c.isDigit() || c == ',' || c == ' ' }.take(30)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        if (!isEdit) {
            Spacer(modifier = Modifier.height(FieldGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "First period already paid (add it to the income now)",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(checked = paidNow, onCheckedChange = { paidNow = it })
            }
        }

        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = if (isEdit) "Save" else "Add",
                onClick = {
                    val reminders = remindersText.split(',', ' ')
                        .mapNotNull { it.trim().toIntOrNull() }
                        .filter { it in 1..3650 }
                        .distinct()
                        .sortedDescending()
                        .take(6)
                    val base = existing ?: HostingCustomer(
                        name = name.trim(),
                        storageMb = storageMb,
                        currency = currency,
                        priceUnits = price,
                        addedMillis = 0L,
                        paidMillis = 0L,
                        expiryMillis = 0L
                    )
                    onSave(
                        base.copy(
                            name = name.trim(),
                            contactName = contact.trim(),
                            email = email.trim(),
                            phone = phone.trim(),
                            notes = notes.trim(),
                            storageMb = storageMb,
                            currency = currency,
                            priceUnits = price,
                            durationDays = days,
                            reminderDays = reminders
                        ),
                        paidNow
                    )
                },
                enabled = valid
            )
        }
    }
}
