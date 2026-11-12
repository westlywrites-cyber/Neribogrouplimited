package com.westly.neribogroup.feature.appdetail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.cloud.AiRollKind
import com.westly.neribogroup.cloud.AiRollState
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.RollBlock
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.AdvertPlatform
import com.westly.neribogroup.data.Channel
import com.westly.neribogroup.data.ChannelRules
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.DialogActions
import androidx.compose.material3.OutlinedButton
import com.westly.neribogroup.data.AdvertType
import com.westly.neribogroup.data.CampaignSize
import com.westly.neribogroup.data.Entry
import com.westly.neribogroup.data.Plan
import com.westly.neribogroup.data.StorageRules
import com.westly.neribogroup.feature.common.AddEntryDialog
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LocalBiometricGate
import com.westly.neribogroup.feature.common.EntryRow
import com.westly.neribogroup.feature.common.NumberField
import com.westly.neribogroup.feature.common.TopSaveActions
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatTile
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.profitColor
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.lastSeenText
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira

// The pages of an app dashboard in view mode. PAGE_MAIN is the short main page; every other
// page opens from a card on it (like "App users") and Back returns to the main page.
private const val PAGE_MAIN = ""
private const val PAGE_USERS_PLANS = "users-plans"
private const val PAGE_MONEY = "money"
private const val PAGE_PLAY = "google-play"
private const val PAGE_BENEFITS = "plan-benefits"
private const val PAGE_ADVERTS = "adverts"
private const val PAGE_APPLE = "apple-store"
private const val PAGE_WEBSITE = "website"
private const val PAGE_GROWTH = "campaign-rolls"

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
    onStopCampaign: () -> Unit,
    onStartAdvert: (String, AdvertType, Channel, List<AdvertPlatform>, Int, Int) -> Unit,
    onStopAdvert: (String) -> Unit,
    /** Company expenses charged by this (Google Play) app, newest first is applied here. */
    chargedToCompany: List<Entry>,
    onEnableLive: (Boolean, Boolean, Boolean) -> Unit,
    onSetChannels: (Boolean, Boolean, Boolean) -> Unit,
    onSetDeploy: (Boolean) -> Unit,
    /** (free MB, pro MB, premium MB, site MB, verified). [verified] is true when a lowering was verified by the owner. */
    onSetStorage: (Long, Long, Long, Long, Boolean) -> Unit,
    onAddCompanyExpense: (String, Long) -> Unit,
    /** Income you record yourself: straight into this app's income and profit, with no cut. */
    onAddIncome: (String, Long) -> Unit,
    /** A renewal paid outside the app: (user id, amount). Moves the plan end date and records the income. */
    onRenewalIncome: (String, Long) -> Unit,
    /** Moves one channel's balance (Apple Store or Paystack) into this app's income. */
    onWithdrawChannel: (Channel) -> Unit,
    /** Phase 4B: true when AI mode is ON. The dice screen is then replaced by the "Today's roll" card. */
    aiOn: Boolean = false,
    aiStateFor: (Long) -> AiRollState = { AiRollState(AiRollKind.NO_JOBS) },
    aiChecking: Boolean = false,
    onAiCheck: () -> Unit = {},
    onAiRollBuiltIn: () -> Unit = {},
    /** Part C: names of the Socials accounts the owner added; the advert form gets a followers field for each. */
    extraAdvertPlatforms: List<String> = emptyList()
) {
    val gate = LocalBiometricGate.current
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(app) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addUser by remember { mutableStateOf(false) }
    var editUserId by remember { mutableStateOf<String?>(null) }
    // The user (added by you) whose plan end date was tapped, to say if they renewed.
    var expiryUserId by remember { mutableStateOf<String?>(null) }
    // The user whose plan history dialog is open.
    var historyUserId by remember { mutableStateOf<String?>(null) }
    // Manual apps: the number boxes are redrawn when a button (not typing) changes their values.
    var fieldsKey by remember { mutableStateOf(0) }
    // Only the Total users box is redrawn while you type in "New users today" (the other box keeps its cursor).
    var totalKey by remember { mutableStateOf(0) }
    var showAddIncome by remember { mutableStateOf(false) }
    var showAddExpense by remember { mutableStateOf(false) }
    var showCompanyExpense by remember { mutableStateOf(false) }
    var showLiveIncome by remember { mutableStateOf(false) }
    var showRenewalIncome by remember { mutableStateOf(false) }
    var storageDialog by remember { mutableStateOf(false) }
    // Lowered Free, Pro and Premium sizes (in MB) waiting for the warning and the owner's verification.
    var lowerOffer by remember { mutableStateOf<Triple<Long, Long, Long>?>(null) }
    // The "Add user i of N" sequence after Save in a Manual app. 0 = not running.
    var queueTotal by remember { mutableStateOf(0) }
    var queueIndex by remember { mutableStateOf(0) }
    // True while the full "App users" page (all users, with search) is showing.
    var showUsers by rememberSaveable { mutableStateOf(false) }
    // The filter the users page opens with (a UserFilter name).
    var usersFilter by rememberSaveable { mutableStateOf(UserFilter.ALL.name) }
    // Which page is showing in view mode (PAGE_MAIN = the short main page).
    var page by rememberSaveable { mutableStateOf(PAGE_MAIN) }
    // The advert whose details are open on the Adverts page (null = the list).
    var openAdvertId by rememberSaveable { mutableStateOf<String?>(null) }

    val now = rememberNowMillis()
    val shown = if (editing) draft else app
    val activeDays = activeUsersWeek(shown, now)
    val newDays = newUsersWeek(app, now)

    val historyUser = app.users.firstOrNull { it.id == historyUserId }
    if (historyUser != null) {
        PlanHistoryDialog(user = historyUser, history = app.planHistory, onClose = { historyUserId = null })
    }

    if (showUsers) {
        AppUsersScreen(
            appName = app.name,
            users = app.users,
            onBack = { showUsers = false },
            planHistory = app.planHistory,
            audienceFree = app.audienceFree,
            initialFilter = UserFilter.fromName(usersFilter),
            showLastSeen = app.googlePlayEnabled
        )
        return
    }

    val subPage = !editing && page != PAGE_MAIN
    val openAdvert = app.adverts.firstOrNull { it.id == openAdvertId }

    BackHandler {
        if (editing) editing = false
        else if (subPage && page == PAGE_ADVERTS && (openAdvert != null || openAdvertId != null)) openAdvertId = null
        else if (subPage) page = PAGE_MAIN
        else onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (subPage) {
                                when (page) {
                                    PAGE_USERS_PLANS -> "Users & plans"
                                    PAGE_MONEY -> "Money"
                                    PAGE_PLAY -> "Google Play"
                                    PAGE_APPLE -> "Apple Store"
                                    PAGE_WEBSITE -> "Website"
                                    PAGE_GROWTH -> "Campaign & rolls"
                                    PAGE_BENEFITS -> "Plan benefits"
                                    PAGE_ADVERTS -> "Adverts"
                                    else -> app.name
                                }
                            } else {
                                shown.name.ifBlank { app.name }
                            },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (editing || subPage) {
                            Text(
                                text = if (editing) "Editing" else app.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (editing) editing = false
                        else if (subPage && page == PAGE_ADVERTS && (openAdvert != null || openAdvertId != null)) openAdvertId = null
                        else if (subPage) page = PAGE_MAIN
                        else onBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (editing) {
                        TopSaveActions(
                            onCancel = { editing = false },
                            onSave = {
                                val increase = if (app.googlePlayEnabled) 0L
                                else draft.manualDailyNewUsers - app.newUsersToday(now)
                                val toSave = draft.copy(name = draft.name.trim().ifBlank { app.name })
                                val saveNow = {
                                    onSave(toSave)
                                    editing = false
                                    if (increase > 0L) {
                                        queueTotal = minOf(increase, 100L).toInt()
                                        queueIndex = 1
                                    }
                                }
                                // Income you typed and a lowered Customize storage are verified before they are saved.
                                val sensitive = sensitiveSaveLines(app, toSave)
                                if (sensitive.isEmpty()) {
                                    saveNow()
                                } else {
                                    gate.verify(
                                        title = "Save changes to " + app.name + "?",
                                        subtitle = if (sensitive.size == 1) sensitive[0]
                                        else sensitive.size.toString() + " changes: income added or storage lowered",
                                        logText = sensitive.joinToString("; ")
                                    ) { saveNow() }
                                }
                            }
                        )
                    } else {
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
                                        fieldsKey += 1
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

                if (!draft.googlePlayEnabled) {
                    item { SectionTitle("Users") }
                    item {
                        key(fieldsKey, totalKey) {
                            NumberField("Total users", draft.manualTotalUsers, {
                                draft = draft.copy(manualTotalUsers = it)
                            })
                        }
                    }
                    item {
                        key(fieldsKey) {
                            NumberField("Premium users", draft.manualPremiumUsers, {
                                draft = draft.copy(manualPremiumUsers = it)
                            })
                        }
                    }
                    item {
                        key(fieldsKey) {
                            NumberField("Pro users", draft.manualProUsers, {
                                draft = draft.copy(manualProUsers = it)
                            })
                        }
                    }
                    item {
                        NumberField("New users today", draft.manualDailyNewUsers, { typed ->
                            val diff = typed - draft.manualDailyNewUsers
                            val newTotal = maxOf(0L, draft.manualTotalUsers + diff)
                            val totalChanged = newTotal != draft.manualTotalUsers
                            draft = draft.copy(
                                manualDailyNewUsers = typed,
                                manualTotalUsers = newTotal
                            )
                            if (totalChanged) totalKey += 1
                        })
                    }
                    item {
                        Text(
                            text = "New users today are added to Total users for you. After saving you " +
                                "can pick Free, Premium or Pro for each one. It goes back to 0 at 12 am.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    item {
                        key(fieldsKey) {
                            NumberField("Active users today", draft.activeUsers, {
                                draft = draft.copy(activeUsers = it)
                            })
                        }
                    }
                    item {
                        Text(
                            text = "Active users goes back to 0 at 12 am. Each saved day is kept in the " +
                                "last 7 days chart.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

                if (!draft.googlePlayEnabled) {
                    item {
                        val premiumNew = maxOf(0L, draft.manualPremiumUsers - app.manualPremiumUsers)
                        val proNew = maxOf(0L, draft.manualProUsers - app.manualProUsers)
                        val income = premiumNew * draft.premiumPrice + proNew * draft.proPrice
                        Text(
                            text = "Saving now adds " + formatNaira(income) + " income: " + premiumNew +
                                " Premium and " + proNew + " Pro new subscriptions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (draft.googlePlayEnabled) {
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

                item { SectionTitle(if (draft.googlePlayEnabled) "Users you added" else "App users") }
                item {
                    val diceUsers = draft.users.count { it.simulated }
                    Text(
                        text = if (!draft.googlePlayEnabled) {
                            "Tap a user to edit or delete. A Premium or Pro user raises that plan's " +
                                "number by 1. Total users is not changed."
                        } else if (diceUsers > 0) {
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

                if (!draft.googlePlayEnabled) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showAddIncome = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("Add income", maxLines = 1) }
                            Button(
                                onClick = { showAddExpense = true },
                                modifier = Modifier.weight(1f)
                            ) { Text("Add expense", maxLines = 1) }
                        }
                    }
                }
                if (draft.entries.isNotEmpty()) {
                    item { SectionTitle("Income and expenses") }
                    items(draft.entries.sortedByDescending { it.dateMillis }, key = { it.id }) { entry ->
                        EntryRow(
                            entry = entry,
                            editing = true,
                            tag = manualTag(draft, entry),
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
                // View mode: a short main page, plus one page per topic (like "App users").
                // -------------------------------------------------------------------------
                val paying = app.users
                    .filter { it.plan != Plan.FREE }
                    .sortedWith(
                        compareBy<AppUser> { it.planDaysLeft(now) == null }
                            .thenBy { it.planExpiryMillis }
                    )
                val endingSoon = paying.count { user ->
                    val left = user.planDaysLeft(now)
                    left != null && left <= 7L
                }

                when (page) {
                    // ---------------------------------------------------------------------
                    // Users & plans: user numbers, the activity chart and plan expiry.
                    // ---------------------------------------------------------------------
                    PAGE_USERS_PLANS -> {
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

                        item { SectionTitle("Users activity") }
                        item {
                            Row(
                                modifier = Modifier.height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatTile(
                                    "Active today",
                                    formatCount(app.activeUsersAt(now)),
                                    Modifier.weight(1f).fillMaxHeight()
                                )
                                StatTile(
                                    "New today",
                                    formatCount(app.newUsersToday(now)),
                                    Modifier.weight(1f).fillMaxHeight()
                                )
                                if (app.googlePlayEnabled) {
                                    StatTile(
                                        "Not seen 7+ days",
                                        formatCount(app.notSeenSevenDays(now)),
                                        Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                        }
                        item { NewUsersChart(days = newDays) }
                        if (app.googlePlayEnabled) {
                            val activeTotal = app.activeUsersAt(now)
                            val unlisted = activeTotal - app.listedActiveToday(now)
                            if (unlisted > 0L) {
                                item {
                                    Text(
                                        text = formatCount(unlisted) + (if (unlisted == 1L) " active user is an audience user"
                                        else " active users are audience users") + " that " +
                                            (if (unlisted == 1L) "is" else "are") + " counted but not listed.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (app.users.isNotEmpty()) {
                                item {
                                    TintedButton(
                                        text = "View active and inactive users",
                                        onClick = {
                                            usersFilter = UserFilter.NOT_SEEN_7.name
                                            showUsers = true
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        } else {
                            item {
                                Text(
                                    text = "Last seen is available for Google Play apps.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (paying.isNotEmpty()) {
                            item { SectionTitle("Plan expiry") }
                            item {
                                val soonText = when (endingSoon) {
                                    0 -> "No plans end in the next 7 days."
                                    1 -> "1 plan ends in the next 7 days."
                                    else -> "$endingSoon plans end in the next 7 days."
                                }
                                Text(
                                    text = soonText + (if (app.googlePlayEnabled) {
                                        " Tap a user you added to say if they renewed. " +
                                            "Users made by the dice renew or leave by themselves."
                                    } else {
                                        " Tap a user to say if they renewed."
                                    }),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(paying.take(15), key = { "expiry-" + it.id }) { user ->
                                ExpiryRow(
                                    user = user,
                                    now = now,
                                    onClick = { expiryUserId = user.id },
                                    onHistory = { historyUserId = user.id }
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
                    }

                    // ---------------------------------------------------------------------
                    // Money: income, expenses, profit, entries, and costs charged to company.
                    // ---------------------------------------------------------------------
                    PAGE_MONEY -> {
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
                        if (app.googlePlayEnabled) {
                            item {
                                Text(
                                    text = "Money in the store balances (Google Play, Apple Store, Paystack) is not in profit " +
                                        "until you withdraw it. Income you add yourself goes straight into profit.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (app.entries.isNotEmpty()) {
                            items(app.entries.sortedByDescending { it.dateMillis }, key = { it.id }) { entry ->
                                EntryRow(entry = entry, editing = false, onDelete = {}, tag = manualTag(app, entry))
                            }
                        }

                        if (app.googlePlayEnabled) {
                            val charged = chargedToCompany.sortedByDescending { it.dateMillis }
                            item { SectionTitle("Expenses charged to company") }
                            item {
                                Text(
                                    text = "Charged to company: " + formatNaira(charged.sumOf { it.amount }),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            items(charged, key = { "charged-" + it.id }) { entry ->
                                EntryRow(entry = entry, editing = false, onDelete = {})
                            }
                            item {
                                Button(
                                    onClick = { showLiveIncome = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Add income") }
                            }
                            item {
                                OutlinedButton(
                                    onClick = { showRenewalIncome = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Add renewal income") }
                            }
                            item {
                                Text(
                                    text = "Income you add goes straight into this app's income and profit. " +
                                        "No store takes a cut of it.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            item {
                                Button(
                                    onClick = { showCompanyExpense = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Add expense to company costs") }
                            }
                            item {
                                Text(
                                    text = "These expenses are kept in Company costs, so they are counted once. " +
                                        "To remove one, delete it on the Company costs page.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // ---------------------------------------------------------------------
                    // Google Play, Apple Store and Website: each channel has its own page.
                    // ---------------------------------------------------------------------
                    PAGE_PLAY -> channelPageItems(
                        app = app, channel = Channel.PLAY, now = now,
                        onEnableLive = onEnableLive, onSetChannels = onSetChannels,
                        onWithdraw = onWithdraw, onWithdrawChannel = onWithdrawChannel
                    )
                    PAGE_APPLE -> channelPageItems(
                        app = app, channel = Channel.APPLE, now = now,
                        onEnableLive = onEnableLive, onSetChannels = onSetChannels,
                        onWithdraw = onWithdraw, onWithdrawChannel = onWithdrawChannel
                    )
                    PAGE_WEBSITE -> channelPageItems(
                        app = app, channel = Channel.WEBSITE, now = now,
                        onEnableLive = onEnableLive, onSetChannels = onSetChannels,
                        onWithdraw = onWithdraw, onWithdrawChannel = onWithdrawChannel
                    )

                    // ---------------------------------------------------------------------
                    // Campaign and roll history (live apps).
                    // ---------------------------------------------------------------------
                    PAGE_GROWTH -> {
                        if (app.googlePlayEnabled) {
                            item { SectionTitle("Campaign") }
                            item {
                                CampaignCard(
                                    app = app,
                                    now = now,
                                    onStart = onStartCampaign,
                                    onStop = onStopCampaign
                                )
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
                    }

                    // ---------------------------------------------------------------------
                    // Plan benefits: what Free, Premium and Pro include.
                    // ---------------------------------------------------------------------
                    PAGE_ADVERTS -> {
                        advertsPageItems(
                            app = app,
                            now = now,
                            openAdvert = openAdvert,
                            onOpenAdvert = { openAdvertId = it },
                            onStartAdvert = onStartAdvert,
                            onStopAdvert = onStopAdvert,
                            extraPlatformNames = extraAdvertPlatforms
                        )
                    }

                    PAGE_BENEFITS -> {
                        item { BenefitsBlock("Free includes", app.freeBenefits) }
                        if (app.premiumPrice > 0L) {
                            item { BenefitsBlock("Premium includes", app.premiumBenefits) }
                        }
                        if (app.proPrice > 0L) {
                            item { BenefitsBlock("Pro includes", app.proBenefits) }
                        }
                    }

                    // ---------------------------------------------------------------------
                    // The short main page: headline numbers, the daily dice, and one card per topic.
                    // ---------------------------------------------------------------------
                    else -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                StatTile("Total users", formatCount(app.totalUsers), Modifier.weight(1f))
                                StatTile(
                                    label = "Profit",
                                    value = formatNaira(app.profit),
                                    modifier = Modifier.weight(1f),
                                    valueColor = profitColor(app.profit)
                                )
                            }
                        }

                        val storageWarning = StorageRules.appWarning(app, now)
                        if (storageWarning != null) {
                            item { StorageWarningCard(storageWarning.title, storageWarning.text) }
                        }

                        if (app.googlePlayEnabled && aiOn) {
                            item {
                                AiRollCard(
                                    state = aiStateFor(now),
                                    now = now,
                                    canRollBuiltIn = DiceEngine.rollBlock(app, now) == RollBlock.OK,
                                    checking = aiChecking,
                                    onCheck = onAiCheck,
                                    onRollBuiltIn = onAiRollBuiltIn
                                )
                            }
                        } else if (app.googlePlayEnabled) {
                            item { SectionTitle("Daily dice") }
                            item { DicePanel(app = app, now = now, onRoll = onRoll) }
                        }

                        val userCount = app.users.size.toLong() + app.audienceFree
                        if (userCount == 0L) {
                            item {
                                Text(
                                    text = if (app.googlePlayEnabled) "No users yet. Roll the dice, or add your own in Edit."
                                    else "No users yet. Add users in Edit.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            item {
                                NavCard(
                                    title = "App users",
                                    summary = (if (userCount == 1L) "1 user" else formatCount(userCount) + " users") +
                                        " · see everyone and search",
                                    onClick = {
                                        usersFilter = UserFilter.ALL.name
                                        showUsers = true
                                    }
                                )
                            }
                        }

                        item {
                            val planParts = buildList {
                                add("Free " + formatCount(app.freeUsers))
                                if (app.premiumPrice > 0L) add("Premium " + formatCount(app.premiumUsers))
                                if (app.proPrice > 0L) add("Pro " + formatCount(app.proUsers))
                                if (endingSoon > 0) add("$endingSoon ending soon")
                            }
                            NavCard(
                                title = "Users & plans",
                                summary = planParts.joinToString(" · "),
                                onClick = { page = PAGE_USERS_PLANS }
                            )
                        }
                        item {
                            NavCard(
                                title = "Money",
                                summary = "Income " + formatNaira(app.income) +
                                    " · Expenses " + formatNaira(app.expenses),
                                onClick = { page = PAGE_MONEY }
                            )
                        }
                        for (channel in Channel.values()) {
                            item(key = "nav-" + channel.name) {
                                NavCard(
                                    title = channel.label,
                                    summary = channelSummary(app, channel),
                                    onClick = { page = channelPage(channel) }
                                )
                            }
                        }
                        if (app.googlePlayEnabled) {
                            item {
                                NavCard(
                                    title = "Campaign & rolls",
                                    summary = if (app.rolls.isEmpty()) "No rolls yet" else app.rolls.size.toString() + " days rolled",
                                    onClick = { page = PAGE_GROWTH }
                                )
                            }
                        }
                        // Storage works in every app, also when no channel is turned on.
                        item {
                            NavCard(
                                title = "Storage",
                                summary = storageSummary(app),
                                onClick = { storageDialog = true }
                            )
                        }
                        item {
                            val benefitNames = buildList {
                                add("Free")
                                if (app.premiumPrice > 0L) add("Premium")
                                if (app.proPrice > 0L) add("Pro")
                            }
                            NavCard(
                                title = "Plan benefits",
                                summary = "What " + benefitNames.joinToString(", ") + " include",
                                onClick = { page = PAGE_BENEFITS }
                            )
                        }
                        if (app.googlePlayEnabled) {
                            item {
                                NavCard(
                                    title = "Adverts",
                                    summary = advertsSummary(app, now),
                                    onClick = { page = PAGE_ADVERTS }
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    var pendingSites by remember { mutableStateOf(1) }
    var pendingCustomMb by remember { mutableStateOf(-1L) }
    var queueCustomMb by remember { mutableStateOf(-1L) }
    if (addUser) {
        UserDialog(
            heading = "Add user",
            initial = null,
            otherUsers = draft.users,
            premiumAvailable = draft.premiumPrice > 0L,
            proAvailable = draft.proPrice > 0L,
            sites = if (draft.deployTracking) 1 else null,
            onSitesChange = { pendingSites = it },
            customMb = -1L,
            onCustomChange = { pendingCustomMb = it },
            onSave = { name, email, phone, plan ->
                draft = draft.withUserAdded(
                    AppUser(
                        name = name,
                        email = email,
                        phone = phone,
                        plan = plan,
                        addedMillis = System.currentTimeMillis(),
                        lastActiveMillis = System.currentTimeMillis(),
                        sites = if (draft.deployTracking) pendingSites else 0,
                        customStorageMb = pendingCustomMb
                    )
                )
                fieldsKey += 1
                addUser = false
            },
            onDismiss = { addUser = false }
        )
    }

    var pendingEditSites by remember { mutableStateOf(1) }
    var pendingEditCustomMb by remember { mutableStateOf(-1L) }
    val userBeingEdited = editUserId?.let { id -> draft.users.firstOrNull { it.id == id } }
    if (editing && userBeingEdited != null && !userBeingEdited.simulated) {
        key(userBeingEdited.id) {
            UserDialog(
                heading = "Edit user",
                initial = userBeingEdited,
                otherUsers = draft.users.filter { it.id != userBeingEdited.id },
                premiumAvailable = draft.premiumPrice > 0L,
                proAvailable = draft.proPrice > 0L,
                sites = if (draft.deployTracking) maxOf(1, userBeingEdited.sites) else null,
                onSitesChange = { pendingEditSites = it },
                customMb = userBeingEdited.customStorageMb,
                onCustomChange = { pendingEditCustomMb = it },
                onSave = { name, email, phone, plan ->
                    draft = draft.withUserUpdated(
                        userBeingEdited.copy(
                            name = name,
                            email = email,
                            phone = phone,
                            plan = plan,
                            sites = if (draft.deployTracking) pendingEditSites else userBeingEdited.sites,
                            customStorageMb = pendingEditCustomMb
                        )
                    )
                    fieldsKey += 1
                    editUserId = null
                },
                onDismiss = { editUserId = null },
                onDelete = {
                    draft = draft.withUserRemoved(userBeingEdited.id)
                    fieldsKey += 1
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
                onDismiss = { expiryUserId = null },
                recordsMoney = !app.googlePlayEnabled
            )
        }
    }

    if (showAddIncome) {
        AddEntryDialog(
            heading = "Add income",
            onDismiss = { showAddIncome = false },
            onAdd = { title, amount ->
                draft = draft.copy(
                    entries = draft.entries + Entry(
                        title = title,
                        amount = amount,
                        isIncome = true,
                        dateMillis = System.currentTimeMillis()
                    )
                )
                showAddIncome = false
            }
        )
    }

    if (showAddExpense) {
        AddEntryDialog(
            heading = "Add expense",
            onDismiss = { showAddExpense = false },
            onAdd = { title, amount ->
                draft = draft.copy(
                    entries = draft.entries + Entry(
                        title = title,
                        amount = amount,
                        isIncome = false,
                        dateMillis = System.currentTimeMillis()
                    )
                )
                showAddExpense = false
            }
        )
    }

    if (showCompanyExpense) {
        AddEntryDialog(
            heading = "Add expense to company costs",
            onDismiss = { showCompanyExpense = false },
            onAdd = { title, amount ->
                showCompanyExpense = false
                onAddCompanyExpense(title, amount)
            }
        )
    }

    if (showLiveIncome) {
        AddEntryDialog(
            heading = "Add income",
            onDismiss = { showLiveIncome = false },
            onAdd = { title, amount ->
                showLiveIncome = false
                gate.verify(
                    title = "Add income to " + app.name,
                    subtitle = title.trim().ifEmpty { "Income" } + " " + formatNaira(amount),
                    logText = "Income added to " + app.name + ": " + title.trim().ifEmpty { "Income" } + " " +
                        formatNaira(amount)
                ) { onAddIncome(title, amount) }
            }
        )
    }

    if (showRenewalIncome) {
        RenewalIncomeDialog(
            app = app,
            onDismiss = { showRenewalIncome = false },
            onSave = { userId, amount ->
                showRenewalIncome = false
                val who = app.users.firstOrNull { it.id == userId }?.name ?: "a user"
                gate.verify(
                    title = "Add renewal income to " + app.name,
                    subtitle = who + " " + formatNaira(amount),
                    logText = "Renewal income added to " + app.name + ": " + who + " " + formatNaira(amount)
                ) { onRenewalIncome(userId, amount) }
            }
        )
    }

    if (storageDialog) {
        StorageOfferDialog(
            app = app,
            onDismiss = { storageDialog = false },
            onSetDeploy = onSetDeploy,
            onSave = { free, pro, premium ->
                storageDialog = false
                if (free < app.storageFreeMb || pro < app.storageProMb || premium < app.storagePremiumMb) {
                    lowerOffer = Triple(free, pro, premium)
                } else {
                    onSetStorage(free, pro, premium, 0L, false)
                }
            }
        )
    }

    // Lowering what a plan gives: the owner reads what it does, then the phone verifies the owner.
    lowerOffer?.let { (free, pro, premium) ->
        ConfirmDialog(
            heading = "Lower " + app.name + "'s storage?",
            message = offerLoweringWarning(app, free, pro, premium),
            confirmLabel = "Lower storage",
            destructive = true,
            onConfirm = {
                lowerOffer = null
                gate.verify(
                    title = "Lower " + app.name + "'s storage?",
                    subtitle = "Free " + StorageRules.textMb(free) + ", Pro " + StorageRules.textMb(pro) +
                        ", Premium " + StorageRules.textMb(premium)
                ) { onSetStorage(free, pro, premium, 0L, true) }
            },
            onDismiss = { lowerOffer = null }
        )
    }

    if (!editing && !app.googlePlayEnabled && queueIndex in 1..queueTotal) {
        key(queueIndex) {
            val last = queueIndex >= queueTotal
            UserDialog(
                heading = "Add user $queueIndex of $queueTotal",
                initial = null,
                otherUsers = app.users,
                saveLabel = if (last) "Save and finish" else "Save and next",
                premiumAvailable = app.premiumPrice > 0L,
                proAvailable = app.proPrice > 0L,
                customMb = -1L,
                onCustomChange = { queueCustomMb = it },
                onSave = { name, email, phone, plan ->
                    // Added to the latest saved app, so earlier users are never lost.
                    onSave(
                        app.withUserAdded(
                            AppUser(
                                name = name,
                                email = email,
                                phone = phone,
                                plan = plan,
                                addedMillis = System.currentTimeMillis(),
                                customStorageMb = queueCustomMb
                            )
                        )
                    )
                    if (last) queueIndex = 0 else queueIndex += 1
                },
                onDismiss = { queueIndex = 0 },
                onSkip = { if (last) queueIndex = 0 else queueIndex += 1 },
                onStop = { queueIndex = 0 }
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
                gate.verify(
                    title = "Delete " + app.name + "?",
                    subtitle = "This cannot be undone.",
                    logText = "App deleted: " + app.name
                ) { onDelete() }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

/** A tappable card on the main page: a title, a one-line summary and an arrow. Opens a page. */
@Composable
private fun NavCard(title: String, summary: String, onClick: () -> Unit) {
    FlatCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** One block on the Plan benefits page (read-only view). */
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
internal fun UserRow(
    user: AppUser,
    clickable: Boolean,
    onClick: () -> Unit,
    /** When given (Google Play apps), a status line shows Active or Last seen, judged at this time. */
    seenNow: Long? = null
) {
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
            if (seenNow != null) {
                if (user.isActiveToday(seenNow)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ActiveGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = ActiveGreen
                        )
                    }
                } else {
                    Text(
                        text = lastSeenText(seenNow, user.lastSeenMillis()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        val isCustom = user.customStorageMb >= 0L && user.plan == Plan.FREE
        Text(
            text = if (isCustom) "Customize" else planText(user.plan),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = if (user.plan == Plan.FREE && !isCustom) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.primary
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The green of the "Active" dot (readable on light and dark cards). */
private val ActiveGreen = Color(0xFF2E9E5B)

/** A small "Manual" tag for entries made before Google Play was turned on. */
private fun manualTag(app: AppData, entry: Entry): String? =
    if (app.googlePlayEnabled && app.googlePlaySinceMillis > 0L && entry.dateMillis < app.googlePlaySinceMillis)
        "Manual"
    else null


// -------------------------------------------------------------------------------------------------
// Channels: where an app is (Google Play, Apple Store, Website), Neribo Deploy sites and storage offered
// -------------------------------------------------------------------------------------------------

@Composable
private fun ChannelSwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

private fun channelPage(channel: Channel): String = when (channel) {
    Channel.PLAY -> PAGE_PLAY
    Channel.APPLE -> PAGE_APPLE
    Channel.WEBSITE -> PAGE_WEBSITE
}

private fun channelOn(app: AppData, channel: Channel): Boolean = when (channel) {
    Channel.PLAY -> app.playOn
    Channel.APPLE -> app.appleOn
    Channel.WEBSITE -> app.websiteOn
}

private fun channelBalance(app: AppData, channel: Channel): Long = when (channel) {
    Channel.PLAY -> app.playBalance
    Channel.APPLE -> app.appleBalance
    Channel.WEBSITE -> app.paystackBalance
}

private fun channelSummary(app: AppData, channel: Channel): String {
    if (!app.googlePlayEnabled) return "Not turned on"
    if (!channelOn(app, channel)) return "Turned off"
    return formatCount(app.usersOfChannel(channel)) + " users · Balance " + formatNaira(channelBalance(app, channel)) +
        (if (channel == Channel.PLAY) " · Ads " + (if (app.adsEnabled) "on" else "off") else "")
}

/** The serious warning that an app might lose its users' data because its storage is over or about to be full. */
@Composable
private fun StorageWarningCard(title: String, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

private fun storageSummary(app: AppData): String =
    StorageRules.text(app.storageAllocatedKb) + " given to users" +
        (if (app.storageShareMb > 0L) " of " + StorageRules.textMb(app.storageShareMb) else "")

/** The page of one channel: its own switch, its users and its own balance. Each channel works on its own. */
private fun LazyListScope.channelPageItems(
    app: AppData,
    channel: Channel,
    now: Long,
    onEnableLive: (Boolean, Boolean, Boolean) -> Unit,
    onSetChannels: (Boolean, Boolean, Boolean) -> Unit,
    onWithdraw: () -> Unit,
    onWithdrawChannel: (Channel) -> Unit
) {
    item { ChannelSwitchCard(app, channel, onEnableLive, onSetChannels) }
    if (app.googlePlayEnabled) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Users from " + channel.label, formatCount(app.usersOfChannel(channel)), Modifier.weight(1f))
                StatTile("Balance", formatNaira(channelBalance(app, channel)), Modifier.weight(1f))
            }
        }
        if (channel == Channel.PLAY) {
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
        } else {
            item { ChannelBalanceCard(app, channel) { onWithdrawChannel(channel) } }
        }
    }
}

/** The switch of one channel. Turning on the first channel of a Manual app turns on live mode. */
@Composable
private fun ChannelSwitchCard(
    app: AppData,
    channel: Channel,
    onEnableLive: (Boolean, Boolean, Boolean) -> Unit,
    onSetChannels: (Boolean, Boolean, Boolean) -> Unit
) {
    val gate = LocalBiometricGate.current
    var confirmLive by remember { mutableStateOf(false) }
    val on = app.googlePlayEnabled && channelOn(app, channel)
    val onlyOne = on && ChannelRules.enabled(app).size == 1
    FlatCard {
        ChannelSwitchRow(channel.label, on) { wantOn ->
            if (!app.googlePlayEnabled) {
                if (wantOn) confirmLive = true
            } else {
                val play = if (channel == Channel.PLAY) wantOn else app.playOn
                val apple = if (channel == Channel.APPLE) wantOn else app.appleOn
                val website = if (channel == Channel.WEBSITE) wantOn else app.websiteOn
                if (play || apple || website) onSetChannels(play, apple, website)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when {
                !app.googlePlayEnabled ->
                    "Not turned on. Turn it on to start getting users and money from " + channel.label +
                        ". Each channel works on its own."
                onlyOne -> "On. At least one channel stays on, so this one cannot be turned off while it is the only one."
                on -> "On. New users and money from this channel go to its own balance."
                else -> "Off. No new users come from this channel. Money already waiting can still be withdrawn."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (confirmLive) {
        ConfirmDialog(
            heading = "Turn on " + channel.label + "?",
            message = "This turns on live mode for this app and cannot be undone. Your typed user numbers are " +
                "replaced by the user list. Your income, expenses and users you added stay.",
            confirmLabel = "Turn on",
            onConfirm = {
                confirmLive = false
                gate.verify(
                    title = "Turn on live mode for " + app.name + "?",
                    subtitle = "Starting with " + channel.label + ". This cannot be undone.",
                    logText = "Live mode turned on for " + app.name + " (" + channel.label + ")"
                ) {
                    onEnableLive(channel == Channel.PLAY, channel == Channel.APPLE, channel == Channel.WEBSITE)
                }
            },
            onDismiss = { confirmLive = false }
        )
    }
}

/** The Apple Store Balance or the Paystack Balance of one app, with a Withdraw button. */
@Composable
private fun ChannelBalanceCard(app: AppData, channel: Channel, onWithdraw: () -> Unit) {
    val gate = LocalBiometricGate.current
    val balance = channelBalance(app, channel)
    var confirm by remember { mutableStateOf(false) }
    FlatCard {
        Text(
            text = if (channel == Channel.APPLE) "Apple Store Balance" else "Paystack Balance",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(formatNaira(balance), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = "Subscription and renewal money waits here after " +
                (if (channel == Channel.APPLE) "Apple's share" else "Paystack's fees") +
                ". It is not profit until you withdraw it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { confirm = true }, enabled = balance > 0L) { Text("Withdraw") }
    }
    if (confirm) {
        ConfirmDialog(
            heading = "Withdraw " + formatNaira(balance) + "?",
            message = "This moves the balance into " + app.name + "'s income and profit.",
            confirmLabel = "Withdraw",
            onConfirm = {
                confirm = false
                val name = if (channel == Channel.APPLE) "Apple Store" else "Paystack"
                gate.verify(
                    title = "Withdraw " + name + " balance?",
                    subtitle = formatNaira(balance) + " moves into " + app.name + "'s profit.",
                    logText = name + " balance withdrawn for " + app.name + ": " + formatNaira(balance)
                ) { onWithdraw() }
            },
            onDismiss = { confirm = false }
        )
    }
}

/**
 * What each plan gives every user of this app, in MB. The app's used storage is worked out from its users, so it is
 * always up to date. The Neribo Deploy switch is only here for the Neribo Deploy app.
 */
@Composable
private fun StorageOfferDialog(
    app: AppData,
    onDismiss: () -> Unit,
    onSetDeploy: (Boolean) -> Unit,
    onSave: (Long, Long, Long) -> Unit
) {
    var free by remember { mutableStateOf(app.storageFreeMb) }
    var pro by remember { mutableStateOf(app.storageProMb) }
    var premium by remember { mutableStateOf(app.storagePremiumMb) }
    val trial = app.copy(storageFreeMb = free, storageProMb = pro, storagePremiumMb = premium)
    val newKb = trial.storageAllocatedKb
    val shareKb = app.storageShareMb * 1024L
    val tooBig = shareKb > 0L && newKb > shareKb && newKb > app.storageAllocatedKb
    NeriboDialog(
        onDismiss = onDismiss,
        title = "Storage",
        subtitle = "What each plan gives every user, in MB. Used storage is worked out from the users, so it is " +
            "always up to date."
    ) {
        NumberField(label = "Free users (MB)", value = free, onChange = { free = it }, maxDigits = 9)
        Spacer(modifier = Modifier.height(8.dp))
        NumberField(label = "Pro users (MB)", value = pro, onChange = { pro = it }, maxDigits = 9)
        Spacer(modifier = Modifier.height(8.dp))
        NumberField(label = "Premium users (MB)", value = premium, onChange = { premium = it }, maxDigits = 9)
        if (app.isDeployApp && app.googlePlayEnabled) {
            Spacer(modifier = Modifier.height(12.dp))
            ChannelSwitchRow("Neribo Deploy sites (users have sites)", app.deployTracking, onSetDeploy)
            Text(
                text = "All of an account's sites share that account's storage.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Given to users: " + StorageRules.text(newKb) +
                (if (app.storageShareMb > 0L) " of " + StorageRules.textMb(app.storageShareMb) else ""),
            style = MaterialTheme.typography.bodyMedium
        )
        if (tooBig) {
            Text(
                text = "That is more than this app's share. Raise the share on the company Storage page first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        DialogActions {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(text = "Save", enabled = !tooBig, onClick = { onSave(free, pro, premium) })
        }
    }
}

/**
 * A renewal paid outside the app, for a Premium or Pro user you added: the plan end date moves forward and the money
 * goes straight into this app's income and profit, with no cut.
 */
@Composable
private fun RenewalIncomeDialog(
    app: AppData,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit
) {
    val users = app.users.filter { !it.simulated && it.plan != Plan.FREE }
    fun priceOf(user: AppUser): Long = if (user.plan == Plan.PREMIUM) app.premiumPrice else app.proPrice
    var selectedId by remember { mutableStateOf(users.firstOrNull()?.id ?: "") }
    var amount by remember { mutableStateOf(users.firstOrNull()?.let { priceOf(it) } ?: 0L) }
    val selected = users.firstOrNull { it.id == selectedId }
    NeriboDialog(
        onDismiss = onDismiss,
        title = "Add renewal income",
        subtitle = "For a user you added who paid you outside the app. Their plan end date moves forward."
    ) {
        if (users.isEmpty()) {
            Text(
                text = "There is no Premium or Pro user that you added yourself. Use Add income instead.",
                style = MaterialTheme.typography.bodyMedium
            )
            DialogActions { TintedButton(text = "Close", onClick = onDismiss) }
        } else {
            for (user in users) {
                FilterChip(
                    selected = user.id == selectedId,
                    onClick = {
                        selectedId = user.id
                        amount = priceOf(user)
                    },
                    label = { Text(user.name + " · " + planText(user.plan)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (selected != null && selected.planExpiryMillis > 0L) {
                Text(
                    text = "Plan ends " + formatDate(selected.planExpiryMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            key(selectedId) {
                NumberField(label = "Amount paid (₦)", value = amount, onChange = { amount = it }, maxDigits = 12)
            }
            DialogActions {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                TintedButton(
                    text = "Record",
                    enabled = selected != null && amount > 0L,
                    onClick = { if (selected != null) onSave(selected.id, amount) }
                )
            }
        }
    }
}

/** What lowering the Free, Pro or Premium sizes would do: which plans, how much less, and how many users. */
private fun offerLoweringWarning(app: AppData, free: Long, pro: Long, premium: Long): String {
    val plainFree = app.plainFreeUsers
    val parts = ArrayList<String>()
    var users = 0L
    var lessMb = 0L
    if (free < app.storageFreeMb) {
        parts.add("Free from " + StorageRules.textMb(app.storageFreeMb) + " to " + StorageRules.textMb(free))
        users += plainFree
        lessMb += plainFree * (app.storageFreeMb - free)
    }
    if (pro < app.storageProMb) {
        parts.add("Pro from " + StorageRules.textMb(app.storageProMb) + " to " + StorageRules.textMb(pro))
        users += app.proUsers
        lessMb += app.proUsers * (app.storageProMb - pro)
    }
    if (premium < app.storagePremiumMb) {
        parts.add("Premium from " + StorageRules.textMb(app.storagePremiumMb) + " to " + StorageRules.textMb(premium))
        users += app.premiumUsers
        lessMb += app.premiumUsers * (app.storagePremiumMb - premium)
    }
    val head = app.name + " would give each user less: " + parts.joinToString(", ") + ". "
    return if (users <= 0L) {
        head + "No user is on those plans right now."
    } else {
        head + StorageRules.textMb(lessMb) + " in total would no longer fit. This affects " + formatCount(users) +
            " users: uploads can be blocked and the data above the limit can be lost."
    }
}

/**
 * The storage-log lines for the changes that must be verified when an edited app is saved: income you typed
 * yourself, and a Customize user whose storage goes down. Empty when the save has neither.
 */
private fun sensitiveSaveLines(old: AppData, updated: AppData): List<String> {
    val lines = ArrayList<String>()
    for (e in updated.entries) {
        if (e.isIncome && !e.auto && old.entries.none { it.id == e.id }) {
            lines.add("Income added to " + updated.name + ": " + e.title + " " + formatNaira(e.amount))
        }
    }
    for (u in updated.users) {
        val before = old.users.firstOrNull { it.id == u.id } ?: continue
        if (before.customStorageMb < 0L) continue
        val nowMb = if (u.customStorageMb >= 0L) u.customStorageMb else when (u.plan) {
            Plan.FREE -> updated.storageFreeMb
            Plan.PRO -> updated.storageProMb
            Plan.PREMIUM -> updated.storagePremiumMb
        }
        if (nowMb < before.customStorageMb) {
            lines.add(
                updated.name + ": storage for " + u.name + " lowered from " + StorageRules.textMb(before.customStorageMb) +
                    " to " + StorageRules.textMb(nowMb)
            )
        }
    }
    return lines
}
