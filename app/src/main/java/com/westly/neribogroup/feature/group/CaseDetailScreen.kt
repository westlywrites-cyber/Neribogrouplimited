package com.westly.neribogroup.feature.group

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.CaseActor
import com.westly.neribogroup.data.CaseEngine
import com.westly.neribogroup.data.CaseEvent
import com.westly.neribogroup.data.CaseEventKind
import com.westly.neribogroup.data.CaseStatus
import com.westly.neribogroup.data.CaseText
import com.westly.neribogroup.data.ComplaintCase
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.Plan
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.DialogActions
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LabeledField
import com.westly.neribogroup.feature.common.NeriboDialog
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDateTime(millis: Long): String =
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))

private fun planName(plan: Plan): String = when (plan) {
    Plan.FREE -> "Free"
    Plan.PREMIUM -> "Premium"
    Plan.PRO -> "Pro"
}

private fun actorLabel(actor: CaseActor): String = when (actor) {
    CaseActor.CUSTOMER -> "Customer"
    CaseActor.SUPPORT -> "Support"
    CaseActor.REGULATOR -> "Regulator"
    CaseActor.COURT -> "Court"
    CaseActor.COMPANY -> "Company"
    CaseActor.OWNER -> "You"
    CaseActor.SYSTEM -> "System"
}

/** The full story of one case. Everything here is a simulation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseDetailScreen(
    ledger: Ledger,
    case: ComplaintCase,
    onBack: () -> Unit,
    onClock: (Long) -> Unit,
    onMarkPaid: () -> Unit,
    onAddNote: (String) -> Unit,
    onOpenApp: (String) -> Unit
) {
    val context = LocalContext.current
    val now = rememberNowMillis()
    LaunchedEffect(now) { onClock(now) }
    val effectiveNow = maxOf(now, ledger.casesClockMillis)

    var confirmPay by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }

    BackHandler { onBack() }

    val status = CaseEngine.statusAt(case, effectiveNow)
    val visible = CaseEngine.visibleEvents(case, effectiveNow)
    val due = CaseEngine.amountDueAt(case, effectiveNow)
    val appExists = ledger.apps.any { it.id == case.appId }
    val escalated = CaseEngine.escalatedAt(case, effectiveNow)
    val inCourt = CaseEngine.inCourtAt(case, effectiveNow)
    val paid = case.paidMillis > 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(case.ref, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
            // 1. Header
            item(key = "header") {
                FlatCard {
                    Text(
                        text = case.subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    StatusDot(text = caseStatusLabel(status), color = caseStatusColor(status))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = case.ref + " · " + caseCategoryLabel(case.category),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = case.appName + " · opened " + formatDate(case.createdMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 2. Customer
            item(key = "customer") {
                FlatCard {
                    SectionTitle("Customer")
                    Spacer(modifier = Modifier.height(6.dp))
                    InfoLine("Name", case.customerName)
                    InfoLine("Email", case.customerEmail)
                    if (case.customerPhone.isNotBlank()) InfoLine("Phone", case.customerPhone)
                    InfoLine("Plan", planName(case.plan))
                    InfoLine("App", case.appName)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = {
                                val subject = "Re: " + case.subject + " [" + case.ref + "]"
                                val intent = Intent(
                                    Intent.ACTION_SENDTO,
                                    Uri.parse("mailto:" + case.customerEmail + "?subject=" + Uri.encode(subject))
                                )
                                try {
                                    context.startActivity(intent)
                                } catch (e: ActivityNotFoundException) {
                                    Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Email customer", maxLines = 2) }
                        OutlinedButton(
                            onClick = { onOpenApp(case.appId) },
                            enabled = appExists,
                            modifier = Modifier.weight(1f)
                        ) { Text("Open app", maxLines = 2) }
                    }
                }
            }

            // 6. Payment box (shown high up so it cannot be missed)
            if (due > 0L || paid) {
                item(key = "payment") {
                    FlatCard {
                        SectionTitle(if (paid) "Payment" else "Payment due")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = formatNaira(case.amountDue),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (paid) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        InfoLine("Reason", case.dueReason)
                        InfoLine(
                            "Case",
                            case.ref + (if (case.suitNo.isNotBlank()) " (" + case.suitNo + ")" else "")
                        )
                        if (paid) {
                            InfoLine("Paid on", formatDate(case.paidMillis))
                            InfoLine("Recorded", "As a company expense in Company costs")
                        } else {
                            if (case.payByMillis > 0L) {
                                InfoLine("Pay by", formatDate(case.payByMillis))
                                if (effectiveNow > case.payByMillis) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    StatusDot(text = "Overdue", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            TintedButton(
                                text = "Mark as Paid",
                                onClick = { confirmPay = true },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !confirmPay && status == CaseStatus.PAYMENT_DUE
                            )
                        }
                    }
                }
            }

            // 4. Regulator box
            if (escalated) {
                item(key = "regulator") {
                    val decision = visible.lastOrNull { it.kind == CaseEventKind.REGULATOR_DECISION }
                    FlatCard {
                        SectionTitle("Regulator")
                        Spacer(modifier = Modifier.height(6.dp))
                        InfoLine("Regulator", regulatorName(case.regulator))
                        InfoLine("Reference", case.regulatorRef)
                        InfoLine("Status", if (decision == null) "Under review" else "Decision given")
                        if (decision != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = decision.title, style = MaterialTheme.typography.titleSmall)
                            Text(text = decision.body, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // 5. Court box
            if (inCourt) {
                item(key = "court") {
                    val next = CaseEngine.nextHearingMillis(case, effectiveNow)
                    val ruling = visible.lastOrNull {
                        it.kind == CaseEventKind.COURT_JUDGMENT || it.kind == CaseEventKind.SETTLEMENT
                    }
                    val winner = CaseEngine.winnerAt(case, effectiveNow)
                    FlatCard {
                        SectionTitle("Court")
                        Spacer(modifier = Modifier.height(6.dp))
                        InfoLine("Court", case.court)
                        InfoLine("Suit number", case.suitNo)
                        InfoLine("Amount claimed", formatNaira(case.claimAmount))
                        InfoLine("Current stage", CaseEngine.courtStage(case, effectiveNow))
                        if (ruling == null) {
                            InfoLine(
                                "Next hearing",
                                if (next > 0L) formatDate(next) else "None scheduled"
                            )
                        } else {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = ruling.title, style = MaterialTheme.typography.titleSmall)
                            Text(text = ruling.body, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (winner != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Winner: $winner",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 3. Timeline
            item(key = "timeline-title") { SectionTitle("Timeline") }
            items(visible, key = { "event-" + it.id }) { event ->
                EventCard(case = case, event = event)
            }

            // 7. Add note
            item(key = "add-note") {
                OutlinedButton(
                    onClick = { showNote = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add note") }
            }

            item(key = "bottom") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (confirmPay) {
        ConfirmDialog(
            heading = "Mark as Paid?",
            message = "Record " + formatNaira(case.amountDue) + " as a company expense and mark case " +
                case.ref + " as Paid? This cannot be undone.",
            confirmLabel = "Mark as Paid",
            destructive = false,
            onConfirm = {
                confirmPay = false
                onMarkPaid()
            },
            onDismiss = { confirmPay = false }
        )
    }

    if (showNote) {
        NoteDialog(
            onDismiss = { showNote = false },
            onSave = {
                showNote = false
                onAddNote(it)
            }
        )
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(0.38f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(0.62f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/** One step of the timeline. Customer messages are shown as emails. */
@Composable
private fun EventCard(case: ComplaintCase, event: CaseEvent) {
    FlatCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = actorLabel(event.actor),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = formatDateTime(event.dueMillis),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        when (event.actor) {
            CaseActor.CUSTOMER -> {
                MailLine("From:", case.customerName + " <" + case.customerEmail + ">")
                MailLine("To:", CaseText.SUPPORT_EMAIL)
                MailLine("Subject:", event.title)
            }
            CaseActor.SUPPORT -> {
                MailLine("From:", "Customer Care <" + CaseText.SUPPORT_EMAIL + ">")
                MailLine("To:", case.customerEmail)
                MailLine("Subject:", event.title)
            }
            else -> {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleSmall
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = event.body, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun NoteDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    NeriboDialog(
        onDismiss = onDismiss,
        title = "Add note",
        subtitle = "Notes are for you. They do not change the case or any money."
    ) {
        LabeledField(
            label = "Note",
            value = text,
            onValueChange = { text = it.take(500) },
            singleLine = false,
            minLines = 3
        )
        DialogActions {
            TextButton(onClick = onDismiss) { Text("Cancel") }
            TintedButton(
                text = "Save",
                onClick = { onSave(text.trim()) },
                enabled = text.isNotBlank()
            )
        }
    }
}
