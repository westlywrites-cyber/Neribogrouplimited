package com.westly.opay.feature.transactions

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private const val NO_DATE = Long.MIN_VALUE
private const val WHEEL_ROWS = 5
private const val FIRST_YEAR = 2025
private const val LAST_YEAR = 2026
private val WheelItemHeight = 40.dp

@Composable
fun MonthPickerSheet(
    visible: Boolean,
    initialMonth: YearMonth,
    onDismiss: () -> Unit,
    onConfirmMonth: (YearMonth) -> Unit,
    onConfirmRange: (LocalDate, LocalDate) -> Unit,
    onClear: () -> Unit
) {
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        val c = OPay.colors
        var tab by rememberSaveable { mutableIntStateOf(0) }
        val years = remember(initialMonth) {
            val low = minOf(FIRST_YEAR, initialMonth.year)
            val high = maxOf(LAST_YEAR, LocalDate.now().year, initialMonth.year)
            (low..high).toList()
        }
        var monthIndex by rememberSaveable(initialMonth) { mutableIntStateOf(initialMonth.monthValue - 1) }
        var yearIndex by rememberSaveable(initialMonth) { mutableIntStateOf(years.indexOf(initialMonth.year)) }
        var startDay by rememberSaveable { mutableLongStateOf(NO_DATE) }
        var endDay by rememberSaveable { mutableLongStateOf(NO_DATE) }
        val rangeValid = startDay != NO_DATE && endDay != NO_DATE && startDay <= endDay
        val rangeInverted = startDay != NO_DATE && endDay != NO_DATE && startDay > endDay

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                PickerTab(text = "Month", selected = tab == 0, onClick = { tab = 0 })
                PickerTab(text = "Time Frame", selected = tab == 1, onClick = { tab = 1 })
            }
            if (tab == 0) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WheelColumn(
                        items = (1..12).map { String.format(Locale.US, "%02d", it) },
                        initialIndex = monthIndex,
                        onSelected = { monthIndex = it },
                        modifier = Modifier.weight(1f)
                    )
                    WheelColumn(
                        items = years.map { it.toString() },
                        initialIndex = yearIndex.coerceAtLeast(0),
                        onSelected = { yearIndex = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    DateField(
                        label = "Start date",
                        epochDay = startDay,
                        onPicked = { startDay = it }
                    )
                    DateField(
                        label = "End date",
                        epochDay = endDay,
                        onPicked = { endDay = it }
                    )
                    if (rangeInverted) {
                        Text(
                            text = "End date must be after start date",
                            style = OPayType.caption,
                            color = c.danger,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
            OPayButton(
                text = "Confirm",
                onClick = {
                    if (tab == 0) {
                        onConfirmMonth(YearMonth.of(years[yearIndex.coerceAtLeast(0)], monthIndex + 1))
                    } else {
                        onConfirmRange(LocalDate.ofEpochDay(startDay), LocalDate.ofEpochDay(endDay))
                    }
                },
                modifier = Modifier.padding(top = 18.dp),
                enabled = tab == 0 || rangeValid
            )
            Text(
                text = "Clear",
                style = OPayType.body,
                color = c.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClear)
                    .padding(vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun PickerTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = OPayType.body,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) c.green else c.muted
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) c.green else c.cardAlt)
        )
    }
}

@Composable
private fun DateField(label: String, epochDay: Long, onPicked: (Long) -> Unit) {
    val c = OPay.colors
    val context = LocalContext.current
    val text = if (epochDay == NO_DATE) {
        label
    } else {
        val day = LocalDate.ofEpochDay(epochDay)
        day.dayOfMonth.toString() + " " + MonthNames[day.monthValue - 1] + " " + day.year
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val base = if (epochDay == NO_DATE) LocalDate.now() else LocalDate.ofEpochDay(epochDay)
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onPicked(LocalDate.of(year, month + 1, dayOfMonth).toEpochDay())
                    },
                    base.year,
                    base.monthValue - 1,
                    base.dayOfMonth
                ).show()
            }
    ) {
        Text(
            text = text,
            style = OPayType.body,
            color = if (epochDay == NO_DATE) c.muted else c.text,
            modifier = Modifier.padding(vertical = 13.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.divider)
        )
    }
}

@Composable
private fun WheelColumn(
    items: List<String>,
    initialIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val itemPx = with(LocalDensity.current) { WheelItemHeight.toPx() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val selected by remember {
        derivedStateOf {
            val bump = if (listState.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0
            (listState.firstVisibleItemIndex + bump).coerceIn(0, items.lastIndex)
        }
    }
    LaunchedEffect(selected) { onSelected(selected) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                if (listState.firstVisibleItemScrollOffset != 0) {
                    listState.animateScrollToItem(selected)
                }
            }
    }
    Box(
        modifier = modifier.height(WheelItemHeight * WHEEL_ROWS),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WheelItemHeight)
                .clip(OPayShapes.tile)
                .background(c.cardAlt)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = WheelItemHeight * (WHEEL_ROWS / 2))
        ) {
            itemsIndexed(items) { index, label ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(WheelItemHeight)
                        .clickable { scope.launch { listState.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = OPayType.title,
                        fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (index == selected) c.text else c.muted
                    )
                }
            }
        }
    }
}
