package com.westly.neribogroup.feature.group

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.PLAY_FEE_PERCENT
import com.westly.neribogroup.data.PlayTx
import com.westly.neribogroup.data.PlayTxKind
import com.westly.neribogroup.feature.appdetail.PlayAppCard
import com.westly.neribogroup.feature.common.ConfirmDialog
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.LocalBiometricGate
import com.westly.neribogroup.feature.common.SectionTitle
import com.westly.neribogroup.feature.common.StatColumn
import com.westly.neribogroup.feature.common.TintedButton
import com.westly.neribogroup.ui.formatDate
import com.westly.neribogroup.ui.formatNaira

/**
 * The Google Play Balance page. Subscription and renewal money (after Google's 15%) and ad money
 * (after Google's share) wait here, per app and in total. Nothing reaches profit until it is withdrawn.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayBalanceScreen(
    ledger: Ledger,
    onBack: () -> Unit,
    onWithdrawApp: (String) -> Unit,
    onWithdrawAll: () -> Unit
) {
    val context = LocalContext.current
    val gate = LocalBiometricGate.current
    var confirmAll by remember { mutableStateOf(false) }

    val subsTotal = ledger.apps.sumOf { it.playSubsBalance }
    val adsTotal = ledger.apps.sumOf { it.playAdsBalance }
    val total = ledger.playBalanceTotal

    // The newest lines from every app, with the app they belong to.
    val recent: List<Pair<String, PlayTx>> = ledger.apps
        .flatMap { app -> app.playTx.map { app.name to it } }
        .sortedByDescending { it.second.dateMillis }
        .take(20)

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Google Play Balance", fontWeight = FontWeight.SemiBold) },
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
            item {
                FlatCard {
                    Text(
                        text = "Total balance, all apps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatNaira(total),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        StatColumn(
                            label = "Subscriptions",
                            value = formatNaira(subsTotal),
                            modifier = Modifier.weight(1f)
                        )
                        StatColumn(
                            label = "Google Ads",
                            value = formatNaira(adsTotal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TintedButton(
                        text = "Withdraw all",
                        onClick = { confirmAll = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = total > 0L
                    )
                }
            }

            item {
                Text(
                    text = "This money is not in your profit yet. It moves into profit only when you " +
                        "withdraw it. Google Play keeps " + PLAY_FEE_PERCENT +
                        "% of every subscription and renewal, and a share of ad money.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { SectionTitle("Each app") }
            val playApps = ledger.apps.filter { it.googlePlayEnabled }
            if (playApps.isEmpty()) {
                item {
                    Text(
                        text = "No Google Play apps yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(playApps, key = { "play-" + it.id }) { app ->
                PlayAppCard(app = app, onWithdraw = { onWithdrawApp(app.id) })
            }

            item { SectionTitle("Recent activity") }
            if (recent.isEmpty()) {
                item {
                    Text(
                        text = "Nothing yet. Roll the dice in an app and its subscriptions and ads appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(recent, key = { "tx-" + it.second.id }) { entry ->
                val tx = entry.second
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = entry.first + " · " + tx.label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = formatNaira(tx.gross) + " - " + formatNaira(tx.fee) +
                            (if (tx.kind == PlayTxKind.ADS) " Google's share" else " Google Play") +
                            " = " + formatNaira(tx.net) + " · " + formatDate(tx.dateMillis),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            item { SectionTitle("Withdrawal history") }
            if (ledger.withdrawals.none { it.channel == com.westly.neribogroup.data.Channel.PLAY }) {
                item {
                    Text(
                        text = "No withdrawals yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(ledger.withdrawals.filter { it.channel == com.westly.neribogroup.data.Channel.PLAY }, key = { "wd-" + it.id }) { w ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = w.label,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = formatNaira(w.amount),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = formatDate(w.dateMillis) + if (w.note.isNotBlank()) " · " + w.note else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
    }

    if (confirmAll) {
        ConfirmDialog(
            heading = "Withdraw " + formatNaira(total) + "?",
            message = "Every app's balance moves into its profit, and all balances go back to ₦0.",
            confirmLabel = "Withdraw all",
            onConfirm = {
                confirmAll = false
                gate.verify(
                    title = "Withdraw all Google Play balances?",
                    subtitle = formatNaira(total) + " moves into the apps' profit.",
                    logText = "Google Play balances withdrawn for all apps: " + formatNaira(total)
                ) {
                    onWithdrawAll()
                    Toast.makeText(context, "Withdrawn successfully", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { confirmAll = false }
        )
    }
}
