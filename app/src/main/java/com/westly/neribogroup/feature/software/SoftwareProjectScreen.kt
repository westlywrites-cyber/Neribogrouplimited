package com.westly.neribogroup.feature.software

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.OfferKind
import com.westly.neribogroup.data.PaymentPurpose
import com.westly.neribogroup.data.ProjectStage
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.SoftwareProject
import com.westly.neribogroup.feature.common.AddEntryDialog
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.EntryRow
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatMoney
import com.westly.neribogroup.ui.formatNaira
import com.westly.neribogroup.ui.formatUsd

@Composable
private fun MoneyLine(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/** One project: stage, money, payments and the expenses of building it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareProjectScreen(
    ledger: Ledger,
    project: SoftwareProject,
    onBack: () -> Unit,
    onClock: (Long) -> Unit,
    onStage: (ProjectStage) -> Unit,
    onAddExpense: (String, Long) -> Unit
) {
    val now = rememberNowMillis()
    val eff = SoftwareEngine.effectiveNow(ledger, now)
    val isCode = project.kind == OfferKind.CODE_PURCHASE
    val delivered = project.stage == ProjectStage.DELIVERED
    val currency = project.currency

    val feePaid = project.payments
        .filter { it.purpose != PaymentPurpose.MAINTENANCE }
        .sumOf { it.units }
    val remaining = maxOf(0L, project.feeUnits - feePaid)
    val receivedUnits = SoftwareEngine.projectReceivedUnits(project)
    val receivedNgn = SoftwareEngine.projectReceivedNgn(project)
    val expenses = SoftwareEngine.projectExpenses(ledger, project.id)
    val expenseTotal = expenses.sumOf { it.amount }
    val anyForeign = project.payments.any { it.currency != "NGN" }

    var confirmDeliver by remember { mutableStateOf(false) }
    var showExpense by remember { mutableStateOf(false) }

    BackHandler { onBack() }
    LaunchedEffect(now) { onClock(System.currentTimeMillis()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Project",
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
            item(key = "header") {
                FlatCard {
                    Text(
                        text = project.ref,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = project.clientName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = kindLabel(project.kind),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            item(key = "stage") {
                FlatCard {
                    SectionTitle("Progress")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (stage in ProjectStage.values()) {
                            FilterChip(
                                selected = project.stage == stage,
                                onClick = {
                                    if (stage == ProjectStage.DELIVERED) {
                                        confirmDeliver = true
                                    } else {
                                        onStage(stage)
                                    }
                                },
                                enabled = !delivered,
                                label = { Text(stageLabel(project.kind, stage), maxLines = 1) }
                            )
                        }
                    }
                    if (delivered) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Delivered on " + formatDate(project.deliveredMillis) +
                                ". The stage can no longer change.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "money") {
                FlatCard {
                    SectionTitle("Money")
                    Spacer(modifier = Modifier.height(6.dp))
                    MoneyLine("Fee", formatMoney(project.feeUnits, currency), bold = true)
                    MoneyLine("Received (" + currency + ")", formatMoney(receivedUnits, currency))
                    MoneyLine("Remaining", formatMoney(remaining, currency))
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    MoneyLine(
                        if (currency == "NGN") "Received" else "Received ≈ ₦ at the time",
                        formatNaira(receivedNgn)
                    )
                    MoneyLine("Expenses", formatNaira(expenseTotal))
                    MoneyLine(
                        if (anyForeign) "Net (approximate)" else "Net",
                        (if (anyForeign) "≈ " else "") + formatNaira(receivedNgn - expenseTotal),
                        bold = true
                    )
                }
            }

            item(key = "about") {
                FlatCard {
                    SectionTitle("What they want")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = project.summary, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(10.dp))
                    SectionTitle("Requirements")
                    Spacer(modifier = Modifier.height(6.dp))
                    for (line in project.requirements.withoutMaintenanceLines()) {
                        Text(
                            text = "• " + line,
                            modifier = Modifier.padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            item(key = "payments-title") { SectionTitle("Payments") }
            if (project.payments.isEmpty()) {
                item(key = "payments-empty") {
                    Text(
                        text = "No payments yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(project.payments.sortedByDescending { it.dateMillis }, key = { "pay-" + it.id }) { pay ->
                androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = SoftwareEngine.purposeLabel(pay.purpose, pay.periodIndex),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = formatMoney(pay.units, pay.currency),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = formatDate(pay.dateMillis) +
                            (if (pay.currency != "NGN") " · +" + formatUsd(pay.usdCents) + " to \$ account" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            item(key = "expenses-title") { SectionTitle("Expenses for this project") }
            if (expenses.isEmpty()) {
                item(key = "expenses-empty") {
                    Text(
                        text = "No expenses yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(expenses, key = { "expense-" + it.id }) { entry ->
                EntryRow(entry = entry, editing = false, onDelete = {})
            }
            item(key = "expense-add") {
                TintedButton(
                    text = "Add expense",
                    onClick = { showExpense = true },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item(key = "end") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (confirmDeliver) {
        ConfirmDialog(
            heading = "Mark as Delivered?",
            message = "Mark " + project.ref + " as Delivered? The remaining 50% (" +
                formatMoney(project.feeUnits - project.feeUnits / 2, currency) +
                ") is recorded now. This cannot be undone.",
            confirmLabel = "Mark as Delivered",
            onConfirm = {
                confirmDeliver = false
                onStage(ProjectStage.DELIVERED)
            },
            onDismiss = { confirmDeliver = false }
        )
    }

    if (showExpense) {
        AddEntryDialog(
            heading = "Add expense",
            onDismiss = { showExpense = false },
            onAdd = { title, amount ->
                showExpense = false
                onAddExpense(title, amount)
            }
        )
    }
}
