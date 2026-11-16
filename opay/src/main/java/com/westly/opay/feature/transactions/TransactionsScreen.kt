package com.westly.opay.feature.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayChip
import com.westly.opay.core.ui.components.OPayTopBar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.EmptyState
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.SkeletonBlock
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.data.model.Transaction
import com.westly.opay.feature.transfer.SelectedTransaction
import java.time.YearMonth

private const val FOOTER_TEXT = "Only transactions made within the past 1 year are displayed"
private const val ARROW = " \u25BE"

@Composable
fun TransactionsScreen(
    onBack: () -> Unit,
    onOpenDetails: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = viewModel<TransactionsViewModel>()
) {
    val c = OPay.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast = rememberOPayToast()

    var categorySheet by rememberSaveable { mutableStateOf(false) }
    var statusSheet by rememberSaveable { mutableStateOf(false) }
    var pickerVisible by rememberSaveable { mutableStateOf(false) }
    var pickerKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var analysisKey by rememberSaveable { mutableStateOf<String?>(null) }

    val open: (Transaction) -> Unit = { tx ->
        SelectedTransaction.select(tx)
        onOpenDetails()
    }
    val analysisGroup = state.groups.firstOrNull { it.key == analysisKey }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            OPayTopBar(
                title = "Transactions",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = 8.dp),
                actionText = "Download",
                onAction = onDownload
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OPayChip(
                    text = state.category + ARROW,
                    selected = state.category != ALL_CATEGORIES,
                    onClick = { categorySheet = true }
                )
                OPayChip(
                    text = state.status + ARROW,
                    selected = state.status != ALL_STATUS,
                    onClick = { statusSheet = true }
                )
            }
            when {
                state.loading -> Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
                ) {
                    repeat(3) {
                        SkeletonBlock(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            height = 150.dp
                        )
                    }
                }
                state.groups.isEmpty() -> EmptyState(message = "No transactions")
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 10.dp,
                        bottom = 16.dp
                    )
                ) {
                    items(state.groups, key = { it.key }) { group ->
                        GroupCard(
                            group = group,
                            onPickMonth = {
                                pickerKey = group.key
                                pickerVisible = true
                            },
                            onAnalysis = { analysisKey = group.key },
                            onOpen = open
                        )
                    }
                    item(key = "footer") {
                        Text(
                            text = FOOTER_TEXT,
                            style = OPayType.caption,
                            color = c.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
        if (analysisGroup != null) {
            AnalysisOverlay(
                group = analysisGroup,
                onBack = { analysisKey = null }
            )
        }
        OptionSheet(
            visible = categorySheet,
            options = TransactionCategories,
            selected = state.category,
            onSelect = {
                viewModel.setCategory(it)
                categorySheet = false
            },
            onDismiss = { categorySheet = false }
        )
        OptionSheet(
            visible = statusSheet,
            options = TransactionStatuses,
            selected = state.status,
            onSelect = {
                viewModel.setStatus(it)
                statusSheet = false
            },
            onDismiss = { statusSheet = false }
        )
        MonthPickerSheet(
            visible = pickerVisible,
            initialMonth = YearMonth.parse(pickerKey),
            onDismiss = { pickerVisible = false },
            onConfirmMonth = {
                viewModel.selectMonth(it)
                pickerVisible = false
            },
            onConfirmRange = { start, end ->
                viewModel.selectRange(start, end)
                pickerVisible = false
            },
            onClear = {
                viewModel.clearSelection()
                pickerVisible = false
            }
        )
        OPayToastHost(state = toast, bottomPadding = 24.dp)
    }
}

@Composable
private fun GroupCard(
    group: MonthGroup,
    onPickMonth: () -> Unit,
    onAnalysis: () -> Unit,
    onOpen: (Transaction) -> Unit
) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(OPayShapes.card)
            .background(c.card)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = group.label + ARROW,
                style = OPayType.body,
                fontWeight = FontWeight.Bold,
                color = c.text,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onPickMonth)
                    .padding(vertical = 4.dp)
            )
            Text(
                text = "Analysis",
                style = OPayType.caption,
                fontWeight = FontWeight.Bold,
                color = c.onGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.green)
                    .clickable(onClick = onAnalysis)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }
        Text(
            text = "In: " + group.inText + "   Out: " + group.outText,
            style = OPayType.caption,
            color = c.muted,
            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
        )
        group.items.forEach { tx ->
            TransactionRow(transaction = tx, onClick = { onOpen(tx) })
        }
    }
}

@Composable
private fun OptionSheet(
    visible: Boolean,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        val c = OPay.colors
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(option) }
                    .padding(horizontal = 20.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = option,
                    style = OPayType.body,
                    color = if (option == selected) c.green else c.text,
                    modifier = Modifier.weight(1f)
                )
                if (option == selected) {
                    OPayIconView(icon = OPayIcon.Check, size = 18.dp, tint = c.green)
                }
            }
        }
    }
}
