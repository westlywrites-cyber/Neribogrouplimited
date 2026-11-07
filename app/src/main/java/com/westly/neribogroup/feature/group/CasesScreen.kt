package com.westly.neribogroup.feature.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.westly.neribogroup.data.CaseCategory
import com.westly.neribogroup.data.CaseEngine
import com.westly.neribogroup.data.CaseGenerator
import com.westly.neribogroup.data.CaseStatus
import com.westly.neribogroup.data.ComplaintCase
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.feature.common.CardRadius
import com.westly.neribogroup.feature.common.SearchField
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatCard
import com.westly.neribogroup.feature.common.StatCardItem
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.feature.common.matchesQuery
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira
import com.westly.neribogroup.ui.theme.extras

/** Plain words for a case status. */
internal fun caseStatusLabel(status: CaseStatus): String = when (status) {
    CaseStatus.OPEN -> "Open"
    CaseStatus.PENDING -> "Pending"
    CaseStatus.RESOLVED -> "Resolved"
    CaseStatus.ESCALATED -> "Escalated to regulator"
    CaseStatus.IN_COURT -> "In court"
    CaseStatus.CLOSED -> "Closed"
    CaseStatus.PAYMENT_DUE -> "Payment due"
    CaseStatus.PAID -> "Paid"
}

internal fun caseCategoryLabel(category: CaseCategory): String = when (category) {
    CaseCategory.BILLING_RENEWAL -> "Billing: renewal"
    CaseCategory.BILLING_UPGRADE -> "Billing: upgrade"
    CaseCategory.PLAN_ACCESS -> "Plan access"
    CaseCategory.DATA_PRIVACY -> "Data privacy"
    CaseCategory.ADS_CONTENT -> "Ads and content"
}

@Composable
internal fun caseStatusColor(status: CaseStatus): Color = when (status) {
    CaseStatus.PAYMENT_DUE -> MaterialTheme.colorScheme.error
    CaseStatus.RESOLVED, CaseStatus.CLOSED, CaseStatus.PAID -> MaterialTheme.colorScheme.primary
    CaseStatus.OPEN, CaseStatus.PENDING, CaseStatus.ESCALATED, CaseStatus.IN_COURT -> MaterialTheme.extras.amber
}

private const val TAB_OPEN = 0
private const val TAB_RESOLVED = 1
private const val TAB_ESCALATED = 2
private const val TAB_LEGAL = 3

private val TAB_NAMES = listOf("Open", "Resolved", "Escalated", "Legal")

/** Which tab a case belongs to right now. */
private fun tabOf(c: ComplaintCase, now: Long): Int {
    if (CaseEngine.inCourtAt(c, now)) return TAB_LEGAL
    if (CaseEngine.escalatedAt(c, now)) return TAB_ESCALATED
    val status = CaseEngine.statusAt(c, now)
    return if (status == CaseStatus.OPEN || status == CaseStatus.PENDING) TAB_OPEN else TAB_RESOLVED
}

/**
 * Customer Service & Complaints. Cases come only from real activity in Google Play apps.
 * Everything shown is a simulation. There is nothing to edit here, so there is no Edit menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasesScreen(
    ledger: Ledger,
    initialTab: Int,
    onBack: () -> Unit,
    onClock: (Long) -> Unit,
    onOpenCase: (String) -> Unit
) {
    val now = rememberNowMillis()
    LaunchedEffect(now) { onClock(now) }
    val effectiveNow = maxOf(now, ledger.casesClockMillis)

    var tab by remember { mutableStateOf(initialTab.coerceIn(0, 3)) }
    var query by remember { mutableStateOf("") }

    BackHandler { onBack() }

    val withTab = ledger.cases.map { it to tabOf(it, effectiveNow) }
    val countOpen = withTab.count { it.second == TAB_OPEN }
    val countResolved = withTab.count { it.second == TAB_RESOLVED }
    val countEscalated = withTab.count { it.second == TAB_ESCALATED }
    val countLegal = withTab.count { it.second == TAB_LEGAL }
    val dueTotal = ledger.cases.sumOf { CaseEngine.amountDueAt(it, effectiveNow) }
    val paidTotal = ledger.cases.filter { it.paidMillis > 0L }.sumOf { it.amountDue }

    val inTab = withTab
        .filter { it.second == tab }
        .map { it.first }
        .filter {
            matchesQuery(
                query, it.ref, it.customerName, it.customerEmail, it.subject, it.appName,
                it.regulator, it.regulatorRef, it.court, it.suitNo
            )
        }
        .sortedByDescending { CaseEngine.latestMillis(it, effectiveNow) }
    val dueList = inTab.filter { CaseEngine.amountDueAt(it, effectiveNow) > 0L }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Customer Service & Complaints",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
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
            if (ledger.cases.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "No complaints. Complaints appear only when something real happens " +
                            "in a Google Play app.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item(key = "summary") {
                    val items = ArrayList<StatCardItem>()
                    items.add(StatCardItem("Open", countOpen.toString()))
                    items.add(StatCardItem("Resolved", countResolved.toString()))
                    items.add(StatCardItem("Escalated", countEscalated.toString()))
                    items.add(StatCardItem("Legal", countLegal.toString()))
                    if (dueTotal > 0L) {
                        items.add(StatCardItem("Payments due", formatNaira(dueTotal), MaterialTheme.colorScheme.error))
                    }
                    if (paidTotal > 0L) {
                        items.add(StatCardItem("Paid so far", formatNaira(paidTotal)))
                    }
                    StatCard(items = items, columns = 2)
                }

                item(key = "search") {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search cases"
                    )
                }

                item(key = "tabs") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TAB_NAMES.forEachIndexed { index, name ->
                            FilterChip(
                                selected = tab == index,
                                onClick = { tab = index },
                                label = { Text(name, maxLines = 1) }
                            )
                        }
                    }
                }

                if (dueList.isNotEmpty()) {
                    item(key = "due-title") {
                        Text(
                            text = "Payments due",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    items(dueList, key = { "due-" + it.id }) { c ->
                        CaseCard(c, effectiveNow, onClick = { onOpenCase(c.id) })
                    }
                    item(key = "all-title") { SectionTitle(TAB_NAMES[tab]) }
                }

                if (inTab.isEmpty()) {
                    item(key = "tab-empty") {
                        Text(
                            text = if (query.isNotBlank()) "No cases match your search." else when (tab) {
                                TAB_OPEN -> "No open complaints."
                                TAB_RESOLVED -> "No resolved complaints yet."
                                TAB_ESCALATED -> "No complaints have gone to a regulator."
                                else -> "No court cases."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(inTab, key = { "case-" + it.id }) { c ->
                    CaseCard(c, effectiveNow, onClick = { onOpenCase(c.id) })
                }
            }

            item(key = "bottom") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }
}

/** One case as a flat card. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CaseCard(c: ComplaintCase, now: Long, onClick: () -> Unit) {
    val status = CaseEngine.statusAt(c, now)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CardRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = c.ref,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = c.subject,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = c.customerName + " · " + c.appName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            StatusDot(text = caseStatusLabel(status), color = caseStatusColor(status))
            Text(
                text = "Latest update " + formatDate(CaseEngine.latestMillis(c, now)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val due = CaseEngine.amountDueAt(c, now)
            if (due > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = formatNaira(due) + " due",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Regulator full name for a stored short name. */
internal fun regulatorName(short: String): String = CaseGenerator.regulatorFullName(short)
