package com.westly.neribogroup.feature.storageroom

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.State
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt
import com.westly.neribogroup.R
import com.westly.neribogroup.cloud.StorageRoomCloud
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.StorageRoomRules
import com.westly.neribogroup.data.StorageRules
import com.westly.neribogroup.feature.common.FlatCard
import com.westly.neribogroup.feature.common.rememberNowMillis
import com.westly.neribogroup.ui.formatCount
import kotlinx.coroutines.delay
import java.util.Locale

/** What the room needs to know about the cloud: is it on, is a sync running and what went wrong last. */
data class RoomCloudInfo(val on: Boolean, val syncing: Boolean, val problem: String)

private val Accent = Color(0xFF7FD1C7)
private val ScreenText = Color(0xFFD9EEFF)
private val ScreenDim = Color(0xFF8FB3D6)
private val ScreenBad = Color(0xFFFF9D94)
private val BarWarn = Color(0xFFFFC27A)
private val BarBad = Color(0xFFFF8A80)
private val BarBack = Color(0x22FFFFFF)

/** A size shown the way the preview shows it: under 1 GB with one decimal in MB, otherwise as the Storage page does. */
private fun sizeText(mb: Long): String =
    if (mb < 1024L) String.format(Locale.US, "%.1f MB", mb.toDouble()) else StorageRules.textMb(mb)

/**
 * The Storage room: its own screen, opened from its own tile on the home dashboard (not from the Storage page).
 * It asks for the access PIN first (and makes the owner create one when there is none). Inside, the desk, the
 * monitor, the keyboard and the console all read the same ledger as the Storage page and change it through the
 * same functions, so a change in either place shows in both at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageRoomScreen(
    ledger: Ledger,
    cloud: RoomCloudInfo,
    cloudFeed: StorageRoomCloud,
    actions: RoomActions,
    onCreatePin: (String) -> Boolean,
    onPullFromCloud: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    // Zoom of the Storage room only: 1.0 is the full size, lower shows the whole room smaller.
    var zoom by rememberSaveable { mutableStateOf(1f) }
    val hasPin = StorageRules.hasPin(ledger.assets)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage room", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (hasPin && unlocked) {
                        IconButton(onClick = { zoom = maxOf(ZOOM_MIN, (Math.round(zoom * 10f) - 1) / 10f) }) {
                            Text("−", fontSize = 24.sp)
                        }
                        Text(
                            text = Math.round(zoom * 100f).toString() + "%",
                            fontSize = 14.sp,
                            modifier = Modifier.clickable { zoom = 1f }.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                        IconButton(onClick = { zoom = minOf(1f, (Math.round(zoom * 10f) + 1) / 10f) }) {
                            Text("+", fontSize = 24.sp)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                !hasPin -> PinCreate(
                    onCreate = { pin ->
                        val saved = onCreatePin(pin)
                        if (saved) {
                            StorageRoomLog.add(RoomLevel.CHANGE, "auth", "access PIN created")
                            unlocked = true
                        }
                        saved
                    }
                )
                !unlocked -> PinEnter(ledger = ledger, onOpened = { unlocked = true })
                else -> RoomContent(ledger, cloud, cloudFeed, actions, onPullFromCloud, zoom) {
                    zoom = (zoom * it).coerceIn(ZOOM_MIN, 1f)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The PIN gate
// ---------------------------------------------------------------------------------------------

@Composable
private fun PinCreate(onCreate: (String) -> Boolean) {
    var first by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val confirming = first.isNotEmpty()
    PinPad(
        title = if (confirming) "Type the PIN again" else "Create your access PIN",
        subtitle = if (confirming) {
            "Type the same PIN once more to confirm it."
        } else {
            "The Storage room is locked with a PIN of 4 to 8 digits. It also guards every time you lower a limit."
        },
        entered = typed.length,
        message = message,
        actionLabel = if (confirming) "Save PIN" else "Next",
        actionEnabled = typed.length >= 4,
        onDigit = { digit ->
            if (typed.length < 8) {
                typed += digit
                message = ""
            }
        },
        onBackspace = { typed = typed.dropLast(1) },
        onAction = {
            if (!confirming) {
                if (StorageRules.isValidPin(typed)) {
                    first = typed
                    typed = ""
                } else {
                    message = "Use 4 to 8 digits."
                }
            } else if (typed == first) {
                if (!onCreate(typed)) {
                    message = "The PIN could not be saved. Try again."
                    first = ""
                    typed = ""
                }
            } else {
                message = "The two PINs are not the same. Start again."
                first = ""
                typed = ""
            }
        }
    )
}

@Composable
private fun PinEnter(ledger: Ledger, onOpened: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    PinPad(
        title = "Storage room",
        subtitle = "Enter your access PIN to open the room.",
        entered = typed.length,
        message = message,
        actionLabel = "Open",
        actionEnabled = typed.length >= 4,
        onDigit = { digit ->
            if (typed.length < 8) {
                typed += digit
                message = ""
            }
        },
        onBackspace = { typed = typed.dropLast(1) },
        onAction = {
            val wait = RoomPinGuard.waitSeconds()
            if (wait > 0L) {
                message = "Too many wrong tries. Wait $wait seconds."
                typed = ""
            } else if (StorageRules.pinMatches(ledger.assets, typed)) {
                RoomPinGuard.success()
                StorageRoomLog.add(RoomLevel.INFO, "auth", "PIN accepted")
                onOpened()
            } else {
                val locked = RoomPinGuard.failure()
                StorageRoomLog.add(RoomLevel.WARN, "auth", "wrong PIN entered")
                message = if (locked) "Too many wrong tries. Wait 30 seconds." else "Wrong PIN. Try again."
                typed = ""
            }
        }
    )
}

@Composable
private fun PinPad(
    title: String,
    subtitle: String,
    entered: Int,
    message: String,
    actionLabel: String,
    actionEnabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(18.dp))
        PinDots(count = entered, color = MaterialTheme.colorScheme.primary, dot = 16.dp)
        Spacer(modifier = Modifier.height(18.dp))
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "<")
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (key in row) {
                        if (key.isEmpty()) {
                            Spacer(modifier = Modifier.size(64.dp))
                        } else {
                            PadKey(label = if (key == "<") "⌫" else key) {
                                if (key == "<") onBackspace() else onDigit(key)
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.height(22.dp)
        )
        Button(onClick = onAction, enabled = actionEnabled) { Text(actionLabel) }
    }
}

@Composable
private fun PadKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 21.sp)
    }
}

@Composable
private fun PinDots(count: Int, color: Color, dot: androidx.compose.ui.unit.Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val slots = maxOf(4, count)
        for (i in 0 until slots) {
            Box(
                modifier = Modifier
                    .size(dot)
                    .clip(CircleShape)
                    .border(2.dp, color, CircleShape)
                    .background(if (i < count) color else Color.Transparent)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The room
// ---------------------------------------------------------------------------------------------

@Composable
private fun RoomContent(
    ledger: Ledger,
    cloud: RoomCloudInfo,
    cloudFeed: StorageRoomCloud,
    actions: RoomActions,
    onPullFromCloud: () -> Unit,
    zoom: Float,
    onPinch: (Float) -> Unit
) {
    val actionsState = rememberUpdatedState(actions)
    val pullState = rememberUpdatedState(onPullFromCloud)
    // The power of the pretend server lives only on this phone (a private file). It never touches the ledger or the cloud.
    val power = rememberRoomPower()
    val powerContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val controller = remember {
        RoomController(
            ledger,
            power = { power.value },
            onPower = { on -> if (on) RoomPower.startBoot(powerContext) else RoomPower.startShutdown(powerContext) }
        ) { actionsState.value }
    }
    controller.update(ledger)

    // The server hum plays while the room is open and the servers run; it stops when the room is left, the app goes
    // to the background, or the server is switched off.
    RoomHum(playing = RoomPower.humPlaying(power.value))

    val apps = StorageRoomRules.roomApps(ledger.apps)
    val now = rememberNowMillis()
    var linked by remember { mutableStateOf(false) }

    // Like the preview, every change to the numbers (an edit, a new user, an off-site copy) makes the sync lights run
    // for a moment, on top of the real cloud sync.
    var pulse by remember { mutableStateOf(false) }
    var firstKey by remember { mutableStateOf(true) }
    val changeKey = listOf(
        StorageRules.usedKb(apps), ledger.assets.offsiteKb, ledger.assets.storageTotalMb, StorageRules.sharesMb(apps)
    )
    LaunchedEffect(changeKey) {
        if (firstKey) {
            firstKey = false
            return@LaunchedEffect
        }
        pulse = true
        delay(1500L)
        pulse = false
        StorageRoomLog.add(RoomLevel.SYNC, "main → backup", "in sync")
    }
    val shownCloud = cloud.copy(syncing = cloud.syncing || pulse)

    // The line under the monitor clears itself after a few seconds.
    LaunchedEffect(controller.message) {
        if (controller.message != null) {
            delay(5000L)
            controller.clearMessage()
        }
    }

    LaunchedEffect(Unit) {
        StorageRoomLog.add(RoomLevel.INFO, "room", "Storage room opened")
    }

    // Owner edits, from the room or from the Storage page: every new line of the storage log is a CHANGE line.
    var seenLog by remember { mutableStateOf(ledger.assets.storageLog.maxOfOrNull { it.millis } ?: 0L) }
    LaunchedEffect(ledger.assets.storageLog) {
        val fresh = ledger.assets.storageLog.filter { it.millis > seenLog }.sortedBy { it.millis }
        if (fresh.isNotEmpty()) {
            seenLog = fresh.last().millis
            for (line in fresh) StorageRoomLog.addUnique(RoomLevel.CHANGE, "owner", line.text)
        }
    }

    // New users of an app.
    val seenUsers = remember { HashMap<String, Long>() }
    val userKey = apps.map { it.id + ":" + it.totalUsers }
    LaunchedEffect(userKey) {
        for (app in apps) {
            val before = seenUsers[app.id]
            val nowUsers = app.totalUsers
            if (before != null && nowUsers > before) {
                val added = nowUsers - before
                StorageRoomLog.add(
                    RoomLevel.INFO, StorageRoomLog.slug(app.name),
                    "+" + formatCount(added) + (if (added == 1L) " new user" else " new users") +
                        " · " + formatCount(nowUsers) + " in total"
                )
            }
            seenUsers[app.id] = nowUsers
        }
    }

    // Storage warnings, worked out by StorageRules.appWarning. A line is added when an app's warning appears or changes kind.
    val seenWarn = remember { HashMap<String, String>() }
    val warnKey = apps.map { it.id + ":" + warningKind(it, now) }
    LaunchedEffect(warnKey) {
        for (app in apps) {
            val kind = warningKind(app, now)
            val before = seenWarn[app.id] ?: ""
            if (kind.isNotEmpty() && kind != before) {
                val warning = StorageRules.appWarning(app, now)
                if (warning != null) StorageRoomLog.add(RoomLevel.WARN, StorageRoomLog.slug(app.name), warning.title)
            } else if (kind.isEmpty() && before.isNotEmpty()) {
                StorageRoomLog.add(RoomLevel.INFO, StorageRoomLog.slug(app.name), "storage warning cleared")
            }
            seenWarn[app.id] = kind
        }
    }

    // The company total: copies over it stop sync and backups.
    val overKb = StorageRules.companyOverKb(ledger.assets, ledger.apps)
    var wasOver by remember { mutableStateOf(overKb > 0L) }
    LaunchedEffect(overKb > 0L) {
        val isOver = overKb > 0L
        if (isOver && !wasOver) StorageRoomLog.add(RoomLevel.ERROR, "server", StorageRules.syncStoppedText(overKb))
        if (!isOver && wasOver) StorageRoomLog.add(RoomLevel.INFO, "server", "company storage fits again, sync can run")
        wasOver = isOver
    }

    // Sync status of the cloud.
    var wasSyncing by remember { mutableStateOf(cloud.syncing) }
    LaunchedEffect(cloud.syncing) {
        if (cloud.syncing && !wasSyncing) StorageRoomLog.add(RoomLevel.SYNC, "cloud", "syncing with the cloud")
        if (!cloud.syncing && wasSyncing) {
            if (cloud.problem.isEmpty()) {
                StorageRoomLog.add(RoomLevel.SYNC, "cloud", "in sync")
            } else {
                StorageRoomLog.add(RoomLevel.WARN, "cloud", cloud.problem)
            }
        }
        wasSyncing = cloud.syncing
    }

    // The live feed from the owner's other phones (Supabase Realtime, table storage_events).
    DisposableEffect(cloudFeed) {
        var lastPull = 0L
        val client = cloudFeed.realtime(
            onState = { connected ->
                if (connected != linked) {
                    linked = connected
                    StorageRoomLog.add(
                        if (connected) RoomLevel.INFO else RoomLevel.WARN,
                        "realtime",
                        if (connected) "live link on" else "live link lost, reconnecting"
                    )
                }
            },
            onEvent = { event ->
                val level = RoomLevel.values().firstOrNull { it.name == event.level } ?: RoomLevel.INFO
                StorageRoomLog.addUnique(level, "phone " + event.deviceId.take(4), event.message)
                if (level == RoomLevel.CHANGE) {
                    // Another phone changed storage: pull its documents so this phone shows the new numbers too.
                    val tick = SystemClock.elapsedRealtime()
                    if (tick - lastPull > 4000L) {
                        lastPull = tick
                        pullState.value()
                    }
                }
            }
        )
        client.start()
        onDispose { client.stop() }
    }

    // The keyboard is docked at the bottom of the screen so it is always visible together with the monitor.
    // The monitor, desk, console and live cards scroll above it.
    Column(modifier = Modifier.fillMaxSize().pinchZoom(onPinch)) {
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            item { RoomScene(controller, ledger, shownCloud, linked, zoom) }
            item { Box(modifier = Modifier.fillMaxWidth().zoomed(zoom)) { LiveCards(ledger, now) } }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
        ) {
            RoomKeyboard(isLit = { controller.isLit(it) }, onKey = { controller.press(it) })
        }
    }
}

/** "" when the app has no warning, "over" when its users hold more than its share, "soon" when it will fill soon. */
private fun warningKind(app: AppData, now: Long): String {
    if (StorageRules.appWarning(app, now) == null) return ""
    return if (StorageRules.overShareKb(app) > 0L) "over" else "soon"
}

@Composable
private fun RoomScene(controller: RoomController, ledger: Ledger, cloud: RoomCloudInfo, linked: Boolean, zoom: Float) {
    // The scene follows the app's own light or dark theme.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = if (dark) Color(0xFF3B3A36) else Color(0xFFECE8DF)
    val wall = if (dark) Color(0xFF31302C) else Color(0xFFE2DDD2)
    val deskTop = if (dark) Color(0xFF6B5638) else Color(0xFFE8D6B8)
    val deskBottom = if (dark) Color(0xFF574429) else Color(0xFFDCBB8C)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to top, 0.18f to wall, 0.18f to deskTop, 1f to deskBottom
                )
            )
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zoomed(zoom)
            .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 22.dp)
    ) {
        Monitor(controller, ledger, cloud)
        DeskPhoto(controller, cloud)
        Text(
            "Tap the keys at the bottom of the screen, or tap lines on the monitor like a mouse click. Keys with a teal outline work right now. " +
                "Tap a server to open Copies, or the rack to open the Log.",
            color = if (dark) Color.White.copy(alpha = 0.9f) else Color(0xFF2A2A2A),
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 10.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        RoomConsole()
    }
    }
}

/** The lowest zoom of the Storage room. */
private const val ZOOM_MIN = 0.3f

/**
 * Shows the content smaller (zoom below 1) as one picture, centred at the top, and gives the layout the smaller height,
 * so there is no empty space below it. The content is laid out at its normal width, so nothing inside changes shape.
 */
private fun Modifier.zoomed(zoom: Float): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val height = (placeable.height * zoom).roundToInt()
    layout(placeable.width, height) {
        placeable.placeWithLayer(0, 0) {
            scaleX = zoom
            scaleY = zoom
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    }
}

/** Two fingers zoom the room. One finger is left alone, so taps, scrolling and the keyboard work as before. */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.size >= 2) {
                val change = event.calculateZoom()
                if (change != 1f) {
                    onZoom(change)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

// ---------------------------------------------------------------------------------------------
// The monitor
// ---------------------------------------------------------------------------------------------

@Composable
private fun Monitor(controller: RoomController, ledger: Ledger, cloud: RoomCloudInfo) {
    val scroll = rememberScrollState()
    LaunchedEffect(controller.page) { scroll.scrollTo(0) }
    val message = controller.message
    val power = controller.powerView()
    val dark = power.phase == PowerPhase.OFF || power.phase == PowerPhase.BOOTING
    val footColor = if (message == null) {
        Color(0xFFD6EBFF)
    } else if (message.bad) {
        Color(0xFFFFB0A8)
    } else {
        Color(0xFF8CF0B5)
    }
    // The Dell frame and stand are one picture (462 x 388). Its edges are stretched around the screen,
    // exactly as the preview does with border-image: 9 wide on the top and sides, 127 at the bottom, all scaled by width / 462.
    val bezel = ImageBitmap.imageResource(R.drawable.room_bezel)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val k = maxWidth.value / 462f
        val edge = (9f * k).dp
        val foot = (127f * k).dp
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { drawBezel(bezel, 9f * k * density, 127f * k * density) }
                .padding(start = edge, end = edge, top = edge, bottom = foot)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(410.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (dark) Brush.linearGradient(listOf(Color.Black, Color.Black))
                        else Brush.linearGradient(listOf(Color(0xFF0B2D6B), Color(0xFF06142E)))
                    )
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (power.phase == PowerPhase.ON) MonitorTabs(controller)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(scroll)
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        when {
                            controller.warn -> PowerWarnPanel()
                            power.busy -> PowerProgressPanel(power)
                            power.phase == PowerPhase.OFF -> PowerOffPanel()
                            else -> Unit
                        }
                        if (power.phase == PowerPhase.ON && !controller.warn) when (controller.page) {
                            RoomController.OVERVIEW -> OverviewPage(ledger, cloud)
                            RoomController.APPS -> AppsPage(controller, ledger)
                            RoomController.PLANS -> PlansPage(controller, ledger)
                            RoomController.COPIES -> CopiesPage(controller, ledger, cloud)
                            else -> LogPage(ledger)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x40000000))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                            .height(54.dp)
                    ) {
                        Text(
                            text = message?.text ?: controller.hint(),
                            color = footColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
                val pin = controller.pin
                if (pin != null) PinOverlay(pin)
            }
        }
    }
}

/** Draws the 8 border pieces of the monitor picture (slice 5 left, 3 top, 3 right, 127 bottom); the middle is left empty for the screen. */
private fun DrawScope.drawBezel(image: ImageBitmap, edgePx: Float, footPx: Float) {
    val w = size.width.toInt()
    val h = size.height.toInt()
    val e = edgePx.toInt()
    val f = footPx.toInt()
    fun piece(sx: Int, sy: Int, sw: Int, sh: Int, dx: Int, dy: Int, dw: Int, dh: Int) {
        if (dw <= 0 || dh <= 0) return
        drawImage(image, IntOffset(sx, sy), IntSize(sw, sh), IntOffset(dx, dy), IntSize(dw, dh))
    }
    piece(0, 0, 5, 3, 0, 0, e, e)
    piece(5, 0, 454, 3, e, 0, w - 2 * e, e)
    piece(459, 0, 3, 3, w - e, 0, e, e)
    piece(0, 3, 5, 258, 0, e, e, h - e - f)
    piece(459, 3, 3, 258, w - e, e, e, h - e - f)
    piece(0, 261, 5, 127, 0, h - f, e, f)
    piece(5, 261, 454, 127, e, h - f, w - 2 * e, f)
    piece(459, 261, 3, 127, w - e, h - f, e, f)
}

@Composable
private fun MonitorTabs(controller: RoomController) {
    val names = listOf("Overview", "Apps", "Plans", "Copies", "Log")
    val keys = listOf("O", "A", "P", "C", "L")
    var clock by remember { mutableStateOf(StorageRoomLog.timeText(System.currentTimeMillis())) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = StorageRoomLog.timeText(System.currentTimeMillis())
            delay(1000L)
        }
    }
    Row(modifier = Modifier.fillMaxWidth().background(Color(0x33000000))) {
        for (i in names.indices) {
            val on = controller.page == i + 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) Accent else Color.Transparent)
                    .clickable { controller.tapTab(i + 1) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = keys[i] + " " + names[i],
                    color = if (on) Color(0xFF04202A) else Color(0xFF9FC3E6),
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
        Text(
            text = clock,
            color = Color(0xFF9FC3E6),
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun PinOverlay(pin: RoomPin) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xEE030A18)).clickable { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MonText(pin.title, Color.White, 15f, bold = true)
            MonText(
                if (pin.title.startsWith("Turn off")) "This takes all your apps offline, so type your PIN on the keyboard."
                else "This lowers a limit, so type your PIN on the keyboard.",
                ScreenDim,
                12f
            )
            PinDots(count = pin.buf.length, color = Accent, dot = 13.dp)
            MonText(pin.message, ScreenBad, 12f)
            MonText("Enter checks it. Esc cancels.", ScreenDim, 11f)
        }
    }
}

// ---- small pieces of the monitor ----

@Composable
private fun MonText(
    text: String,
    color: Color = ScreenText,
    size: Float = 12.5f,
    bold: Boolean = false,
    modifier: Modifier = Modifier,
    underline: Boolean = false
) {
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        lineHeight = (size * 1.4f).sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textDecoration = if (underline) TextDecoration.Underline else null,
        modifier = modifier
    )
}

@Composable
private fun MonLine(left: String, right: String, rightColor: Color = Color.White) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MonText(left, modifier = Modifier.weight(1f))
        MonText(right, rightColor, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MonBar(valueKb: Long, ofKb: Long) {
    val percent = if (ofKb > 0L) valueKb.toDouble() / ofKb.toDouble() * 100.0 else 0.0
    val color = if (percent > 100.0) BarBad else if (percent > 85.0) BarWarn else Accent
    val fraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, bottom = 6.dp)
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(BarBack)
    ) {
        Box(modifier = Modifier.fillMaxWidth(fraction).height(6.dp).background(color))
    }
}

@Composable
private fun MonRow(selected: Boolean, onTap: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val barColor = if (selected) Accent else Color.Transparent
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) Color(0x2B7FD1C7) else Color.Transparent)
            .drawBehind { drawRect(color = barColor, size = Size(3.dp.toPx(), size.height)) }
            .clickable { onTap() }
            .padding(start = 9.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        content = content
    )
}

@Composable
private fun Cell(text: String, picked: Boolean, onTap: () -> Unit) {
    MonText(
        text = text,
        color = if (picked) Accent else Color.White,
        underline = picked,
        modifier = Modifier.clickable { onTap() }
    )
}

@Composable
private fun EditCell(edit: RoomEdit, withUnit: Boolean) {
    val text = (if (edit.buf.isEmpty()) "_" else edit.buf) + "▌" + (if (withUnit) " " + edit.unit.label else "")
    Text(
        text = text,
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.5.sp,
        modifier = Modifier.clip(RoundedCornerShape(3.dp)).background(Color(0x447FD1C7)).padding(horizontal = 4.dp)
    )
}

// ---- the five pages ----

@Composable
private fun OverviewPage(ledger: Ledger, cloud: RoomCloudInfo) {
    val assets = ledger.assets
    val apps = StorageRoomRules.roomApps(ledger.apps)
    val totalMb = assets.storageTotalMb
    val liveKb = StorageRules.usedKb(apps)
    val copiesKb = StorageRules.copiesKb(assets, apps)
    val overKb = StorageRules.companyOverKb(assets, apps)
    // Each server holds two 24 TB drives: about 43 TB of room for the live data (and the same again for the backup).
    val ssdKb = 43L * 1024L * 1024L * 1024L
    val syncing = cloud.syncing
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(
                "NERIBO GROUP LIMITED",
                color = Color(0xFF8FB0FF),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text("Company Storage", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(Color(0xFF43D17A)))
            Spacer(modifier = Modifier.width(5.dp))
            Text("Online", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SsdCard(
            "Main Storage",
            StorageRules.text(liveKb) + " used · " + StorageRules.text(maxOf(0L, ssdKb - liveKb)) + " free",
            liveKb,
            ssdKb,
            Modifier.weight(1f)
        )
        SsdCard(
            "Backup Storage",
            when {
                overKb > 0L -> "Stopped: over the total"
                syncing -> "Syncing…"
                else -> "Copy of main drive"
            },
            liveKb,
            ssdKb,
            Modifier.weight(1f)
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 8.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SyncPill("Router online", false)
        SyncPill("Nightly backup", false)
        SyncPill(if (syncing) "Syncing" else "Server on", syncing)
    }
    MonLine("Company storage", if (totalMb > 0L) StorageRules.textMb(totalMb) else "Not set")
    MonBar(copiesKb, totalMb * 1024L)
    MonLine("Used by users (live)", StorageRules.text(liveKb))
    MonLine("Used by all copies", StorageRules.text(copiesKb))
    MonLine("Given to apps", StorageRules.textMb(StorageRules.sharesMb(apps)))
    when {
        totalMb <= 0L -> MonLine("Free", "total not set")
        overKb > 0L -> MonLine("Over the total by", StorageRules.text(overKb), ScreenBad)
        else -> MonLine("Free", StorageRules.text(totalMb * 1024L - copiesKb))
    }
    Spacer(modifier = Modifier.height(6.dp))
    MonText("Each app", ScreenDim)
    for (app in apps) {
        val usedKb = app.storageAllocatedKb
        MonLine(
            app.name,
            StorageRules.text(usedKb) + " / " + (if (app.storageShareMb > 0L) StorageRules.textMb(app.storageShareMb) else "no share")
        )
        MonBar(usedKb, app.storageShareMb * 1024L)
    }
}

/** One of the two server cards of the Overview: the orange bar shows the live data against the server's 43 TB. */
@Composable
private fun SsdCard(title: String, note: String, valueKb: Long, ofKb: Long, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x0DFFFFFF))
            .border(1.dp, Color(0xAA5F86D6), RoundedCornerShape(10.dp))
            .padding(8.dp)
    ) {
        Text(title, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
        Text("IronWolf Pro 24 TB × 2", color = Color(0xFF9FC3E6), fontSize = 9.5.sp)
        Spacer(modifier = Modifier.height(6.dp))
        OrangeBar(valueKb, ofKb)
        Text(note, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun OrangeBar(valueKb: Long, ofKb: Long) {
    val percent = if (ofKb > 0L) valueKb.toDouble() / ofKb.toDouble() * 100.0 else 0.0
    val color = if (percent > 100.0) Color(0xFFFF6B5E) else if (percent > 85.0) Color(0xFFFFD23F) else Color(0xFFF7931E)
    val fraction = (maxOf(2.0, minOf(100.0, percent)) / 100.0).toFloat()
    Box(modifier = Modifier.fillMaxWidth().height(9.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFF0A1D44))) {
        Box(modifier = Modifier.fillMaxWidth(fraction).height(9.dp).clip(RoundedCornerShape(5.dp)).background(color))
    }
}

/** A status pill. While syncing its light pulses amber, like the preview. */
@Composable
private fun SyncPill(text: String, syncing: Boolean) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x880A1D44))
            .border(1.dp, Color(0xAA5F86D6), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (syncing) SyncDot() else Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF43D17A)))
        Spacer(modifier = Modifier.width(5.dp))
        Text(text, color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SyncDot() {
    val transition = rememberInfiniteTransition(label = "pill")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pillAlpha"
    )
    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(BarWarn.copy(alpha = alpha)))
}

@Composable
private fun AppsPage(controller: RoomController, ledger: Ledger) {
    val assets = ledger.assets
    val apps = StorageRoomRules.roomApps(ledger.apps)
    val edit = controller.edit
    MonRow(selected = controller.sel == 0, onTap = { controller.tapRow(0, 0) }) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MonText("Company total", Color.White, bold = true)
            if (edit != null && edit.kind == RoomEditKind.TOTAL) {
                EditCell(edit, true)
            } else {
                MonText(if (assets.storageTotalMb > 0L) StorageRules.textMb(assets.storageTotalMb) else "not set", Color.White)
            }
        }
        MonText("copies use " + StorageRules.text(StorageRules.copiesKb(assets, apps)), ScreenDim, 11.5f)
    }
    for ((index, app) in apps.withIndex()) {
        val usedKb = app.storageAllocatedKb
        val overShareKb = StorageRules.overShareKb(app)
        MonRow(selected = controller.sel == index + 1, onTap = { controller.tapRow(index + 1, 0) }) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MonText(app.name, Color.White, bold = true, modifier = Modifier.weight(1f))
                if (edit != null && edit.kind == RoomEditKind.SHARE && edit.appId == app.id) {
                    EditCell(edit, true)
                } else {
                    MonText(
                        if (app.storageShareMb > 0L) StorageRules.textMb(app.storageShareMb) else "no share",
                        Color.White
                    )
                }
            }
            if (app.storageShareMb > 0L) MonBar(usedKb, app.storageShareMb * 1024L)
            if (overShareKb > 0L) {
                MonText("Over its share by " + StorageRules.text(overShareKb), ScreenBad, 11.5f)
            } else if (app.storageShareMb > 0L) {
                MonText("Free: " + StorageRules.text(app.storageShareMb * 1024L - usedKb), ScreenDim, 11.5f)
            } else {
                MonText("uses " + StorageRules.text(usedKb), ScreenDim, 11.5f)
            }
        }
    }
    if (apps.isEmpty()) MonText("No app to show yet.", ScreenDim)
}

@Composable
private fun PlansPage(controller: RoomController, ledger: Ledger) {
    val apps = StorageRoomRules.roomApps(ledger.apps)
    if (apps.isEmpty()) {
        MonText("No app to show yet.", ScreenDim)
        return
    }
    val app = apps[controller.shownAppIndex()]
    val edit = controller.edit
    val usedKb = app.storageAllocatedKb
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = edit == null && controller.pin == null) { controller.press("Tab") },
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        MonText("◀ " + app.name + " ▶", Color.White, bold = true)
        MonText("Tab: next app", ScreenDim, 11f)
    }
    MonBar(usedKb, app.storageShareMb * 1024L)
    MonText(
        if (app.storageShareMb > 0L) {
            StorageRules.text(usedKb) + " of its share of " + StorageRules.textMb(app.storageShareMb)
        } else {
            "uses " + StorageRules.text(usedKb) + ". No share set yet."
        },
        ScreenDim,
        11.5f
    )
    Spacer(modifier = Modifier.height(6.dp))
    for (row in 0..3) {
        val users = StorageRoomRules.planUsers(app, row)
        val sizeMb = StorageRoomRules.planSizeMb(app, row)
        val e = edit
        val editingHere = e != null && e.appId == app.id && e.row == row &&
            (e.kind == RoomEditKind.SIZE || e.kind == RoomEditKind.USERS)
        MonRow(selected = controller.sel == row, onTap = { controller.tapRow(row, controller.col) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MonText(StorageRoomRules.PLAN_NAMES[row], Color.White, bold = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (row == StorageRoomRules.CUSTOM) {
                        MonText(formatCount(users) + (if (users == 1L) " user" else " users"), Color.White)
                    } else {
                        if (e != null && editingHere && e.kind == RoomEditKind.USERS) {
                            EditCell(e, false)
                        } else {
                            Cell(formatCount(users), controller.sel == row && controller.col == 1) {
                                controller.tapRow(row, 1)
                            }
                        }
                        MonText(" × ", ScreenDim)
                        if (e != null && editingHere && e.kind == RoomEditKind.SIZE) {
                            EditCell(e, true)
                        } else {
                            Cell(sizeText(sizeMb), controller.sel == row && controller.col == 0) {
                                controller.tapRow(row, 0)
                            }
                        }
                    }
                }
            }
            val note = if (row == StorageRoomRules.CUSTOM) {
                "= " + StorageRules.textMb(StorageRoomRules.planTotalMb(app, row)) + " · set for each user in the app"
            } else if (StorageRoomRules.usersEditable(app, row)) {
                "= " + StorageRules.textMb(StorageRoomRules.planTotalMb(app, row))
            } else {
                "= " + StorageRules.textMb(StorageRoomRules.planTotalMb(app, row)) + " · users come from the app"
            }
            MonText(note, ScreenDim, 11.5f)
        }
    }
    Spacer(modifier = Modifier.height(6.dp))
    val shows = (0..3)
        .filter { StorageRoomRules.planUsers(app, it) > 0L }
        .joinToString(" · ") { StorageRoomRules.PLAN_NAMES[it] + " " + sizeText(StorageRoomRules.planSizeMb(app, it)) }
    MonText(app.name + " app now shows: " + shows, ScreenDim, 11.5f)
}

@Composable
private fun CopiesPage(controller: RoomController, ledger: Ledger, cloud: RoomCloudInfo) {
    val assets = ledger.assets
    val apps = StorageRoomRules.roomApps(ledger.apps)
    val liveKb = StorageRules.usedKb(apps)
    val totalKb = assets.storageTotalMb * 1024L
    val overKb = StorageRules.companyOverKb(assets, apps)
    val ofText = if (totalKb > 0L) "of " + StorageRules.text(totalKb) else "no company total set"

    MonRow(selected = controller.sel == 0, onTap = { controller.tapRow(0, 0) }) {
        MonLine("SSD main · live copy", StorageRules.text(liveKb))
        MonBar(liveKb, totalKb)
        MonText(ofText, ScreenDim, 11.5f)
    }
    MonRow(selected = controller.sel == 1, onTap = { controller.tapRow(1, 0) }) {
        MonLine("SSD backup · standby copy", StorageRules.text(liveKb))
        MonBar(liveKb, totalKb)
        val status = when {
            overKb > 0L -> "backup stopped: company storage is over its total"
            cloud.syncing -> "Syncing…"
            else -> "In sync, follows live in real time"
        }
        MonText(status, if (overKb > 0L) ScreenBad else ScreenDim, 11.5f)
    }
    MonRow(selected = controller.sel == 2, onTap = { controller.tapRow(2, 0) }) {
        val made = assets.offsiteMillis > 0L
        MonLine("Off-site copy", if (made) StorageRules.text(assets.offsiteKb) else "never made")
        MonBar(if (made) assets.offsiteKb else 0L, totalKb)
        val gap = StorageRules.offsiteGapKb(assets, liveKb)
        val note = when {
            !made -> "Press Enter to make the first off-site copy."
            gap == 0L -> "Up to date. Press Enter to make a new copy."
            else -> StorageRules.text(gap) + " different from live. Press Enter to refresh it."
        }
        MonText(note, ScreenDim, 11.5f)
    }
    Spacer(modifier = Modifier.height(6.dp))
    MonLine("All copies together", StorageRules.text(StorageRules.copiesKb(assets, apps)))
    if (overKb > 0L) MonLine("Over the total by", StorageRules.text(overKb), ScreenBad)
}

@Composable
private fun LogPage(ledger: Ledger) {
    val lines = ledger.assets.storageLog.take(40)
    if (lines.isEmpty()) {
        MonText("No change has been made yet.", ScreenDim)
        return
    }
    for (line in lines) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            MonText(StorageRoomLog.timeText(line.millis) + " ", ScreenDim, 11.5f)
            MonText(line.text, ScreenText, 11.5f, modifier = Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The server room: one photo with the rack (two servers), two UPS units, router and Starlink
// ---------------------------------------------------------------------------------------------

/** The desk is drawn in a 1448 x 1086 space and scaled to the screen width, like the preview's stage. */
private const val DESK_W = 1448f
private const val DESK_H = 1086f

private fun wirePaths(): List<Path> {
    fun path(build: Path.() -> Unit): Path = Path().apply(build)
    return listOf(
        // Router to the main server: the blue cable.
        path {
            moveTo(1140f, 480f)
            cubicTo(1132f, 540f, 1100f, 580f, 1040f, 580f)
            cubicTo(990f, 580f, 950f, 574f, 925f, 564f)
            cubicTo(880f, 546f, 830f, 492f, 760f, 458f)
        },
        // Main server to the backup server: the 10G link.
        path { moveTo(578f, 522f); lineTo(578f, 596f) },
        // Starlink to the router.
        path { moveTo(1302f, 482f); lineTo(1282f, 482f) }
    )
}

@Composable
private fun DeskPhoto(controller: RoomController, cloud: RoomCloudInfo) {
    val power = controller.powerView()
    val picture = RoomPower.picture(power)
    val photo = ImageBitmap.imageResource(
        when (picture) {
            "off" -> R.drawable.room_pwr_off
            "s1" -> R.drawable.room_pwr_s1
            "s2" -> R.drawable.room_pwr_s2
            else -> R.drawable.room_desk
        }
    )
    val wires = remember { wirePaths() }
    // The sync lines only move while the cloud is syncing; otherwise nothing animates and nothing redraws.
    val transition = rememberInfiniteTransition(label = "wires")
    val phase: State<Float> = if (cloud.syncing) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 40f,
            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
            label = "wirePhase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    // The sync lines and drive lights only show while the servers are running.
    val syncing = cloud.syncing && picture == "on"
    BoxWithConstraints(modifier = Modifier.fillMaxWidth().offset(y = (-6).dp)) {
        val w = maxWidth
        val h = w * (DESK_H / DESK_W)
        Box(modifier = Modifier.fillMaxWidth().height(h)) {
            Image(
                bitmap = photo,
                contentDescription = "Server room: rack with two servers, two UPS units, router and Starlink",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        // The photo fades into the room background at its top and bottom edges (gently, so the AC stays clear).
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent, 0.035f to Color.Black, 0.955f to Color.Black, 1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
            )
            // Data lines between the devices, and the drive lights.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val s = size.width / DESK_W
                scale(s, Offset.Zero) {
                    if (syncing) {
                        val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 30f), 40f - phase.value)
                        for (wire in wires) {
                            drawPath(wire, Color(0x407FD1C7), style = Stroke(width = 12f, cap = StrokeCap.Round, pathEffect = dash))
                            drawPath(wire, Color(0xFF7FD1C7), style = Stroke(width = 5f, cap = StrokeCap.Round, pathEffect = dash))
                        }
                    }
                    // The drive lights of the two servers (main on top, backup below).
                    if (picture == "on") {
                        for (c in listOf(Offset(427f, 484f), Offset(427f, 504f), Offset(427f, 623f), Offset(427f, 643f))) {
                            drawCircle(Color(0x5543D17A), radius = 9f, center = c)
                        }
                    }
                }
            }
            // Tap areas: the rack opens the Log, either server opens Copies.
            val tapSource = remember { MutableInteractionSource() }
            Box(
                modifier = Modifier
                    .offset(x = w * 0.207f, y = h * 0.170f)
                    .size(width = w * 0.432f, height = h * 0.686f)
                    .clickable(interactionSource = tapSource, indication = null) { controller.tapTab(5) }
            )
            Box(
                modifier = Modifier
                    .offset(x = w * 0.262f, y = h * 0.405f)
                    .size(width = w * 0.266f, height = h * 0.097f)
                    .clickable(interactionSource = tapSource, indication = null) { controller.tapTab(4) }
            )
            Box(
                modifier = Modifier
                    .offset(x = w * 0.262f, y = h * 0.540f)
                    .size(width = w * 0.266f, height = h * 0.090f)
                    .clickable(interactionSource = tapSource, indication = null) { controller.tapTab(4) }
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The live copy of the Storage cards
// ---------------------------------------------------------------------------------------------

@Composable
private fun LiveCards(ledger: Ledger, now: Long) {
    val assets = ledger.assets
    val apps = StorageRoomRules.roomApps(ledger.apps)
    val totalMb = assets.storageTotalMb
    val liveKb = StorageRules.usedKb(apps)
    val copiesKb = StorageRules.copiesKb(assets, apps)
    val freeKb = totalMb * 1024L - copiesKb
    Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 28.dp)) {
        Text(
            "Live in the Neribo Group app",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
        )
        FlatCard(padding = 16.dp) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Company storage", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    if (totalMb > 0L) StorageRules.textMb(totalMb) else "Not set",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SegmentBar(copiesKb, totalMb * 1024L)
            Text(
                text = "Live " + StorageRules.text(liveKb) + " · All copies " + StorageRules.text(copiesKb) +
                    " · Given to apps " + StorageRules.textMb(StorageRules.sharesMb(apps)) +
                    (if (totalMb <= 0L) "" else if (freeKb < 0L) " · Over by " + StorageRules.text(-freeKb) else " · Free " + StorageRules.text(freeKb)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        for (app in apps) {
            Spacer(modifier = Modifier.height(10.dp))
            val usedKb = app.storageAllocatedKb
            val plans = (0..2).filter { StorageRoomRules.planUsers(app, it) > 0L }.map {
                StorageRoomRules.PLAN_NAMES[it] + " " + formatCount(StorageRoomRules.planUsers(app, it)) +
                    " × " + sizeText(StorageRoomRules.planSizeMb(app, it))
            }
            val warning = StorageRules.appWarning(app, now)
            FlatCard(padding = 16.dp) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(app.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(StorageRules.text(usedKb), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = (if (app.storageShareMb > 0L) "of its share of " + StorageRules.textMb(app.storageShareMb) else "No share set yet") +
                        (if (plans.isEmpty()) "" else " · " + plans.joinToString(" · ")),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
                SegmentBar(usedKb, app.storageShareMb * 1024L)
                if (warning != null) {
                    Text(
                        text = warning.title,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/** The thin two-part bar of the Storage cards: the used part and the rest. */
@Composable
private fun SegmentBar(valueKb: Long, ofKb: Long) {
    val percent = if (ofKb > 0L) (valueKb.toDouble() / ofKb.toDouble() * 100.0).coerceIn(0.0, 100.0) else 0.0
    val used = percent.toFloat()
    Row(modifier = Modifier.fillMaxWidth().height(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (used > 0.5f) {
            Box(modifier = Modifier.weight(used).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF80CBC4)))
        }
        if (used < 99.5f) {
            Box(modifier = Modifier.weight(100f - used).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFF4B4458)))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The power screens of the monitor (shown on this phone only)
// ---------------------------------------------------------------------------------------------

@Composable
private fun PowerWarnPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MonText("⚠ Turn off the server?", Color(0xFFFFC24D), 16f, bold = true)
        MonText("This takes ALL your apps offline until you switch the server on again.")
        MonText("Shutdown takes about 5 minutes. Starting up again takes about 10 minutes.")
        MonText("Enter continues. Esc cancels.", ScreenDim, 11.5f)
    }
}

@Composable
private fun PowerOffPanel() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MonText("Server is off.", ScreenDim, 13f)
        MonText("Press PWR to switch it on.", ScreenDim, 12f)
    }
}

@Composable
private fun PowerProgressPanel(power: PowerView) {
    val lines = RoomPower.lines(power).takeLast(11)
    val verb = if (power.phase == PowerPhase.SHUTTING) "Shutting down" else "Starting up"
    val left = power.secondsLeft
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (line in lines) MonText(line, Color(0xFFBFE3FF), 11.5f)
        MonText("▌", Accent, 12f)
        Spacer(modifier = Modifier.height(8.dp))
        androidx.compose.material3.LinearProgressIndicator(
            progress = { power.percent / 100f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = Accent,
            trackColor = Color(0x33FFFFFF)
        )
        MonText(
            verb + " " + power.percent + "% · about " + (left / 60) + " min " + (left % 60).toString().padStart(2, '0') + " s left",
            ScreenDim,
            10.5f
        )
    }
}
