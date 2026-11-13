package com.westly.neribogroup.feature.software

import androidx.activity.compose.BackHandler
import com.westly.neribogroup.cloud.AiRollKind
import com.westly.neribogroup.cloud.AiRollState
import com.westly.neribogroup.feature.appdetail.AiRollCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.ClientType
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.OfferKind
import com.westly.neribogroup.data.OfferStatus
import com.westly.neribogroup.data.ProjectStage
import com.westly.neribogroup.data.RateSource
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.SOFTWARE_CURRENCIES
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.SoftwareOffer
import com.westly.neribogroup.data.SoftwareProject
import com.westly.neribogroup.data.UsdTxKind
import com.westly.neribogroup.data.dayKey
import com.westly.neribogroup.feature.common.EntryRow
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatCard
import com.westly.neribogroup.feature.common.StatCardItem
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatDateTime
import com.westly.neribogroup.ui.formatMoney
import com.westly.neribogroup.ui.formatNaira
import com.westly.neribogroup.ui.formatRate
import com.westly.neribogroup.ui.formatUsd

// ---------------------------------------------------------------------------
// Small helpers shared by the three software screens
// ---------------------------------------------------------------------------

internal fun kindLabel(kind: OfferKind): String = when (kind) {
    OfferKind.WEBSITE -> "Website"
    OfferKind.WEB_APP -> "Web app"
    OfferKind.ANDROID_APP -> "Android app"
    OfferKind.CODE_PURCHASE -> "Code purchase"
}

internal fun clientTypeLabel(type: ClientType): String =
    if (type == ClientType.COMPANY) "Company" else "Person"

/** Code purchases use their own words for the same four stages. */
internal fun stageLabel(kind: OfferKind, stage: ProjectStage): String =
    if (kind == OfferKind.CODE_PURCHASE) {
        when (stage) {
            ProjectStage.ACCEPTED -> "Accepted"
            ProjectStage.IN_PROGRESS -> "Preparing code"
            ProjectStage.TESTING -> "Handover review"
            ProjectStage.DELIVERED -> "Delivered"
        }
    } else {
        when (stage) {
            ProjectStage.ACCEPTED -> "Accepted"
            ProjectStage.IN_PROGRESS -> "In progress"
            ProjectStage.TESTING -> "Testing"
            ProjectStage.DELIVERED -> "Delivered"
        }
    }

internal fun offerStatusLabel(status: OfferStatus): String = when (status) {
    OfferStatus.PENDING -> "Waiting"
    OfferStatus.ACCEPTED -> "Accepted"
    OfferStatus.DECLINED -> "Declined"
    OfferStatus.EXPIRED -> "Expired"
}

@Composable
internal fun offerStatusColor(status: OfferStatus): Color = when (status) {
    OfferStatus.PENDING -> MaterialTheme.colorScheme.primary
    OfferStatus.ACCEPTED -> MaterialTheme.colorScheme.primary
    OfferStatus.DECLINED -> MaterialTheme.colorScheme.error
    OfferStatus.EXPIRED -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun stageColor(stage: ProjectStage): Color = when (stage) {
    ProjectStage.DELIVERED -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun daysLeftText(days: Long): String =
    if (days == 1L) "1 day left" else "$days days left"

internal fun rateSourceShort(source: RateSource): String = when (source) {
    RateSource.LIVE -> "Live"
    RateSource.MANUAL -> "Typed by you"
    RateSource.DEFAULT -> "Default"
}

/** The offered amount in the client's currency, plus the naira equivalent for a foreign client. */
internal fun offerAmountText(
    units: Long,
    currency: String,
    rates: Map<String, com.westly.neribogroup.data.RateInfo>
): String {
    val own = formatMoney(units, currency)
    if (currency == "NGN") return own
    return own + " ≈ " + formatNaira(SoftwareEngine.ngnEstimateFor(units, currency, rates))
}

// ---------------------------------------------------------------------------
// The screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareClientsScreen(
    ledger: Ledger,
    ratesLoading: Boolean,
    ratesMessage: String,
    onBack: () -> Unit,
    onClock: (Long) -> Unit,
    onRoll: () -> Unit,
    onOpenOffer: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    onConvert: (Long, Double, String) -> Unit,
    onRefreshRates: () -> Unit,
    onSaveRate: (String, Double) -> Unit,
    onUseLiveRate: (String) -> Unit,
    /** True when opened from the "$ Account" tile on Home: shows only the $ account, rates and conversions. */
    usdOnly: Boolean = false,
    /** Phase 6: AI mode on. The dice panel is replaced by the "Today's roll" card while Claude rolls. */
    aiOn: Boolean = false,
    aiStateFor: (Long) -> AiRollState = { AiRollState(AiRollKind.NO_JOBS) },
    aiChecking: Boolean = false,
    onAiCheck: () -> Unit = {},
    onAiRollBuiltIn: () -> Unit = {},
    /** Part C: how many SOFTWARE_ROLL requests were made today (1..3). */
    softwareAttempts: Int = 0
) {
    val now = rememberNowMillis()
    val sw = ledger.software
    val eff = SoftwareEngine.effectiveNow(ledger, now)

    var projectTab by remember { mutableStateOf(0) }
    var showConvert by remember { mutableStateOf(false) }
    var rateCode by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    // Keep the highest time ever seen, and refresh old rates once when the screen opens.
    LaunchedEffect(now) { onClock(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        if (System.currentTimeMillis() - sw.ratesFetchedMillis > 6L * 3_600_000L) onRefreshRates()
    }

    val waiting = sw.offers
        .filter { SoftwareEngine.offerStatusAt(it, eff) == OfferStatus.PENDING }
        .sortedByDescending { it.createdMillis }
    val past = sw.offers
        .filter { SoftwareEngine.offerStatusAt(it, eff) != OfferStatus.PENDING }
        .sortedByDescending { it.createdMillis }
        .take(20)
    val ongoing = sw.projects.filter { it.stage != ProjectStage.DELIVERED }
        .sortedByDescending { it.createdMillis }
    val delivered = sw.projects.filter { it.stage == ProjectStage.DELIVERED }
        .sortedByDescending { it.deliveredMillis }
    val canRoll = SoftwareEngine.canRoll(ledger, now)
    val todayRoll = sw.rolls.firstOrNull { it.dayKey == dayKey(eff) }

    val app = ledger.apps.firstOrNull { it.id == SOFTWARE_APP_ID }
    val incomeEntries = (app?.entries ?: emptyList()).filter { it.isIncome }
        .sortedByDescending { it.dateMillis }
    val expenseEntries = ledger.company.expenses.filter { it.appId == SOFTWARE_APP_ID }
        .sortedByDescending { it.dateMillis }
    val conversions = sw.usdTx.filter { it.kind == UsdTxKind.CONVERSION_OUT }.take(20)
    val clients = sw.clients.sortedByDescending { it.sinceMillis }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (usdOnly) "\$ Account" else "Software Development Clients",
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
            if (!usdOnly) {
            // 1. Summary
            item(key = "summary") {
                val stats = ArrayList<StatCardItem>()
                stats.add(StatCardItem("Proposals waiting", waiting.size.toString()))
                stats.add(StatCardItem("Ongoing projects", ongoing.size.toString()))
                stats.add(StatCardItem("Delivered", delivered.size.toString()))
                stats.add(StatCardItem("Clients", sw.clients.size.toString()))
                StatCard(items = stats, columns = 2)
            }

            // 2. Daily dice
            item(key = "dice") {
                SoftwareClaudeCard(
                    now = now,
                    aiOn = aiOn,
                    state = aiStateFor(now),
                    attemptsToday = softwareAttempts,
                    todayRoll = todayRoll,
                    checking = aiChecking,
                    onCheck = onAiCheck
                )
            }

            // 3. Proposals waiting
            item(key = "waiting-title") { SectionTitle("Proposals waiting") }
            if (waiting.isEmpty()) {
                item(key = "waiting-empty") {
                    Text(
                        text = "No proposals waiting. Roll the dice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(waiting, key = { "offer-" + it.id }) { offer ->
                OfferCard(offer = offer, eff = eff, rates = sw.rates, onClick = { onOpenOffer(offer.id) })
            }

            // 4. Past proposals
            item(key = "past-title") { SectionTitle("Past proposals") }
            if (past.isEmpty()) {
                item(key = "past-empty") {
                    Text(
                        text = "Accepted, declined and expired proposals appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(past, key = { "past-" + it.id }) { offer ->
                val status = SoftwareEngine.offerStatusAt(offer, eff)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenOffer(offer.id) }
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = offer.clientName,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusDot(text = offerStatusLabel(status), color = offerStatusColor(status))
                    }
                    Text(
                        text = offer.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            // 5. Projects
            item(key = "projects-title") { SectionTitle("Projects") }
            item(key = "projects-tabs") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = projectTab == 0,
                        onClick = { projectTab = 0 },
                        label = { Text("Ongoing", maxLines = 1) }
                    )
                    FilterChip(
                        selected = projectTab == 1,
                        onClick = { projectTab = 1 },
                        label = { Text("Delivered", maxLines = 1) }
                    )
                }
            }
            val shownProjects = if (projectTab == 0) ongoing else delivered
            if (shownProjects.isEmpty()) {
                item(key = "projects-empty") {
                    Text(
                        text = if (projectTab == 0) {
                            "No ongoing projects. Accept a proposal to start one."
                        } else {
                            "No delivered projects yet."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(shownProjects, key = { "project-" + it.id }) { project ->
                ProjectCard(
                    project = project,
                    onClick = { onOpenProject(project.id) }
                )
            }

            }

            // 6. $ account, rates, conversions
            item(key = "usd-card") {
                FlatCard {
                    Text(
                        text = "\$ Account",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = formatUsd(sw.usdBalanceCents),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        com.westly.neribogroup.feature.common.StatColumn(
                            label = "Received so far",
                            value = formatUsd(SoftwareEngine.usdReceivedCents(sw)),
                            modifier = Modifier.weight(1f)
                        )
                        com.westly.neribogroup.feature.common.StatColumn(
                            label = "Converted so far",
                            value = formatUsd(SoftwareEngine.usdConvertedCents(sw)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TintedButton(
                        text = "Convert to ₦",
                        onClick = { showConvert = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = sw.usdBalanceCents > 0L
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Money from clients abroad waits here. It is not company profit " +
                            "until you convert it to naira.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item(key = "rates-card") {
                FlatCard {
                    Text(
                        text = "Exchange rates",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (sw.ratesFetchedMillis > 0L) {
                            "Last live refresh " + formatDateTime(sw.ratesFetchedMillis)
                        } else {
                            "No live refresh yet. Default rates are only placeholders."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    for (code in SOFTWARE_CURRENCIES) {
                        val info = sw.rates[code]
                        if (info != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (code == "USD") Modifier
                                        else Modifier.clickable { rateCode = code }
                                    )
                                    .padding(vertical = 6.dp)
                            ) {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = code,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = formatRate(info.perUsd) + " per \$1",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = rateSourceShort(info.source) +
                                        (if (code != "USD") " · tap to type a rate" else ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TintedButton(
                        text = if (ratesLoading) "Refreshing…" else "Refresh rates",
                        onClick = onRefreshRates,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !ratesLoading
                    )
                    if (ratesMessage.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = ratesMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "conv-title") { SectionTitle("Conversions to naira") }
            if (conversions.isEmpty()) {
                item(key = "conv-empty") {
                    Text(
                        text = "No conversions yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(conversions, key = { "conv-" + it.id }) { tx ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = formatUsd(tx.usdCents) + " → " + formatNaira(tx.ngnAmount) +
                            " at ₦" + formatRate(tx.ngnPerUsd) + " per \$1",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = formatDate(tx.dateMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            if (!usdOnly) {
            // 7. Clients
            item(key = "clients-title") { SectionTitle("Clients") }
            if (clients.isEmpty()) {
                item(key = "clients-empty") {
                    Text(
                        text = "Clients are added here when you accept their proposal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(clients, key = { "client-" + it.id }) { client ->
                val theirs = sw.projects.filter { it.clientId == client.id }
                val received = theirs.sumOf { SoftwareEngine.projectReceivedUnits(it) }
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = client.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = clientTypeLabel(client.type),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = client.country + " · " + theirs.size + " project" +
                            (if (theirs.size == 1) "" else "s") + " · received " +
                            formatMoney(received, client.currency),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            // 8. Money
            item(key = "income-title") {
                SectionTitle("Income received (" + formatNaira(incomeEntries.sumOf { it.amount }) + ")")
            }
            if (incomeEntries.isEmpty()) {
                item(key = "income-empty") {
                    Text(
                        text = "No naira income yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(incomeEntries, key = { "income-" + it.id }) { entry ->
                EntryRow(entry = entry, editing = false, onDelete = {})
            }

            item(key = "expense-title") {
                SectionTitle(
                    "Expenses charged to company (" + formatNaira(expenseEntries.sumOf { it.amount }) + ")"
                )
            }
            if (expenseEntries.isEmpty()) {
                item(key = "expense-empty") {
                    Text(
                        text = "No project expenses yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(expenseEntries, key = { "expense-" + it.id }) { entry ->
                EntryRow(entry = entry, editing = false, onDelete = {})
            }

            // 9. Roll history
            item(key = "history-title") { SectionTitle("Roll history") }
            if (sw.rolls.isEmpty()) {
                item(key = "history-empty") {
                    Text(
                        text = "No rolls yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(sw.rolls, key = { "roll-" + it.dayKey + "-" + it.atMillis }) { roll ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    SoftwareRollRow(roll)
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            }

            item(key = "end") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (showConvert) {
        val ngnRate = sw.rates["NGN"] ?: com.westly.neribogroup.data.DEFAULT_RATES.getValue("NGN")
        ConvertUsdDialog(
            balanceCents = sw.usdBalanceCents,
            ngnRate = ngnRate,
            ratesLoading = ratesLoading,
            onRefreshRates = onRefreshRates,
            onConvert = { cents, rate, requestId -> onConvert(cents, rate, requestId) },
            onDismiss = { showConvert = false }
        )
    }

    val editingCode = rateCode
    if (editingCode != null) {
        val info = sw.rates[editingCode] ?: com.westly.neribogroup.data.DEFAULT_RATES.getValue(editingCode)
        RateDialog(
            code = editingCode,
            current = info,
            onSave = { onSaveRate(editingCode, it) },
            onUseLive = { onUseLiveRate(editingCode) },
            onDismiss = { rateCode = null }
        )
    }
}

@Composable
private fun OfferCard(
    offer: SoftwareOffer,
    eff: Long,
    rates: Map<String, com.westly.neribogroup.data.RateInfo>,
    onClick: () -> Unit
) {
    FlatCard(modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = offer.clientName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = clientTypeLabel(offer.clientType),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            text = offer.country + " · " + offer.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = offerAmountText(offer.feeUnits, offer.currency, rates),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = daysLeftText(SoftwareEngine.offerDaysLeft(offer, eff)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ProjectCard(project: SoftwareProject, onClick: () -> Unit) {
    FlatCard(modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = project.ref,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusDot(
                text = stageLabel(project.kind, project.stage),
                color = stageColor(project.stage)
            )
        }
        Text(
            text = project.clientName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = project.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = formatMoney(project.feeUnits, project.currency),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
