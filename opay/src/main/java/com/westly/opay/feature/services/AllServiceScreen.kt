package com.westly.opay.feature.services

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.westly.opay.core.theme.OPay
import com.westly.opay.core.theme.OPayShapes
import com.westly.opay.core.theme.OPayType
import com.westly.opay.core.ui.components.OPayCard
import com.westly.opay.core.ui.components.OPayUnderlineField
import com.westly.opay.core.ui.icons.OPayIcon
import com.westly.opay.core.ui.icons.OPayIconView
import com.westly.opay.core.ui.overlay.EmptyState
import com.westly.opay.core.ui.overlay.OPayToastHost
import com.westly.opay.core.ui.overlay.rememberOPayToast
import com.westly.opay.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

private const val TILE_COLUMNS = 4
private const val MAX_RECENT_SERVICES = 8
private const val NOT_AVAILABLE_MESSAGE = ""

private data class ServiceTile(
    val label: String,
    val route: String? = null,
    val hot: Boolean = false
)

private data class ServiceSection(val title: String, val tiles: List<ServiceTile>)

private val ServiceSections = listOf(
    ServiceSection(
        "E-commerce",
        listOf(ServiceTile("AliExpress"), ServiceTile("Gift Cards"), ServiceTile("Chowdeck"))
    ),
    ServiceSection(
        "International Services",
        listOf(ServiceTile("Remit"))
    ),
    ServiceSection(
        "Bills Payment",
        listOf(
            ServiceTile("Tuition Payment", hot = true),
            ServiceTile("Electricity"),
            ServiceTile("Solar"),
            ServiceTile("Products and Services"),
            ServiceTile("School & Exam"),
            ServiceTile("Internet Services"),
            ServiceTile("Financial Services"),
            ServiceTile("Invoice Payments"),
            ServiceTile("Aid Grants"),
            ServiceTile("Religious"),
            ServiceTile("Government Payments"),
            ServiceTile("Embassies"),
            ServiceTile("International Travel"),
            ServiceTile("Transport & Hotel")
        )
    ),
    ServiceSection(
        "Finance",
        listOf(
            ServiceTile("OWealth", Routes.Owealth),
            ServiceTile("Fixed", Routes.Fixed),
            ServiceTile("SafeBox", Routes.SafeBox),
            ServiceTile("Targets", Routes.Targets),
            ServiceTile("Spend & Save", Routes.SpendSave)
        )
    ),
    ServiceSection(
        "Rewards",
        listOf(
            ServiceTile("Daily Check-In"),
            ServiceTile("Play4Child"),
            ServiceTile("Invitation", Routes.Invite)
        )
    ),
    ServiceSection(
        "Others",
        listOf(
            ServiceTile("Physical Card", Routes.Cards),
            ServiceTile("Virtual Card", Routes.Cards)
        )
    )
)

private val AllServiceTiles: List<ServiceTile> = ServiceSections.flatMap { it.tiles }

/**
 * Remembers the tiles opened while the app process is alive, newest first.
 */
private object RecentServices {
    private val state = MutableStateFlow<List<String>>(emptyList())

    val labels: StateFlow<List<String>> = state.asStateFlow()

    fun record(label: String) {
        state.value = (listOf(label) + state.value.filterNot { it == label }).take(MAX_RECENT_SERVICES)
    }
}

@Composable
fun AllServiceScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    val toast = rememberOPayToast()
    val recentLabels by RecentServices.labels.collectAsStateWithLifecycle()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val needle = query.trim()
    fun List<ServiceTile>.matching(): List<ServiceTile> =
        if (needle.isEmpty()) this else filter { it.label.contains(needle, ignoreCase = true) }

    val recentTiles = recentLabels
        .mapNotNull { label -> AllServiceTiles.firstOrNull { it.label == label } }
        .matching()
    val sections = ServiceSections
        .map { ServiceSection(it.title, it.tiles.matching()) }
        .filter { it.tiles.isNotEmpty() }

    fun open(tile: ServiceTile) {
        val route = tile.route
        if (route != null) {
            RecentServices.record(tile.label)
            onNavigate(route)
        }
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
                    .padding(horizontal = 12.dp, vertical = 14.dp),
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
                    OPayIconView(icon = OPayIcon.Back, size = 22.dp, tint = c.text)
                }
                Text(
                    text = "All Service",
                    style = OPayType.title,
                    color = c.text,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clickable {
                            searching = !searching
                            if (!searching) query = ""
                        },
                    contentAlignment = Alignment.Center
                ) {
                    MagnifierIcon(tint = c.text)
                }
            }
            if (searching) {
                OPayUnderlineField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Search",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    leading = { MagnifierIcon(tint = c.muted) }
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 12.dp, top = 8.dp, end = 12.dp)
            ) {
                if (recentTiles.isNotEmpty()) {
                    ServiceCard(title = "Recently Used", tiles = recentTiles, onOpen = { open(it) })
                }
                sections.forEach { section ->
                    ServiceCard(title = section.title, tiles = section.tiles, onOpen = { open(it) })
                }
                if (needle.isNotEmpty() && sections.isEmpty()) {
                    EmptyState(message = "No results")
                }
                Spacer(Modifier.navigationBarsPadding().height(8.dp))
            }
        }
        OPayToastHost(state = toast, bottomPadding = 90.dp)
    }
}

@Composable
private fun ServiceCard(
    title: String,
    tiles: List<ServiceTile>,
    onOpen: (ServiceTile) -> Unit
) {
    OPayCard {
        Text(
            text = title,
            style = OPayType.body,
            fontWeight = FontWeight.Bold,
            color = OPay.colors.text
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(TILE_COLUMNS).forEach { rowTiles ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowTiles.forEach { tile ->
                        ServiceTileView(
                            tile = tile,
                            onClick = { onOpen(tile) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(TILE_COLUMNS - rowTiles.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceTileView(
    tile: ServiceTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = OPay.colors
    Column(
        modifier = modifier
            .clip(OPayShapes.tile)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(36.dp)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(c.cardAlt, OPayShapes.tile),
                contentAlignment = Alignment.Center
            ) {
                OPayIconView(icon = OPayIcon.Bag, size = 20.dp, tint = c.green)
            }
            if (tile.hot) {
                Text(
                    text = "HOT",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 8.dp)
                        .clip(OPayShapes.chip)
                        .background(c.danger)
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = tile.label,
            fontSize = 12.sp,
            color = c.text,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MagnifierIcon(tint: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val unit = size.minDimension / 24f
        drawCircle(
            color = tint,
            radius = 6.5f * unit,
            center = Offset(10f * unit, 10f * unit),
            style = Stroke(width = 2f * unit, cap = StrokeCap.Round)
        )
        drawLine(
            color = tint,
            start = Offset(15f * unit, 15f * unit),
            end = Offset(21f * unit, 21f * unit),
            strokeWidth = 2f * unit,
            cap = StrokeCap.Round
        )
    }
}
