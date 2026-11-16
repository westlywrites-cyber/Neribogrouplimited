package com.westly.opay.feature.biz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.EmptyState
import com.westly.opay.core.util.formatNaira
import com.westly.opay.data.DailyTotals
import com.westly.opay.feature.transactions.formatRowDate

/**
 * The user's successful incoming transfers, newest first, with a Today / All filter.
 * It reads the same live list as My BizPayment, so the count and the total always agree with that screen.
 */
@Composable
fun BizReceivedScreen(
    initialTodayOnly: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val transactions by repository.transactions.collectAsStateWithLifecycle()
    var todayOnly by rememberSaveable { mutableStateOf(initialTodayOnly) }

    val credits = remember(transactions, todayOnly) {
        val all = DailyTotals.receivedCredits(transactions)
        if (todayOnly) {
            val now = System.currentTimeMillis()
            all.filter { DailyTotals.isToday(it.timestampMillis, now) }
        } else {
            all
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        OPayTopBar(
            title = "Received",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OPayChip(text = "Today", selected = todayOnly, onClick = { todayOnly = true })
            OPayChip(text = "All", selected = !todayOnly, onClick = { todayOnly = false })
        }
        if (credits.isEmpty()) {
            EmptyState(message = if (todayOnly) "No money received today" else "No money received yet")
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 16.dp)
            ) {
                items(credits, key = { it.id }) { credit ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = credit.title, style = OPayType.body, color = c.text)
                            val name = credit.counterpartyName.orEmpty()
                            if (name.isNotEmpty()) {
                                Text(
                                    text = name,
                                    style = OPayType.caption,
                                    color = c.muted,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            Text(
                                text = formatRowDate(credit.timestampMillis),
                                style = OPayType.caption,
                                color = c.muted,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+" + formatNaira(credit.amountKobo),
                                style = OPayType.body,
                                fontWeight = FontWeight.Bold,
                                color = c.green
                            )
                            Text(
                                text = credit.status,
                                style = OPayType.caption,
                                color = c.green,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                    OPayRowDivider()
                }
            }
        }
    }
}
