package com.westly.opay.feature.transfer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.westly.opay.OPayApplication
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.EmptyState
import com.westly.opay.core.ui.overlay.OPayDialog
import com.westly.opay.data.model.Bank
import com.westly.opay.feature.receipt.BankLogoImage
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private val logoPalette = listOf(
    Color(0xFF1F6F52),
    Color(0xFF2F6BB3),
    Color(0xFF8E44AD),
    Color(0xFFC0792B),
    Color(0xFFB03A48),
    Color(0xFF3A8D9B)
)

private const val OPAY_LABEL = "OPay"

private val frequentlyUsed = listOf(
    "Access Bank" to "Access Bank",
    "United Bank For Africa" to "UBA",
    "First Bank Of Nigeria" to "First Bank",
    "Guaranty Trust Bank" to "GTBank",
    "Zenith Bank" to "Zenith Bank"
)

private const val LEADING_ITEMS = 2

internal fun bankLogoColor(name: String): Color =
    logoPalette[(name.hashCode() and Int.MAX_VALUE) % logoPalette.size]

@Composable
internal fun BankLogo(name: String, size: Dp) {
    val c = OPay.colors
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        BankLogoImage(
            bankName = name,
            logoSize = size * 0.7f,
            fallback = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFFDDF5EC), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(OPayIcon.Bank, size = size * 0.5f, tint = c.green)
                }
            }
        )
    }
}

private fun letterOf(name: String): Char {
    val first = name.first().uppercaseChar()
    return if (first in 'A'..'Z') first else '#'
}

@Composable
fun SelectBankScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TransferViewModel = viewModel<TransferViewModel>(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
) {
    val c = OPay.colors
    val context = LocalContext.current
    val repository = remember { (context.applicationContext as OPayApplication).repository }
    val banks by repository.banksState.collectAsStateWithLifecycle()
    val unavailable by repository.banksUnavailable.collectAsStateWithLifecycle()
    var noticeClosed by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val sections = remember(banks) {
        banks.groupBy { letterOf(it.name) }
            .mapValues { (_, list) -> list.sortedBy { it.name.lowercase() } }
            .toSortedMap()
    }
    val headerIndex = remember(sections) {
        val map = LinkedHashMap<Char, Int>()
        var position = LEADING_ITEMS
        sections.forEach { (letter, list) ->
            map[letter] = position
            position += 1 + list.size
        }
        map
    }
    val indexLetters = remember(sections) {
        (if ('#' in sections) listOf('#') else emptyList()) + ('A'..'Z').toList()
    }
    val frequent = remember(banks) {
        val internal = banks.firstOrNull { it.isInternal }?.let { OPAY_LABEL to it }
        listOfNotNull(internal) + frequentlyUsed.mapNotNull { (label, name) ->
            banks.firstOrNull { it.name == name && !it.isInternal }?.let { label to it }
        }
    }
    val searching = query.isNotBlank()
    val results = remember(banks, query) {
        if (query.isBlank()) emptyList() else banks.filter { it.name.contains(query.trim(), ignoreCase = true) }
    }

    val pick: (Bank) -> Unit = { bank ->
        viewModel.selectBank(bank)
        onBack()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Back"; role = Role.Button }
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    OPayIconView(icon = OPayIcon.Close, size = 22.dp, tint = c.text)
                }
                Text(text = "Select Bank", style = OPayType.title, color = c.text)
            }
            SearchField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            if (banks.isEmpty() || (searching && results.isEmpty())) {
                EmptyState(message = if (banks.isEmpty()) "No bank available" else "No bank found")
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 12.dp, end = 32.dp)
                ) {
                    if (searching) {
                        items(results, key = { it.code }) { bank ->
                            BankRow(bank = bank, onClick = { pick(bank) })
                        }
                    } else {
                        item(key = "label_frequent") {
                            Text(
                                text = "Frequently Used Bank",
                                style = OPayType.body,
                                color = c.muted,
                                modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp)
                            )
                        }
                        item(key = "grid_frequent") {
                            FrequentGrid(items = frequent, onPick = pick)
                        }
                        sections.forEach { (letter, list) ->
                            item(key = "header_$letter") {
                                Text(
                                    text = letter.toString(),
                                    style = OPayType.caption,
                                    fontWeight = FontWeight.Bold,
                                    color = c.muted,
                                    modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 2.dp)
                                )
                            }
                            items(list, key = { it.code }) { bank ->
                                BankRow(bank = bank, onClick = { pick(bank) })
                            }
                        }
                    }
                    item(key = "bottom_inset") {
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
        if (!searching && banks.isNotEmpty()) {
            IndexBar(
                letters = indexLetters,
                onIndex = { index ->
                    val letter = indexLetters[index]
                    val target = headerIndex.entries.firstOrNull { it.key >= letter }?.value
                        ?: headerIndex.values.lastOrNull()
                    if (target != null) scope.launch { listState.scrollToItem(target) }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp, top = 56.dp)
            )
        }
        if (unavailable && !noticeClosed) {
            OPayDialog(
                title = "Banks are not available yet",
                message = "The bank list has not been set up. Please try again later.",
                primaryText = "OK",
                onPrimary = { noticeClosed = true },
                onDismiss = { noticeClosed = true }
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val fieldStyle = TextStyle(fontSize = 16.sp, color = c.text)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(c.card, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(modifier = Modifier.size(18.dp)) {
            val unit = size.minDimension / 24f
            drawCircle(
                color = c.muted,
                radius = 6.5f * unit,
                center = Offset(10f * unit, 10f * unit),
                style = Stroke(width = 2f * unit)
            )
            drawLine(
                color = c.muted,
                start = Offset(15f * unit, 15f * unit),
                end = Offset(21f * unit, 21f * unit),
                strokeWidth = 2f * unit,
                cap = StrokeCap.Round
            )
        }
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = fieldStyle,
            cursorBrush = SolidColor(c.green),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(text = "Search Bank Name", style = fieldStyle, color = c.muted)
                    }
                    innerTextField()
                }
            }
        )
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = "Clear search"; role = Role.Button }
                    .clickable { onValueChange("") },
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Close, size = 16.dp, tint = c.muted)
            }
        }
    }
}

@Composable
private fun FrequentGrid(items: List<Pair<String, Bank>>, onPick: (Bank) -> Unit) {
    val c = OPay.colors
    Column(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEach { (label, bank) ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(OPayShapes.tile)
                            .background(c.card)
                            .clickable { onPick(bank) }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = OPayType.caption,
                            color = c.text,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(3 - rowItems.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BankRow(bank: Bank, onClick: () -> Unit) {
    val c = OPay.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BankLogo(name = bank.name, size = 40.dp)
            Text(
                text = bank.name,
                style = OPayType.body,
                color = c.text,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(c.divider)
        )
    }
}

@Composable
private fun IndexBar(
    letters: List<Char>,
    onIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val currentOnIndex by rememberUpdatedState(onIndex)
    Column(
        modifier = modifier
            .width(24.dp)
            .fillMaxHeight(0.82f)
            .pointerInput(letters.size) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val count = letters.size
                    fun report(y: Float) {
                        val height = size.height
                        if (height > 0) {
                            currentOnIndex((y / height * count).toInt().coerceIn(0, count - 1))
                        }
                    }
                    report(down.position.y)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        report(change.position.y)
                        change.consume()
                    } while (change.pressed)
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { letter ->
            Text(
                text = letter.toString(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = c.green
            )
        }
    }
}
