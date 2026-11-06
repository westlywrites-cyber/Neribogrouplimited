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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.Section
import com.westly.neribogroup.data.DEFAULT_COMPANY_NAME
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.SaveBar
import com.westly.neribogroup.feature.common.SearchField
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.matchesQuery
import com.westly.neribogroup.feature.common.profitColor
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatCount
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira

/**
 * The home page. It stays short however many apps there are: search, the money summary,
 * a renewals alert and four tiles (Apps, Renewals, Staff, Company costs) that open their own pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDashboardScreen(
    ledger: Ledger,
    onOpenSection: (Section) -> Unit,
    onOpenApp: (String) -> Unit,
    onOpenStaff: (String) -> Unit,
    onSave: (Ledger) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ledger) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmImport by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val now = rememberNowMillis()
    val shown = if (editing) draft else ledger
    val companyName = shown.company.name.ifBlank { DEFAULT_COMPANY_NAME }
    val searching = query.isNotBlank()

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
                item {
                    Text(
                        text = "Apps, staff, renewals and company costs are edited inside their own pages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                item {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = "Search apps, staff, renewals, costs"
                    )
                }
            }

            if (searching && !editing) {
                val appHits = shown.apps.filter { matchesQuery(query, it.name) }
                val staffHits = shown.staff.filter {
                    matchesQuery(query, it.name, it.role, it.email, it.phone)
                }
                val renewalHits = shown.renewals
                    .filter { matchesQuery(query, it.name) }
                    .sortedBy { it.daysLeft(now) }
                val costHits = shown.company.expenses
                    .filter { matchesQuery(query, it.title, it.amount.toString()) }
                    .sortedByDescending { it.dateMillis }
                    .take(20)

                if (appHits.isEmpty() && staffHits.isEmpty() &&
                    renewalHits.isEmpty() && costHits.isEmpty()
                ) {
                    item {
                        Text(
                            text = "Nothing found for \"" + query.trim() + "\".",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (appHits.isNotEmpty()) {
                    item { SectionTitle("Apps") }
                    items(appHits, key = { "app-" + it.id }) { app ->
                        ResultRow(
                            title = app.name,
                            subtitle = formatCount(app.totalUsers) + " users · Profit " +
                                formatNaira(app.profit),
                            onClick = { onOpenApp(app.id) }
                        )
                    }
                }

                if (staffHits.isNotEmpty()) {
                    item { SectionTitle("Staff") }
                    items(staffHits, key = { "staff-" + it.id }) { person ->
                        ResultRow(
                            title = person.name,
                            subtitle = if (person.role.isNotBlank()) person.role
                            else formatNaira(person.monthlyTotal) + " per month",
                            onClick = { onOpenStaff(person.id) }
                        )
                    }
                }

                if (renewalHits.isNotEmpty()) {
                    item { SectionTitle("Renewals") }
                    items(renewalHits, key = { "renewal-" + it.id }) { renewal ->
                        ResultRow(
                            title = renewal.name,
                            subtitle = renewalStatus(renewal.daysLeft(now)),
                            onClick = { onOpenSection(Section.RENEWALS) }
                        )
                    }
                }

                if (costHits.isNotEmpty()) {
                    item { SectionTitle("Company costs") }
                    items(costHits, key = { "cost-" + it.id }) { entry ->
                        ResultRow(
                            title = entry.title,
                            subtitle = formatNaira(entry.amount) + " · " + formatDate(entry.dateMillis),
                            onClick = { onOpenSection(Section.COSTS) }
                        )
                    }
                }
            } else {
                item { OverviewCard(shown) }

                val attention = shown.renewalsNeedingAttention(now)
                if (attention.isNotEmpty() && !editing) {
                    item {
                        AttentionCard(
                            text = if (attention.size == 1) "1 renewal needs attention"
                            else attention.size.toString() + " renewals need attention",
                            lines = attention.take(3).map { it.name + " · " + renewalStatus(it.daysLeft(now)) },
                            onClick = { onOpenSection(Section.RENEWALS) }
                        )
                    }
                }

                if (!editing) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionTile(
                                title = "Apps",
                                subtitle = countLabel(shown.apps.size, "app", "apps"),
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenSection(Section.APPS) }
                            )
                            SectionTile(
                                title = "Renewals",
                                subtitle = if (attention.isNotEmpty()) attention.size.toString() + " need attention"
                                else shown.renewals.size.toString() + " tracked",
                                modifier = Modifier.weight(1f),
                                highlight = attention.isNotEmpty(),
                                onClick = { onOpenSection(Section.RENEWALS) }
                            )
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionTile(
                                title = "Staff",
                                subtitle = countLabel(shown.staff.size, "person", "people"),
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenSection(Section.STAFF) }
                            )
                            SectionTile(
                                title = "Company costs",
                                subtitle = formatNaira(shown.companyExpenses),
                                modifier = Modifier.weight(1f),
                                onClick = { onOpenSection(Section.COSTS) }
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
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

private fun countLabel(count: Int, one: String, many: String): String =
    if (count == 1) "1 $one" else "$count $many"

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AttentionCard(text: String, lines: List<String>, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(6.dp))
            for (line in lines) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionTile(
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
    highlight: Boolean = false
) {
    val subtitleColor: Color =
        if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
