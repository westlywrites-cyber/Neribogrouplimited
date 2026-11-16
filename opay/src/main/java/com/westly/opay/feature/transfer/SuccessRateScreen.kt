package com.westly.opay.feature.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayRowDivider
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.data.model.Bank
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REFRESH_MILLIS = 600L

@Composable
fun SuccessRateScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val c = OPay.colors
    val context = LocalContext.current
    val banks = remember {
        (context.applicationContext as OPayApplication).repository.banks
    }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        OPayTopBar(
            title = "Transfer Success Rate",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp),
            actionText = "Refresh",
            onAction = {
                if (!loading) {
                    scope.launch {
                        loading = true
                        delay(REFRESH_MILLIS)
                        loading = false
                    }
                }
            }
        )
        if (loading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OPayLoader()
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .clip(OPayShapes.card)
                    .background(c.card)
            ) {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(banks, key = { it.code }) { bank ->
                        SuccessRateRow(bank)
                    }
                    item(key = "bottom_inset") {
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }
}

@Composable
private fun SuccessRateRow(bank: Bank) {
    val c = OPay.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BankLogo(name = bank.name, size = 24.dp)
            Text(
                text = bank.name,
                style = OPayType.body,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${bank.successRate}%",
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = c.green
            )
        }
        OPayRowDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
