package com.westly.opay.feature.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.ui.components.OPayRingAvatar
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.OPayBottomSheet
import com.westly.opay.core.ui.overlay.OPayLoader
import com.westly.opay.core.util.formatNaira
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

private val AnalysisZone: ZoneId = ZoneId.of("Africa/Lagos")
private val BarGreen = Color(0xFF2A6D44)
private val RankPink = Color(0xFFE85C86)
private val ConfirmBlue = Color(0xFF8C97D9)
private val DonutColors = listOf(
    Color(0xFF663E01),
    Color(0xFF453400),
    Color(0xFF55400A),
    Color(0xFF3B2C00),
    Color(0xFF4D3705)
)
private const val LOAD_MILLIS = 450L
private const val VISIBLE_DAYS = 5
private const val FIRST_YEAR = 2025
private const val WHEEL_ROWS = 5
private val WheelItemHeight = 40.dp
private val AxisWidth = 46.dp
private val ChartTop = 44.dp
private val ChartPlot = 150.dp
private val ChartFoot = 26.dp

private fun monthLabel(month: YearMonth): String =
    MonthNames[month.monthValue - 1] + "." + month.year

private fun spaced(kobo: Long): String = "\u20A6 " + formatNaira(kobo).removePrefix("\u20A6")

@Composable
fun AnalysisOverlay(
    group: MonthGroup,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember(context) { (context.applicationContext as OPayApplication).repository }
    val all by repository.transactions.collectAsStateWithLifecycle()
    var monthKey by rememberSaveable { mutableStateOf(group.key) }
    var sideIndex by rememberSaveable { mutableIntStateOf(0) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    val month = YearMonth.parse(monthKey)
    val side = if (sideIndex == 0) AnalysisSide.Expenses else AnalysisSide.Income
    val dark = c.bg.luminance() < 0.5f
    val panel = if (dark) Color(0xFF1F1F21) else c.card

    LaunchedEffect(monthKey, sideIndex) {
        loading = true
        delay(LOAD_MILLIS)
        loading = false
    }
    BackHandler(onBack = onBack)

    val stats = remember(all, month, side) {
        buildAnalysisStats(all, month, side, LocalDate.now(AnalysisZone), AnalysisZone)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(c.card)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Back"; role = Role.Button }
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
                }
                Text(text = "Statistical Analysis", fontSize = 20.sp, color = c.text)
            }
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OPayLoader()
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf("Expenses", "Income").forEachIndexed { index, label ->
                        val selected = index == sideIndex
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { sideIndex = index },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = if (selected) c.green else c.text.copy(alpha = 0.75f),
                                modifier = Modifier.padding(vertical = 14.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(2.dp)
                                    .background(if (selected) c.green else Color.Transparent)
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { pickerOpen = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = monthLabel(month), fontSize = 14.sp, color = c.muted)
                        Spacer(Modifier.width(8.dp))
                        Canvas(modifier = Modifier.size(width = 10.dp, height = 6.dp)) {
                            val path = Path().apply {
                                moveTo(0f, 0f)
                                lineTo(size.width, 0f)
                                lineTo(size.width / 2f, size.height)
                                close()
                            }
                            drawPath(path = path, color = c.muted)
                        }
                    }
                    Text(
                        text = "Total " + stats.count + " " + (if (side == AnalysisSide.Expenses) "Expenses" else "Income"),
                        fontSize = 14.sp,
                        color = c.muted,
                        modifier = Modifier.padding(top = 14.dp)
                    )
                    Text(
                        text = spaced(stats.totalKobo),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = c.text,
                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                    )
                    AnalysisPanel(
                        title = if (side == AnalysisSide.Expenses) "Daily Expenses" else "Daily Income",
                        background = panel
                    ) {
                        DayChart(days = stats.days)
                        Text(
                            text = "\u2039 Swipe to view more days \u203A",
                            fontSize = 11.sp,
                            color = c.muted.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 8.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    AnalysisPanel(title = "Bill distributed", background = panel) {
                        BillDonut(slices = stats.bills)
                    }
                    Spacer(Modifier.height(12.dp))
                    AnalysisPanel(title = "Leaderboard", background = panel) {
                        Leaderboard(rows = stats.leaders)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        MonthSheet(
            visible = pickerOpen,
            current = month,
            onDismiss = { pickerOpen = false },
            onConfirm = {
                monthKey = it.toString()
                pickerOpen = false
            }
        )
    }
}

@Composable
private fun AnalysisPanel(
    title: String,
    background: Color,
    content: @Composable () -> Unit
) {
    val c = OPay.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = c.text,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

@Composable
private fun DayChart(days: List<DayBar>) {
    val c = OPay.colors
    val measurer = rememberTextMeasurer()
    val maxNaira = (days.maxOfOrNull { it.kobo } ?: 0L) / 100.0
    val axis = remember(maxNaira) { niceAxis(maxNaira) }
    val axisStyle = TextStyle(fontSize = 11.sp, color = c.muted)
    val valueStyle = TextStyle(fontSize = 11.sp, color = BarGreen)
    val chartHeight = ChartTop + ChartPlot + ChartFoot
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val viewport = maxWidth - AxisWidth
        val slots = days.size.coerceIn(1, VISIBLE_DAYS)
        val slotWidth = viewport / slots
        Row(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .width(AxisWidth)
                    .height(chartHeight)
            ) {
                val top = ChartTop.toPx()
                val plot = ChartPlot.toPx()
                axis.ticks.forEach { tick ->
                    val y = top + plot * (1f - (tick / axis.top).toFloat())
                    val label = measurer.measure(formatAxisTick(tick, axis.step), axisStyle)
                    drawText(
                        textLayoutResult = label,
                        topLeft = Offset(
                            this.size.width - label.size.width - 6.dp.toPx(),
                            y - label.size.height / 2f
                        )
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(viewport)
                    .horizontalScroll(rememberScrollState())
            ) {
                Canvas(
                    modifier = Modifier
                        .width(slotWidth * days.size.coerceAtLeast(1))
                        .height(chartHeight)
                ) {
                    val top = ChartTop.toPx()
                    val plot = ChartPlot.toPx()
                    val slot = slotWidth.toPx()
                    val barWidth = slot * 0.5f
                    val baseline = top + plot
                    drawLine(
                        color = BarGreen,
                        start = Offset(0f, baseline),
                        end = Offset(this.size.width, baseline),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    days.forEachIndexed { index, day ->
                        val centerX = slot * index + slot / 2f
                        val dayLabel = measurer.measure(day.label, axisStyle)
                        drawText(
                            textLayoutResult = dayLabel,
                            topLeft = Offset(centerX - dayLabel.size.width / 2f, baseline + 6.dp.toPx())
                        )
                        if (day.kobo > 0L) {
                            val height = ((day.kobo / 100.0) / axis.top * plot).toFloat().coerceAtLeast(1.5.dp.toPx())
                            drawRect(
                                color = BarGreen,
                                topLeft = Offset(centerX - barWidth / 2f, baseline - height),
                                size = Size(barWidth, height)
                            )
                            val value = measurer.measure(formatNaira(day.kobo), valueStyle)
                            val anchor = Offset(centerX - barWidth / 2f, baseline - height - 4.dp.toPx())
                            rotate(degrees = -40f, pivot = anchor) {
                                drawText(
                                    textLayoutResult = value,
                                    topLeft = Offset(anchor.x, anchor.y - value.size.height)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BillDonut(slices: List<BillSlice>) {
    val c = OPay.colors
    val measurer = rememberTextMeasurer()
    val total = slices.sumOf { it.kobo }
    if (total <= 0L) {
        Text(
            text = "No data",
            fontSize = 14.sp,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 40.dp)
        )
        return
    }
    val amountStyle = TextStyle(fontSize = 13.sp, color = c.text.copy(alpha = 0.6f))
    val nameStyle = TextStyle(fontSize = 11.sp, color = c.muted.copy(alpha = 0.7f))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
    ) {
        val outer = 70.dp.toPx()
        val inner = 40.dp.toPx()
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val ring = (outer + inner) / 2f
        var start = -90f
        val labels = mutableListOf<Triple<Int, Float, Offset>>()
        slices.forEachIndexed { index, slice ->
            val sweep = (slice.kobo.toDouble() / total * 360.0).toFloat()
            drawArc(
                color = DonutColors[index % DonutColors.size],
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(center.x - ring, center.y - ring),
                size = Size(ring * 2f, ring * 2f),
                style = Stroke(width = outer - inner)
            )
            val mid = Math.toRadians((start + sweep / 2f).toDouble())
            val edge = Offset(
                center.x + (outer * Math.cos(mid)).toFloat(),
                center.y + (outer * Math.sin(mid)).toFloat()
            )
            labels.add(Triple(index, mid.toFloat(), edge))
            start += sweep
        }
        val minGap = 38.dp.toPx()
        val limit = this.size.height - 30.dp.toPx()
        listOf(true, false).forEach { rightSide ->
            val group = labels
                .filter { (Math.cos(it.second.toDouble()) >= 0.0) == rightSide }
                .sortedBy { it.third.y }
            var previous = -minGap
            group.forEach { (index, _, edge) ->
                val wanted = center.y + (edge.y - center.y) * 1.25f
                val y = maxOf(wanted, previous + minGap).coerceAtMost(limit)
                previous = y
                val slice = slices[index]
                val amount = measurer.measure(formatNaira(slice.kobo), amountStyle)
                val name = measurer.measure(slice.name, nameStyle)
                val textX = if (rightSide) center.x + outer + 22.dp.toPx()
                else center.x - outer - 22.dp.toPx() - maxOf(amount.size.width, name.size.width)
                val anchorX = if (rightSide) textX - 4.dp.toPx() else textX + maxOf(amount.size.width, name.size.width) + 4.dp.toPx()
                drawLine(
                    color = DonutColors[index % DonutColors.size],
                    start = edge,
                    end = Offset(anchorX, y + amount.size.height / 2f),
                    strokeWidth = 1.dp.toPx()
                )
                drawText(textLayoutResult = amount, topLeft = Offset(textX, y))
                drawText(textLayoutResult = name, topLeft = Offset(textX, y + amount.size.height))
            }
        }
    }
}

@Composable
private fun Leaderboard(rows: List<LeaderRow>) {
    val c = OPay.colors
    if (rows.isEmpty()) {
        Text(
            text = "No data",
            fontSize = 14.sp,
            color = c.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        )
        return
    }
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
        rows.take(5).forEachIndexed { index, row ->
            if (index > 0) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 34.dp)
                        .height(1.dp)
                        .background(c.muted.copy(alpha = 0.15f))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = (index + 1).toString() + ".",
                    fontSize = 15.sp,
                    color = if (index < 3) RankPink else c.muted,
                    modifier = Modifier.width(28.dp)
                )
                LeaderIcon(kind = row.kind)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = row.name,
                    fontSize = 15.sp,
                    color = c.text.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                )
                Text(text = spaced(row.kobo), fontSize = 15.sp, color = c.text.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun LeaderIcon(kind: LeaderKind) {
    val green = OPay.colors.green
    if (kind == LeaderKind.Other) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(Color(0xFF2CB67D), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            OPayIconView(icon = OPayIcon.Doc, size = 18.dp, tint = Color.White)
        }
        return
    }
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Color(0xFFE5E5E5), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            LeaderKind.MoneySent, LeaderKind.MoneyReceived -> OPayRingAvatar(size = 22.dp)
            LeaderKind.TransferBank -> OPayIconView(
                icon = OPayIcon.Forward,
                modifier = Modifier.rotate(-90f),
                size = 18.dp,
                tint = green
            )
            LeaderKind.MobileData -> OPayIconView(icon = OPayIcon.Data, size = 18.dp, tint = green)
            LeaderKind.Airtime -> OPayIconView(icon = OPayIcon.Phone, size = 18.dp, tint = green)
            LeaderKind.BankDeposit -> OPayIconView(icon = OPayIcon.Bank, size = 18.dp, tint = green)
            LeaderKind.Other -> Unit
        }
    }
}

@Composable
private fun MonthSheet(
    visible: Boolean,
    current: YearMonth,
    onDismiss: () -> Unit,
    onConfirm: (YearMonth) -> Unit
) {
    OPayBottomSheet(visible = visible, onDismiss = onDismiss) {
        val c = OPay.colors
        val now = YearMonth.now(AnalysisZone)
        val years = remember { (FIRST_YEAR..maxOf(now.year, current.year)).toList() }
        var yearIndex by remember(current) { mutableIntStateOf(years.indexOf(current.year).coerceAtLeast(0)) }
        var monthIndex by remember(current) { mutableIntStateOf(current.monthValue - 1) }
        val year = years[yearIndex]
        val monthCount = if (year == now.year) now.monthValue else 12
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Cancel",
                    fontSize = 18.sp,
                    color = c.muted,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onDismiss)
                )
                Text(
                    text = "Confirm",
                    fontSize = 18.sp,
                    color = ConfirmBlue,
                    modifier = Modifier.clickable {
                        onConfirm(YearMonth.of(year, minOf(monthIndex, monthCount - 1) + 1))
                    }
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Wheel(
                    items = years.map { it.toString() },
                    initialIndex = yearIndex,
                    onSelected = { yearIndex = it },
                    modifier = Modifier.weight(1f)
                )
                androidx.compose.runtime.key(year) {
                    Wheel(
                        items = (1..monthCount).map { String.format(Locale.US, "%02d", it) },
                        initialIndex = minOf(monthIndex, monthCount - 1),
                        onSelected = { monthIndex = it },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Wheel(
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
        Column(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(WheelItemHeight * (WHEEL_ROWS / 2)))
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.muted.copy(alpha = 0.25f))
            )
            Spacer(Modifier.height(WheelItemHeight - 1.dp))
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(c.muted.copy(alpha = 0.25f))
            )
        }
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
                        fontSize = 20.sp,
                        color = if (index == selected) c.text else c.muted
                    )
                }
            }
        }
    }
}
