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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.CampaignSize
import com.westly.neribogroup.data.Plan
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
    onDelete: () -> Unit,
    onRoll: () -> Unit,
    onWithdraw: () -> Unit,
    onStartCampaign: (CampaignSize, Int, Int) -> Unit,
    onStopCampaign: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(app) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addUser by remember { mutableStateOf(false) }
    var editUserId by remember { mutableStateOf<String?>(null) }
    // The user (added by you) whose plan end date was tapped, to say if they renewed.
    var expiryUserId by remember { mutableStateOf<String?>(null) }

    val now = rememberNowMillis()
    val shown = if (editing) draft else app
    val activeDays = activeUsersWeek(shown, now)

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
            if (editing) {
                // -------------------------------------------------------------------------
                // Edit mode: only settings and the users you add yourself.
                // -------------------------------------------------------------------------
                item { SectionTitle("App settings") }
                item {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it.take(40)) },
                        label = { Text("App name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                        text = "Leave a price empty if that plan does not exist in this app. " +
                            "A plan with no price is hidden, cannot be given to users and is not rolled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item { SectionTitle("Google Ads") }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Google Ads in this app",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Switch(
                            checked = draft.adsEnabled,
                            onCheckedChange = { draft = draft.copy(adsEnabled = it) }
                        )
                    }
                }
                item {
                    Text(
                        text = "When on, the daily roll adds ad money based on how many Free users " +
                            "the app has. Google keeps part of it and the rest goes to the Google Play Balance.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item { SectionTitle("Plan benefits") }
                item {
                    OutlinedTextField(
                        value = draft.freeBenefits,
                        onValueChange = { draft = draft.copy(freeBenefits = it.take(500)) },
                        label = { Text("What Free includes") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (draft.premiumPrice > 0L) {
                    item {
                        OutlinedTextField(
                            value = draft.premiumBenefits,
                            onValueChange = { draft = draft.copy(premiumBenefits = it.take(500)) },
                            label = { Text("What Premium includes") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (draft.proPrice > 0L) {
                    item {
                        OutlinedTextField(
                            value = draft.proBenefits,
                            onValueChange = { draft = draft.copy(proBenefits = it.take(500)) },
                            label = { Text("What Pro includes") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                item { SectionTitle("Users you added") }
                item {
                    val diceUsers = draft.users.count { it.simulated }
                    Text(
                        text = if (diceUsers > 0) {
                            formatCount(diceUsers.toLong()) +
                                " users were made by the dice. They cannot be edited. " +
                                "Users you add yourself record no money."
                        } else {
                            "Users you add yourself record no money. " +
                                "They are for your own accounts."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(
                    draft.users.filter { !it.simulated }.sortedByDescending { it.addedMillis },
                    key = { "mine-" + it.id }
                ) { user ->
                    UserRow(user = user, clickable = true, onClick = { editUserId = user.id })
                }
                item {
                    Button(
                        onClick = { addUser = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Add user") }
                }

                if (draft.entries.isNotEmpty()) {
                    item { SectionTitle("Income and expenses") }
                    items(draft.entries.sortedByDescending { it.dateMillis }, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            editing = true,
                            onDelete = {
                                draft = draft.copy(entries = draft.entries.filter { it.id != entry.id })
                            }
                        )
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
            } else {
                // -------------------------------------------------------------------------
                // View mode.
                // -------------------------------------------------------------------------
                item { SectionTitle("Daily dice") }
                item { DicePanel(app = app, now = now, onRoll = onRoll) }

                item { SectionTitle("Campaign") }
                item {
                    CampaignCard(
                        app = app,
                        now = now,
                        onStart = onStartCampaign,
                        onStop = onStopCampaign
                    )
                }

                item { SectionTitle("Users") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Total users", formatCount(app.totalUsers), Modifier.weight(1f))
                        StatTile("Free", formatCount(app.freeUsers), Modifier.weight(1f))
                        StatTile("New today", formatCount(app.newUsersToday(now)), Modifier.weight(1f))
                    }
                }
                if (app.premiumPrice > 0L || app.proPrice > 0L) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (app.premiumPrice > 0L) {
                                StatTile("Premium", formatCount(app.premiumUsers), Modifier.weight(1f))
                            }
                            if (app.proPrice > 0L) {
                                StatTile("Pro", formatCount(app.proUsers), Modifier.weight(1f))
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (app.premiumPrice > 0L) {
                                StatTile(
                                    "Premium today",
                                    formatCount(app.premiumTodayAt(now)),
                                    Modifier.weight(1f)
                                )
                            }
                            if (app.proPrice > 0L) {
                                StatTile(
                                    "Pro today",
                                    formatCount(app.proTodayAt(now)),
                                    Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (app.premiumPrice > 0L) {
                                StatTile("Premium price", formatNaira(app.premiumPrice), Modifier.weight(1f))
                            }
                            if (app.proPrice > 0L) {
                                StatTile("Pro price", formatNaira(app.proPrice), Modifier.weight(1f))
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "This app has no paid plans. Set a Premium or Pro price in Edit to add them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item { SectionTitle("Activity") }
                item {
                    ActiveUsersCard(
                        activeToday = app.activeUsersAt(now),
                        totalUsers = app.totalUsers,
                        days = activeDays
                    )
                }

                item { SectionTitle("Google Play Balance") }
                item {
                    StatTile(
                        label = "Earned today (after Google's cut)",
                        value = formatNaira(app.playEarningsToday(now)),
                        modifier = Modifier.fillMaxWidth(),
                        valueColor = MaterialTheme.colorScheme.primary
                    )
                }
                item { PlayAppCard(app = app, onWithdraw = onWithdraw, showName = false) }

                val paying = app.users
                    .filter { it.plan != Plan.FREE }
                    .sortedWith(
                        compareBy<AppUser> { it.planDaysLeft(now) == null }
                            .thenBy { it.planExpiryMillis }
                    )
                if (paying.isNotEmpty()) {
                    item { SectionTitle("Plan expiry") }

                    val endingSoon = paying.count { user ->
                        val left = user.planDaysLeft(now)
                        left != null && left <= 7L
                    }
                    item {
                        val soonText = when (endingSoon) {
                            0 -> "No plans end in the next 7 days."
                            1 -> "1 plan ends in the next 7 days."
                            else -> "$endingSoon plans end in the next 7 days."
                        }
                        Text(
                            text = soonText + " Tap a user you added to say if they renewed. " +
                                "Users made by the dice renew or leave by themselves.",
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
                item { BenefitsBlock("Free includes", app.freeBenefits) }
                if (app.premiumPrice > 0L) {
                    item { BenefitsBlock("Premium includes", app.premiumBenefits) }
                }
                if (app.proPrice > 0L) {
                    item { BenefitsBlock("Pro includes", app.proBenefits) }
                }

                item { SectionTitle("App users") }
                item {
                    val count = app.users.size
                    Text(
                        text = when (count) {
                            0 -> "No users yet. Roll the dice, or add your own in Edit."
                            1 -> "1 user"
                            else -> formatCount(count.toLong()) + " users (showing the latest 50)"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(
                    app.users.sortedByDescending { it.addedMillis }.take(50),
                    key = { it.id }
                ) { user ->
                    UserRow(user = user, clickable = false, onClick = {})
                }

                item { SectionTitle("Money") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile("Income", formatNaira(app.income), Modifier.weight(1f))
                        StatTile("Expenses", formatNaira(app.expenses), Modifier.weight(1f))
                    }
                }
                item {
                    StatTile(
                        label = "Profit",
                        value = formatNaira(app.profit),
                        modifier = Modifier.fillMaxWidth(),
                        valueColor = profitColor(app.profit)
                    )
                }
                item {
                    Text(
                        text = "Google Play Balance money is not in profit until you withdraw it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (app.entries.isNotEmpty()) {
                    items(app.entries.sortedByDescending { it.dateMillis }, key = { it.id }) { entry ->
                        EntryRow(entry = entry, editing = false, onDelete = {})
                    }
                }

                item { SectionTitle("Roll history") }
                val recent = app.rolls.takeLast(10).reversed()
                if (recent.isEmpty()) {
                    item {
                        Text(
                            text = "No rolls yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(recent, key = { "roll-" + it.dayKey }) { record ->
                    RollHistoryRow(record)
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (addUser) {
        UserDialog(
            heading = "Add user",
            initial = null,
            otherUsers = draft.users,
            premiumAvailable = draft.premiumPrice > 0L,
            proAvailable = draft.proPrice > 0L,
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
                addUser = false
            },
            onDismiss = { addUser = false }
        )
    }

    val userBeingEdited = editUserId?.let { id -> draft.users.firstOrNull { it.id == id } }
    if (editing && userBeingEdited != null && !userBeingEdited.simulated) {
        key(userBeingEdited.id) {
            UserDialog(
                heading = "Edit user",
                initial = userBeingEdited,
                otherUsers = draft.users.filter { it.id != userBeingEdited.id },
                premiumAvailable = draft.premiumPrice > 0L,
                proAvailable = draft.proPrice > 0L,
                onSave = { name, email, phone, plan ->
                    draft = draft.withUserUpdated(
                        userBeingEdited.copy(
                            name = name,
                            email = email,
                            phone = phone,
                            plan = plan
                        )
                    )
                    editUserId = null
                },
                onDismiss = { editUserId = null },
                onDelete = {
                    draft = draft.withUserRemoved(userBeingEdited.id)
                    editUserId = null
                }
            )
        }
    }

    val expiryUser = expiryUserId?.let { id -> app.users.firstOrNull { it.id == id } }
    if (!editing && expiryUser != null && !expiryUser.simulated) {
        key(expiryUser.id) {
            RenewPlanDialog(
                user = expiryUser,
                now = now,
                onRenewed = {
                    onSave(app.withUserRenewed(expiryUser.id, System.currentTimeMillis()))
                    expiryUserId = null
                },
                onNotRenewed = {
                    onSave(app.withPlanLapsed(expiryUser.id))
                    expiryUserId = null
                },
                onDismiss = { expiryUserId = null }
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            heading = "Delete ${app.name}?",
            message = if (app.playBalance > 0L) {
                "This app still has " + formatNaira(app.playBalance) +
                    " in the Google Play Balance. Withdraw it first, or it is lost. " +
                    "Deleting removes the app right away."
            } else {
                "This removes the app and all its entries right away."
            },
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

/** One user: name, email, phone (if given) and a small plan label. Tappable only when [clickable]. */
@Composable
private fun UserRow(user: AppUser, clickable: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
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
