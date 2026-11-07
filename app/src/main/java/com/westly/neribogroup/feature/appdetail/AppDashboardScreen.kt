package com.westly.neribogroup.feature.appdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.data.Plan
import com.westly.neribogroup.feature.common.AddEntryDialog
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.EntryRow
import com.westly.neribogroup.feature.common.NumberField
import com.westly.neribogroup.feature.common.SaveBar
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatTile
import com.westly.neribogroup.feature.common.profitColor
import com.westly.neribogroup.feature.common.rememberNowMillis
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
    var addUser by remember { mutableStateOf(false) }
    var editUserId by remember { mutableStateOf<String?>(null) }
    // The user whose plan end date was tapped, to say if they renewed.
    var expiryUserId by remember { mutableStateOf<String?>(null) }
    // Goes up when a user change moves the Premium or Pro numbers, so those boxes refresh.
    var countsVersion by remember { mutableStateOf(0) }
    // The "new users today" pop-up sequence: 0 means not running.
    var seqTotal by rememberSaveable { mutableStateOf(0) }
    var seqIndex by rememberSaveable { mutableStateOf(1) }
    val latestApp by rememberUpdatedState(app)

    val now = rememberNowMillis()
    val shown = if (editing) draft else app
    val activeDays = activeUsersWeek(shown, now)

    // What this save would add automatically, shown live while editing.
    val premiumAdded = (draft.premiumUsers - app.premiumUsers).coerceAtLeast(0L)
    val proAdded = (draft.proUsers - app.proUsers).coerceAtLeast(0L)
    val autoIncome = premiumAdded * draft.premiumPrice + proAdded * draft.proPrice

    BackHandler {
        if (editing) editing = false else onBack()
    }
    BackHandler(enabled = seqTotal > 0) {
        seqTotal = 0
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
                                        draft = app.forEditing(now)
                                        addUser = false
                                        editUserId = null
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
                        val saveNow = System.currentTimeMillis()
                        val newUsers = draft.dailyNewUsers - app.newUsersToday(saveNow)
                        onSave(draft.copy(name = draft.name.trim().ifBlank { app.name }))
                        editing = false
                        if (newUsers > 0L) {
                            seqTotal = minOf(newUsers, 100L).toInt()
                            seqIndex = 1
                        }
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
                    key(countsVersion) {
                        NumberField("Total users", draft.totalUsers, {
                            draft = draft.copy(totalUsers = it)
                        })
                    }
                }
                item {
                    key(countsVersion) {
                        NumberField("Premium users", draft.premiumUsers, {
                            draft = draft.copy(premiumUsers = it)
                        })
                    }
                }
                item {
                    key(countsVersion) {
                        NumberField("Pro users", draft.proUsers, {
                            draft = draft.copy(proUsers = it)
                        })
                    }
                }
                item {
                    NumberField("New users today", draft.dailyNewUsers, { value ->
                        // New users also raise Total users by the same amount.
                        val change = value - draft.dailyNewUsers
                        draft = draft.copy(
                            dailyNewUsers = value,
                            totalUsers = (draft.totalUsers + change).coerceAtLeast(0L)
                        )
                        countsVersion++
                    })
                }
                item {
                    Text(
                        text = "New users today are added to Total users for you. " +
                            "After saving you can pick Free, Premium or Pro for each one. " +
                            "New users today goes back to 0 at 12 am.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    NumberField("Active users today", draft.activeUsers, {
                        draft = draft.copy(activeUsers = it)
                    })
                }
                item {
                    Text(
                        text = "Active users today goes back to 0 at 12 am. " +
                            "Each day is kept in the last 7 days chart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { SectionTitle("Subscription prices") }
                item {
                    NumberField("Premium price (₦ per subscription)", draft.premiumPrice, {
                        draft = draft.copy(premiumPrice = it)
                    })
                }
                item {
                    NumberField("Pro price (₦ per subscription)", draft.proPrice, {
                        draft = draft.copy(proPrice = it)
                    })
                }
                item {
                    Text(
                        text = if (autoIncome > 0L) {
                            "Saving now adds " + formatNaira(autoIncome) + " income: " +
                                formatCount(premiumAdded) + " Premium and " +
                                formatCount(proAdded) + " Pro new subscriptions."
                        } else {
                            "Raise Premium or Pro users and the app adds the income for you when you save. " +
                                "Premium today and Pro today are counted for you."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Total users", formatCount(shown.totalUsers), Modifier.weight(1f))
                        StatTile("New today", formatCount(shown.newUsersToday(now)), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Premium", formatCount(shown.premiumUsers), Modifier.weight(1f))
                        StatTile("Pro", formatCount(shown.proUsers), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Premium today", formatCount(shown.premiumTodayAt(now)), Modifier.weight(1f))
                        StatTile("Pro today", formatCount(shown.proTodayAt(now)), Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Premium price", formatNaira(shown.premiumPrice), Modifier.weight(1f))
                        StatTile("Pro price", formatNaira(shown.proPrice), Modifier.weight(1f))
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
            }

            if (!editing) {
                item { SectionTitle("Activity") }
                item {
                    ActiveUsersCard(
                        activeToday = shown.activeUsersAt(now),
                        totalUsers = shown.totalUsers,
                        days = activeDays
                    )
                }

                item { SectionTitle("Plan expiry") }

                val paying = shown.users
                    .filter { it.plan != Plan.FREE }
                    .sortedWith(
                        compareBy<AppUser> { it.planDaysLeft(now) == null }
                            .thenBy { it.planExpiryMillis }
                    )
                val endingSoon = paying.count { user ->
                    val left = user.planDaysLeft(now)
                    left != null && left <= 7L
                }

                item {
                    Text(
                        text = when {
                            paying.isEmpty() ->
                                "No Premium or Pro users yet. Their plan end dates show here."
                            endingSoon == 0 ->
                                "No plans end in the next 7 days. Tap a user to say if they renewed."
                            endingSoon == 1 ->
                                "1 plan ends in the next 7 days. Tap a user to say if they renewed."
                            else ->
                                "$endingSoon plans end in the next 7 days. Tap a user to say if they renewed."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(paying.take(15), key = { "expiry-" + it.id }) { user ->
                    ExpiryRow(
                        user = user,
                        now = now,
                        onClick = { expiryUserId = user.id }
                    )
                }

                if (paying.size > 15) {
                    item {
                        Text(
                            text = "Showing the 15 soonest of " + paying.size + ".",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item { SectionTitle("Plan benefits") }

            if (editing) {
                item {
                    OutlinedTextField(
                        value = draft.freeBenefits,
                        onValueChange = { draft = draft.copy(freeBenefits = it.take(500)) },
                        label = { Text("What Free includes") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.premiumBenefits,
                        onValueChange = { draft = draft.copy(premiumBenefits = it.take(500)) },
                        label = { Text("What Premium includes") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = draft.proBenefits,
                        onValueChange = { draft = draft.copy(proBenefits = it.take(500)) },
                        label = { Text("What Pro includes") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                item { BenefitsBlock("Free includes", shown.freeBenefits) }
                item { BenefitsBlock("Premium includes", shown.premiumBenefits) }
                item { BenefitsBlock("Pro includes", shown.proBenefits) }
            }

            item { SectionTitle("App users") }

            item {
                val count = shown.users.size
                Text(
                    text = when (count) {
                        0 -> "No users recorded yet."
                        1 -> "1 user recorded"
                        else -> formatCount(count.toLong()) + " users recorded"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(shown.users.reversed().sortedByDescending { it.addedMillis }, key = { it.id }) { user ->
                UserRow(
                    user = user,
                    editing = editing,
                    onClick = { editUserId = user.id }
                )
            }

            if (editing) {
                item {
                    Button(
                        onClick = { addUser = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add user") }
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

    if (addUser) {
        UserDialog(
            heading = "Add user",
            initial = null,
            otherUsers = draft.users,
            onSave = { name, email, phone, plan ->
                draft = draft.withUserAdded(
                    AppUser(
                        name = name,
                        email = email,
                        phone = phone,
                        plan = plan,
                        addedMillis = System.currentTimeMillis()
                    )
                )
                countsVersion++
                addUser = false
            },
            onDismiss = { addUser = false }
        )
    }

    val userBeingEdited = editUserId?.let { id -> draft.users.firstOrNull { it.id == id } }
    if (editing && userBeingEdited != null) {
        key(userBeingEdited.id) {
            UserDialog(
                heading = "Edit user",
                initial = userBeingEdited,
                otherUsers = draft.users.filter { it.id != userBeingEdited.id },
                onSave = { name, email, phone, plan ->
                    draft = draft.withUserUpdated(
                        userBeingEdited.copy(
                            name = name,
                            email = email,
                            phone = phone,
                            plan = plan
                        )
                    )
                    countsVersion++
                    editUserId = null
                },
                onDismiss = { editUserId = null },
                onDelete = {
                    draft = draft.withUserRemoved(userBeingEdited.id)
                    countsVersion++
                    editUserId = null
                }
            )
        }
    }

    if (seqTotal > 0) {
        val isLast = seqIndex >= seqTotal
        val next: () -> Unit = {
            if (seqIndex >= seqTotal) {
                seqTotal = 0
            } else {
                seqIndex += 1
            }
        }
        key(seqIndex) {
            UserDialog(
                heading = "Add user $seqIndex of $seqTotal",
                initial = null,
                otherUsers = latestApp.users,
                saveLabel = if (isLast) "Save and finish" else "Save and next",
                onSave = { name, email, phone, plan ->
                    // Built from the latest saved app so no earlier user or count is lost.
                    onSave(
                        latestApp.withUserAdded(
                            AppUser(
                                name = name,
                                email = email,
                                phone = phone,
                                plan = plan,
                                addedMillis = System.currentTimeMillis()
                            )
                        )
                    )
                    next()
                },
                onDismiss = { seqTotal = 0 },
                onSkip = { next() },
                onStop = { seqTotal = 0 }
            )
        }
    }

    val expiryUser = expiryUserId?.let { id -> app.users.firstOrNull { it.id == id } }
    if (!editing && expiryUser != null) {
        key(expiryUser.id) {
            RenewPlanDialog(
                user = expiryUser,
                price = if (expiryUser.plan == Plan.PRO) app.proPrice else app.premiumPrice,
                now = now,
                onRenewed = {
                    onSave(
                        app.forEditing(System.currentTimeMillis())
                            .withUserRenewed(expiryUser.id, System.currentTimeMillis())
                    )
                    expiryUserId = null
                },
                onNotRenewed = {
                    onSave(
                        app.forEditing(System.currentTimeMillis())
                            .withPlanLapsed(expiryUser.id)
                    )
                    expiryUserId = null
                },
                onDismiss = { expiryUserId = null }
            )
        }
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

/** One block in the Plan benefits section (read-only view). */
@Composable
private fun BenefitsBlock(label: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            if (text.isBlank()) {
                Text(
                    text = "Not written yet. Tap the three dots, choose Edit, then write it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/** One user: name, email, phone (if given) and a small plan label. Tappable only while editing. */
@Composable
private fun UserRow(user: AppUser, editing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (editing) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = user.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = user.email,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (user.phone.isNotBlank()) {
                Text(
                    text = user.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = planText(user.plan),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (user.plan == Plan.FREE) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
