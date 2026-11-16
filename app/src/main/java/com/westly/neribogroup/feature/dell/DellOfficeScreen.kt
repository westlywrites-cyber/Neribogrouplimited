package com.westly.neribogroup.feature.dell

import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.neribogroup.R
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private val Desk = Color(0xFF1B1E20)
private val Accent = Color(0xFF7FD1C7)
private val ScreenDimText = Color(0xFF8A9498)

// The monitor is the same Dell picture the Storage room uses (room_bezel.webp, 462 x 388): 9 wide on the top and sides,
// 127 at the bottom (the chin and stand), all scaled by width / 462, so it fits the phone exactly like the Storage room.
private const val BEZEL_W = 462f
private const val BEZEL_EDGE = 9f
private const val BEZEL_FOOT = 127f

private const val ZOOM_MIN = 0.35f
private const val ZOOM_MAX = 1f
private const val ZOOM_START = 0.6f

// The Dell screen is shaped like a real company monitor (never taller than this many times its width).
private const val SCREEN_MAX_ASPECT = 0.85f

/** Sends one key press (down and up) into the app that is on the Dell screen, as a real keyboard would. */
private fun sendKey(view: View, code: Int, shift: Boolean) {
    if (!view.isFocused) view.requestFocus()
    val meta = if (shift) AndroidKeyEvent.META_SHIFT_ON or AndroidKeyEvent.META_SHIFT_LEFT_ON else 0
    val now = SystemClock.uptimeMillis()
    view.dispatchKeyEvent(
        AndroidKeyEvent(now, now, AndroidKeyEvent.ACTION_DOWN, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0)
    )
    view.dispatchKeyEvent(
        AndroidKeyEvent(now, now, AndroidKeyEvent.ACTION_UP, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0)
    )
}

/** One finger event, sent straight into the app on the Dell screen at ([x], [y]) in the window, exactly like a real touch. */
private fun sendTouch(view: View, action: Int, downTime: Long, eventTime: Long, x: Float, y: Float) {
    val props = arrayOf(MotionEvent.PointerProperties().apply {
        id = 0
        toolType = MotionEvent.TOOL_TYPE_FINGER
    })
    val coords = arrayOf(MotionEvent.PointerCoords().apply {
        this.x = x
        this.y = y
        pressure = 1f
        size = 1f
    })
    val event = MotionEvent.obtain(
        downTime, eventTime, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
    )
    view.dispatchTouchEvent(event)
    event.recycle()
}

/** A tap at ([x], [y]): what the Enter key does at the pointer. */
private fun sendTap(view: View, x: Float, y: Float) {
    // Posted, so it runs after the key press that asked for it is finished. A touch sent while another touch (the tap on
    // the Dell key) is still being handled is dropped by Compose.
    view.post {
        val t = SystemClock.uptimeMillis()
        sendTouch(view, MotionEvent.ACTION_DOWN, t, t, x, y)
        sendTouch(view, MotionEvent.ACTION_UP, t, t + 40L, x, y)
    }
}

/**
 * A slow swipe from ([x], [y]) that moves [distance] pixels up (negative) or down (positive), ending with a short rest
 * so the page stops where the swipe ends instead of flinging on.
 */
private fun sendSwipe(view: View, x: Float, y: Float, distance: Float) {
    // Posted for the same reason as sendTap.
    view.post {
        val t = SystemClock.uptimeMillis()
        val steps = 8
        sendTouch(view, MotionEvent.ACTION_DOWN, t, t, x, y)
        for (i in 1..steps) {
            sendTouch(view, MotionEvent.ACTION_MOVE, t, t + 16L * i, x, y + distance * i / steps)
        }
        sendTouch(view, MotionEvent.ACTION_UP, t, t + 16L * steps + 120L, x, y + distance)
    }
}

/** The mouse pointer on the Dell screen, drawn at [at] (in pixels, from the top left of the screen). */
@Composable
private fun DellPointer(at: Offset) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val u = density
        val path = Path().apply {
            moveTo(at.x, at.y)
            lineTo(at.x, at.y + 17f * u)
            lineTo(at.x + 4f * u, at.y + 13.2f * u)
            lineTo(at.x + 7f * u, at.y + 20f * u)
            lineTo(at.x + 9.6f * u, at.y + 18.8f * u)
            lineTo(at.x + 6.6f * u, at.y + 12.4f * u)
            lineTo(at.x + 12f * u, at.y + 12.4f * u)
            close()
        }
        drawPath(path, Color.White)
        drawPath(path, Color.Black, style = Stroke(width = 1.3f * u))
    }
}

/**
 * The CEO office: a real-looking Dell monitor on the desk with a keyboard in front of it. The Dell screen is a
 * Windows-style desktop with a wallpaper, a taskbar, a Start menu and a clock, and the whole app opens in a window on it.
 * The Dell has its own power: switching it off or on never touches the Storage room servers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DellOfficeScreen(
    shortcuts: List<DellShortcut>,
    iconTick: Int,
    /** True when the open window is back at its first screen, so there is nothing left to go back to. */
    windowAtRoot: Boolean,
    /** Gets the app ready for an icon that was opened (which section, app or social account). */
    onLaunch: (DellShortcut) -> Unit,
    /** The window was closed: forget what it had open. */
    onWindowClosed: () -> Unit,
    onExit: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    DellPower.load(context)

    var phase by remember { mutableStateOf(if (DellPower.isOff) DellPhase.OFF else DellPhase.ON) }
    var confirmOff by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableStateOf(ZOOM_START) }
    var showKeys by rememberSaveable { mutableStateOf(true) }
    var full by rememberSaveable { mutableStateOf(false) }
    var appOpen by rememberSaveable { mutableStateOf(false) }
    // The icon whose window is open (or minimised). Null means no window.
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    val nav = remember { DellDesktopNav() }
    val openShortcut = shortcuts.firstOrNull { it.key == openKey }
    var shift by remember { mutableStateOf(false) }
    var screenOrigin by remember { mutableStateOf(Offset.Zero) }
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    // The pointer, in pixels from the top left of the Dell screen. Negative means it has not been placed yet.
    var pointer by remember { mutableStateOf(Offset(-1f, -1f)) }
    val px = LocalDensity.current.density
    val progress = remember { Animatable(0f) }
    val fullNow = full && phase == DellPhase.ON

    val view = LocalView.current
    val backOwner = LocalOnBackPressedDispatcherOwner.current

    fun closeWindow() {
        appOpen = false
        openKey = null
        onWindowClosed()
    }

    fun openWindow(item: DellShortcut) {
        onLaunch(item)
        openKey = item.key
        appOpen = true
        nav.startOpen = false
    }

    // A window opened from an icon other than Neribo Group closes by itself when Back has taken it to the start of
    // that icon's own screens (for example after Back from the Staff list).
    LaunchedEffect(windowAtRoot, appOpen, openKey) {
        if (appOpen && openKey != null && openKey != DELL_HOME_KEY && windowAtRoot) closeWindow()
    }

    // Back closes full screen, then the app window, then leaves the office. Inside the app, Back works as usual
    // because the screens of the app register their own handlers after this one.
    BackHandler {
        if (fullNow) {
            full = false
        } else if (appOpen) {
            closeWindow()
        } else {
            onExit()
        }
    }

    // The Dell keyboard types, so the phone's own keyboard is kept away while the Dell is in front.
    val imeVisible = WindowInsets.isImeVisible
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(imeVisible) {
        if (imeVisible) keyboardController?.hide()
    }

    LaunchedEffect(phase) {
        if (phase == DellPhase.SHUTTING) {
            delay(3500L)
            phase = DellPhase.OFF
        } else if (phase == DellPhase.BOOTING) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(durationMillis = 90_000, easing = LinearEasing))
            phase = DellPhase.ON
        }
    }

    fun powerPressed() {
        if (phase == DellPhase.ON) {
            confirmOff = true
        } else if (phase == DellPhase.OFF) {
            DellPower.setOff(context, false)
            closeWindow()
            phase = DellPhase.BOOTING
        }
    }

    // Scrolls the page on the screen by about [amount] dp, down or up, with a slow swipe in the middle of the screen.
    fun scrollPage(down: Boolean) {
        if (screenSize.width == 0) return
        val x = screenOrigin.x + screenSize.width / 2f
        val y = screenOrigin.y + screenSize.height * 0.45f
        sendSwipe(view, x, y, (if (down) -110f else 110f) * px)
    }

    // The arrow keys move the pointer. At the top or bottom edge of the screen they scroll the page instead.
    fun movePointer(dx: Int, dy: Int) {
        if (screenSize.width == 0) return
        val w = screenSize.width.toFloat()
        val h = screenSize.height.toFloat()
        val step = 26f * px
        val edge = 10f * px
        val x = (pointer.x + dx * step).coerceIn(0f, w - 1f)
        var y = pointer.y + dy * step
        if (dy > 0 && y > h - edge) {
            scrollPage(true)
            y = h - edge
        } else if (dy < 0 && y < edge) {
            scrollPage(false)
            y = edge
        }
        pointer = Offset(x, y.coerceIn(0f, h - 1f))
    }

    val onKey: (DellKey) -> Unit = { key ->
        if (key.kind == DellKey.POWER) {
            powerPressed()
        } else if (phase == DellPhase.ON) {
            val code = key.code
            if (key.kind == DellKey.SHIFT) {
                shift = !shift
            } else if (!appOpen && nav.handleKey(code, shortcuts.size) { index -> shortcuts.getOrNull(index)?.let { openWindow(it) } }) {
                // On the desktop the keys work the icons and the Start menu, so the screen never has to be touched.
                shift = false
            } else if (code == AndroidKeyEvent.KEYCODE_ESCAPE) {
                backOwner?.onBackPressedDispatcher?.onBackPressed()
            } else if (code == AndroidKeyEvent.KEYCODE_DPAD_UP || code == AndroidKeyEvent.KEYCODE_DPAD_DOWN) {
                // Shift + Up or Down scrolls the page. Up or Down alone moves the pointer.
                if (shift) scrollPage(code == AndroidKeyEvent.KEYCODE_DPAD_DOWN)
                else movePointer(0, if (code == AndroidKeyEvent.KEYCODE_DPAD_DOWN) 1 else -1)
                shift = false
            } else if (code == AndroidKeyEvent.KEYCODE_DPAD_LEFT || code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT) {
                movePointer(if (code == AndroidKeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1, 0)
                shift = false
            } else if (code == AndroidKeyEvent.KEYCODE_ENTER && !shift) {
                // Enter clicks where the pointer is. Shift + Enter sends a real Enter key to the app instead.
                if (pointer.x >= 0f) sendTap(view, screenOrigin.x + pointer.x, screenOrigin.y + pointer.y)
            } else {
                sendKey(view, code, shift)
                shift = false
            }
        }
    }

    // Only the Dell monitor and its keyboard: no office picture, no desk. The monitor takes all the room that is left
    // above the keyboard, and the keyboard is docked at the bottom, as in the Storage room.
    Box(modifier = Modifier.fillMaxSize().background(Desk)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF15181A)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "‹ Leave",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { onExit() }.padding(horizontal = 8.dp, vertical = 8.dp)
                )
                Text(
                    text = "CEO office",
                    color = Color(0xFFD0D6D8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "−",
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable { zoom = ((Math.round(zoom * 20f) - 1) / 20f).coerceAtLeast(ZOOM_MIN) }
                        .padding(horizontal = 9.dp)
                )
                Text(
                    text = Math.round(zoom * 100f).toString() + "%",
                    color = Color(0xFFD0D6D8),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { zoom = ZOOM_START }.padding(vertical = 8.dp)
                )
                Text(
                    text = "+",
                    color = Color.White,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable { zoom = ((Math.round(zoom * 20f) + 1) / 20f).coerceAtMost(ZOOM_MAX) }
                        .padding(horizontal = 9.dp)
                )
                Text(
                    text = if (fullNow) "Exit full" else "Full",
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clickable { if (phase == DellPhase.ON) full = !full }
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
                if (!fullNow) {
                    Text(
                        text = if (showKeys) "Hide keys" else "Keys",
                        color = Accent,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { showKeys = !showKeys }.padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }

            DellMonitor(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 6.dp),
                phase = phase,
                bootProgress = progress.value,
                zoom = zoom,
                full = fullNow,
                pointer = if (phase == DellPhase.ON && appOpen && pointer.x >= 0f) pointer else null,
                onScreenPlaced = { origin, size ->
                    screenOrigin = origin
                    screenSize = size
                    if (pointer.x < 0f) pointer = Offset(size.width * 0.5f, size.height * 0.4f)
                }            ) {
                DellDesktop(
                    shortcuts = shortcuts,
                    nav = nav,
                    iconTick = iconTick,
                    appOpen = appOpen,
                    openShortcut = openShortcut,
                    onLaunch = { openWindow(it) },
                    onWindowShow = { appOpen = it },
                    onWindowClose = { closeWindow() },
                    onShutDown = { confirmOff = true },
                    content = content
                )
            }

            if (showKeys && !fullNow) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    DellKeyboard(
                        enabled = phase == DellPhase.ON,
                        shiftOn = shift,
                        onKey = onKey
                    )
                }
            }
        }
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Shut down the Dell?") },
            text = { Text("Only this Dell is switched off. The Storage room servers keep running.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmOff = false
                    DellPower.setOff(context, true)
                    closeWindow()
                    full = false
                    phase = DellPhase.SHUTTING
                }) { Text("Shut down") }
            },
            dismissButton = {
                TextButton(onClick = { confirmOff = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun DellMonitor(
    modifier: Modifier,
    phase: DellPhase,
    bootProgress: Float,
    zoom: Float,
    full: Boolean,
    pointer: Offset?,
    onScreenPlaced: (Offset, IntSize) -> Unit,
    desktop: @Composable () -> Unit
) {
    val bezel = ImageBitmap.imageResource(R.drawable.room_bezel)
    // Where the screen is in the window, so the pointer and the swipes land on the right place.
    val placed = Modifier.onGloballyPositioned { onScreenPlaced(it.positionInRoot(), it.size) }
    BoxWithConstraints(modifier = modifier) {
        if (full) {
            Box(modifier = Modifier.fillMaxSize().then(placed).background(Color.Black)) {
                DellScreenContent(phase, bootProgress, zoom, desktop)
                if (pointer != null) DellPointer(pointer)
            }
        } else {
            // The frame spans the phone width. The screen is shaped like a real company Dell: as tall as the room allows,
            // but never taller than SCREEN_MAX_ASPECT times its width. Anything left over is empty space around the monitor.
            val k = maxWidth.value / BEZEL_W
            val edge = (BEZEL_EDGE * k).dp
            val foot = (BEZEL_FOOT * k).dp
            val screenW = maxWidth - edge * 2
            val screenH = minOf(maxHeight - edge - foot, screenW * SCREEN_MAX_ASPECT).coerceAtLeast(60.dp)
            Box(
                modifier = Modifier
                    .size(maxWidth, edge + screenH + foot)
                    .align(Alignment.Center)
                    .drawBehind { drawBezel(bezel, BEZEL_EDGE * k * density, BEZEL_FOOT * k * density) }
                    .padding(start = edge, end = edge, top = edge, bottom = foot)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(placed)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black)
                ) {
                    DellScreenContent(phase, bootProgress, zoom, desktop)
                    if (pointer != null) DellPointer(pointer)
                }
            }
        }
    }
}

/** What the monitor shows for each power phase: the desktop, the shutting down message, the boot logo, or black. */
@Composable
private fun DellScreenContent(phase: DellPhase, bootProgress: Float, zoom: Float, desktop: @Composable () -> Unit) {
    when (phase) {
        DellPhase.ON -> ScaledDesktop(zoom, desktop)
        DellPhase.SHUTTING -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(modifier = Modifier.height(10.dp))
            Text("Shutting down…", color = Color.White, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text("The Storage room keeps running", color = ScreenDimText, fontSize = 11.sp)
        }
        DellPhase.BOOTING -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.dell_logo),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { bootProgress },
                modifier = Modifier.width(120.dp),
                color = Color.White,
                trackColor = Color(0xFF333A3D)
            )
        }
        DellPhase.OFF -> Box(modifier = Modifier.fillMaxSize())
    }
}

/** Draws the 8 border pieces of the monitor picture (5 left, 3 top, 3 right, 127 bottom); the middle is left empty for the screen. Same as the Storage room. */
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

/**
 * The desktop is laid out on a bigger "monitor" and shrunk to fit the picture of the screen, the way a monitor shows
 * more than a phone does; − and + above the monitor change how much fits. Touches still work.
 */
@Composable
private fun ScaledDesktop(zoom: Float, desktop: @Composable () -> Unit) {
    // The desktop is measured at a bigger size (screen size / zoom) and drawn shrunk from the top left corner, so it
    // always starts exactly at the top left of the screen and covers all of it.
    Layout(
        content = { desktop() },
        modifier = Modifier.fillMaxSize().clipToBounds()
    ) { measurables, constraints ->
        val w = (constraints.maxWidth / zoom).roundToInt()
        val h = (constraints.maxHeight / zoom).roundToInt()
        val placeables = measurables.map { it.measure(Constraints.fixed(w, h)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEach {
                it.placeWithLayer(0, 0) {
                    scaleX = zoom
                    scaleY = zoom
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}
