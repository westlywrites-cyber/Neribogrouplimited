package com.westly.opay.feature.statement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayButton
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val ItemHeight = 44.dp
private const val VISIBLE_ROWS = 5

/** The date picker: three wheels (day, month, year) held inside [bounds]. Nothing outside them can be confirmed. */
@Composable
fun StatementDatePickerSheet(
    visible: Boolean,
    bounds: DateBounds?,
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        if (bounds != null) {
            PickerContent(bounds = bounds, initial = initial, onDismiss = onDismiss, onConfirm = onConfirm)
        }
    }
}

@Composable
private fun PickerContent(
    bounds: DateBounds,
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val c = OPay.colors
    val start = (initial ?: bounds.max).let { StatementPeriods.clamp(it.year, it.monthValue, it.dayOfMonth, bounds) }
    val years = (bounds.min.year..bounds.max.year).toList()
    val dayState = rememberLazyListState(start.dayOfMonth - 1)
    val monthState = rememberLazyListState(start.monthValue - 1)
    val yearState = rememberLazyListState(years.indexOf(start.year).coerceAtLeast(0))
    val itemPx = with(LocalDensity.current) { ItemHeight.toPx() }
    val day = selectedIndex(dayState, itemPx, 31) + 1
    val month = selectedIndex(monthState, itemPx, 12) + 1
    val year = years[selectedIndex(yearState, itemPx, years.size)]
    val settled = !dayState.isScrollInProgress && !monthState.isScrollInProgress && !yearState.isScrollInProgress
    val candidate = StatementPeriods.clamp(year, month, day, bounds)
    val valid = candidate.dayOfMonth == day && candidate.monthValue == month && candidate.year == year

    LaunchedEffect(day, month, year, settled) {
        if (settled && !valid) {
            coroutineScope {
                launch { dayState.animateScrollToItem(candidate.dayOfMonth - 1) }
                launch { monthState.animateScrollToItem(candidate.monthValue - 1) }
                launch { yearState.animateScrollToItem(years.indexOf(candidate.year).coerceAtLeast(0)) }
            }
        }
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Close, size = 18.dp, tint = c.text)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(ItemHeight * VISIBLE_ROWS)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(ItemHeight)
                .clip(OPayShapes.tile)
                .background(c.cardAlt)
        )
        Row(modifier = Modifier.fillMaxSize()) {
            Wheel(dayState, (1..31).map { "%02d".format(it) }, day - 1, Modifier.weight(1f))
            Wheel(monthState, (1..12).map { "%02d".format(it) }, month - 1, Modifier.weight(1f))
            Wheel(yearState, years.map { it.toString() }, years.indexOf(year), Modifier.weight(1f))
        }
    }
    OPayButton(
        text = "Confirm",
        onClick = { onConfirm(candidate) },
        modifier = Modifier.padding(top = 16.dp),
        enabled = settled && valid
    )
}

private fun selectedIndex(state: LazyListState, itemPx: Float, count: Int): Int {
    val scrolled = state.firstVisibleItemIndex * itemPx + state.firstVisibleItemScrollOffset
    return (scrolled / itemPx).roundToInt().coerceIn(0, count - 1)
}

@Composable
private fun Wheel(state: LazyListState, labels: List<String>, selected: Int, modifier: Modifier) {
    val c = OPay.colors
    LazyColumn(
        state = state,
        flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = PaddingValues(vertical = ItemHeight * 2),
        modifier = modifier.fillMaxSize()
    ) {
        itemsIndexed(labels) { index, label ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ItemHeight),
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
