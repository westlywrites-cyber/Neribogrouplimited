package com.westly.neribogroup.feature.software

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.westly.neribogroup.data.ClientType
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.OfferKind
import com.westly.neribogroup.data.OfferStatus
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.SoftwareOffer
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatusDot
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatMoney
import com.westly.neribogroup.ui.formatNaira
import com.westly.neribogroup.ui.formatUsd

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
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

/** One proposal in full: who, what they want, the requirements, the money and the decision. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoftwareOfferScreen(
    ledger: Ledger,
    offer: SoftwareOffer,
    onBack: () -> Unit,
    onClock: (Long) -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onOpenProject: (String) -> Unit
) {
    val now = rememberNowMillis()
    val sw = ledger.software
    val eff = SoftwareEngine.effectiveNow(ledger, now)
    val status = SoftwareEngine.offerStatusAt(offer, eff)
    val project = sw.projects.firstOrNull { it.offerId == offer.id }
    val isCode = offer.kind == OfferKind.CODE_PURCHASE
    val isNgn = offer.currency == "NGN"
    val half = offer.feeUnits / 2
    val halfUsdCents = if (isNgn) 0L else SoftwareEngine.usdCentsFor(half, offer.currency, sw.rates)

    var confirmAccept by remember { mutableStateOf(false) }
    var confirmDecline by remember { mutableStateOf(false) }

    BackHandler { onBack() }
    LaunchedEffect(now) { onClock(System.currentTimeMillis()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Proposal",
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
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = offer.ref,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        StatusDot(text = offerStatusLabel(status), color = offerStatusColor(status))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    DetailLine("Created", formatDate(offer.createdMillis))
                    DetailLine(
                        "Expires",
                        formatDate(offer.expiresMillis) +
                            (if (status == OfferStatus.PENDING) {
                                " (" + daysLeftText(SoftwareEngine.offerDaysLeft(offer, eff)) + ")"
                            } else {
                                ""
                            })
                    )
                }
            }

            item(key = "client") {
                FlatCard {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Client",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = clientTypeLabel(offer.clientType),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = offer.clientName,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (offer.clientType == ClientType.COMPANY) {
                        DetailLine("Contact person", offer.contactName)
                    }
                    DetailLine("Email", offer.email)
                    DetailLine("Phone", offer.phone)
                    DetailLine("Country", offer.country)
                }
            }

            item(key = "want") {
                FlatCard {
                    SectionTitle("What they want")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = offer.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = kindLabel(offer.kind),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = offer.summary, style = MaterialTheme.typography.bodyMedium)
                }
            }

            item(key = "requirements") {
                FlatCard {
                    SectionTitle("Requirements")
                    Spacer(modifier = Modifier.height(6.dp))
                    for (line in offer.requirements) {
                        Text(
                            text = "• " + line,
                            modifier = Modifier.padding(vertical = 2.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    DetailLine(
                        "Delivery time",
                        offer.deliveryWeeks.toString() + (if (offer.deliveryWeeks == 1) " week" else " weeks")
                    )
                }
            }

            item(key = "money") {
                FlatCard {
                    SectionTitle("Offer")
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = formatMoney(offer.feeUnits, offer.currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!isNgn) {
                        Text(
                            text = "≈ " + formatNaira(
                                SoftwareEngine.ngnEstimateFor(offer.feeUnits, offer.currency, sw.rates)
                            ) + " at today's rates",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isCode) {
                            "Payment: 50% now, 50% on handover. Full handoff, no maintenance."
                        } else {
                            "Payment: 50% now, 50% on delivery. Maintenance: 10% of the fee per month (" +
                                formatMoney(offer.feeUnits / 10, offer.currency) + ")."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (isNgn) {
                            "If you accept: " + formatNaira(half) + " is added to your income now."
                        } else {
                            "If you accept: " + formatUsd(halfUsdCents) + " goes to your \$ account."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item(key = "decision") {
                when (status) {
                    OfferStatus.PENDING -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            TintedButton(
                                text = "Decline",
                                onClick = { confirmDecline = true },
                                modifier = Modifier.weight(1f),
                                destructive = true
                            )
                            TintedButton(
                                text = "Accept",
                                onClick = { confirmAccept = true },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    OfferStatus.ACCEPTED -> {
                        FlatCard {
                            Text(
                                text = "Accepted on " + formatDate(offer.decidedMillis),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (project != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                TintedButton(
                                    text = "Open project",
                                    onClick = { onOpenProject(project.id) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    OfferStatus.DECLINED -> {
                        Text(
                            text = "Declined on " + formatDate(offer.decidedMillis),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OfferStatus.EXPIRED -> {
                        Text(
                            text = "This proposal expired on " + formatDate(offer.expiresMillis) + ".",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item(key = "end") { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (confirmAccept) {
        ConfirmDialog(
            heading = "Accept proposal?",
            message = if (isNgn) {
                "Accept " + offer.clientName + "'s proposal? " + formatNaira(half) +
                    " (50% upfront) is added to your income now."
            } else {
                "Accept " + offer.clientName + "'s proposal? " + formatMoney(half, offer.currency) +
                    " (50% upfront, about " + formatUsd(halfUsdCents) +
                    " at today's rate) goes into your \$ account. " +
                    "It is not company profit until you convert it."
            },
            confirmLabel = "Accept",
            onConfirm = {
                confirmAccept = false
                onAccept()
            },
            onDismiss = { confirmAccept = false }
        )
    }

    if (confirmDecline) {
        ConfirmDialog(
            heading = "Decline proposal?",
            message = "Decline " + offer.clientName + "'s proposal? No money is added.",
            confirmLabel = "Decline",
            onConfirm = {
                confirmDecline = false
                onDecline()
            },
            onDismiss = { confirmDecline = false },
            destructive = true
        )
    }
}
