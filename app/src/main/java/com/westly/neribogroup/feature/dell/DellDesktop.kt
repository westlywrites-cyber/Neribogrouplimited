package com.westly.neribogroup.feature.dell

import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.neribogroup.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TaskbarHeight = 38.dp
private val TitleBarHeight = 28.dp
private val TaskbarColor = Color(0xE61B1E23)
private val MenuColor = Color(0xF2272B31)
private val WindowTitle = Color(0xFF20252A)
private val IconTeal = Color(0xFF7FD1C7)
private val WinBlue = Color(0xFF4CC2FF)

/**
 * Where the keyboard and the touch screen are on the desktop: which icon is picked, which desktop screen shows,
 * and the Start menu. [keys] is true once a keyboard key was used, so the picked icon is drawn with a frame
 * (and numbered 1 to 5); touching the screen hides the frame again.
 */
class DellDesktopNav {
    var selected by mutableStateOf(0)
    var page by mutableStateOf(0)
    var keys by mutableStateOf(false)
    var startOpen by mutableStateOf(false)
    var startIndex by mutableStateOf(0)

    /** True while the Wi-Fi list (opened from the taskbar) is open. */
    var wifiOpen by mutableStateOf(false)

    /** What each Start menu line does, in order. Filled in by [DellDesktop] every time it is drawn. */
    var menuActions: List<() -> Unit> = emptyList()

    fun select(index: Int) {
        selected = index
        page = index / DELL_PER_PAGE
    }
}

/**
 * The desktop keys, for when no window is open (the Start menu is handled here too).
 * Arrows pick an icon (Left and Right also change the desktop screen), Tab picks the next one, Enter opens it,
 * 1 to 5 open the icon with that number on this screen, Space opens or closes the Start menu.
 * Returns true when the key was used; Esc and every other key are left to the caller.
 */
fun DellDesktopNav.handleKey(code: Int, count: Int, openIndex: (Int) -> Unit): Boolean {
    keys = true
    if (startOpen) {
        val last = menuActions.size - 1
        when (code) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> startIndex = (startIndex - 1).coerceAtLeast(0)
            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> startIndex = (startIndex + 1).coerceAtMost(maxOf(0, last))
            AndroidKeyEvent.KEYCODE_ENTER -> {
                startOpen = false
                menuActions.getOrNull(startIndex)?.invoke()
            }
            AndroidKeyEvent.KEYCODE_ESCAPE, AndroidKeyEvent.KEYCODE_SPACE -> startOpen = false
        }
        return true
    }
    if (count <= 0) return false
    val last = count - 1
    val now = selected.coerceIn(0, last)
    val pageStart = (now / DELL_PER_PAGE) * DELL_PER_PAGE
    val pageEnd = minOf(pageStart + DELL_PER_PAGE, count) - 1
    val inPage = now - pageStart
    return when (code) {
        AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> { select(minOf(now + 1, last)); true }
        AndroidKeyEvent.KEYCODE_DPAD_LEFT -> { select(maxOf(now - 1, 0)); true }
        AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { if (inPage < 3) select(minOf(now + 3, pageEnd)); true }
        AndroidKeyEvent.KEYCODE_DPAD_UP -> { if (inPage >= 3) select(now - 3); true }
        AndroidKeyEvent.KEYCODE_TAB -> { select(if (now >= last) 0 else now + 1); true }
        AndroidKeyEvent.KEYCODE_ENTER -> { select(now); openIndex(now); true }
        AndroidKeyEvent.KEYCODE_SPACE -> { startOpen = true; startIndex = 0; true }
        AndroidKeyEvent.KEYCODE_1, AndroidKeyEvent.KEYCODE_2, AndroidKeyEvent.KEYCODE_3,
        AndroidKeyEvent.KEYCODE_4, AndroidKeyEvent.KEYCODE_5 -> {
            val index = pageStart + (code - AndroidKeyEvent.KEYCODE_1)
            if (index <= pageEnd) {
                select(index)
                openIndex(index)
            }
            true
        }
        else -> false
    }
}

/**
 * What is on the Dell screen: the wallpaper, the desktop (5 icons on each screen, with dots to show which screen),
 * a taskbar with a Start menu and a clock, and one window ([content]) for the icon that was opened.
 * [appOpen] says whether the window shows (otherwise the desktop shows). [openShortcut] is the icon the window belongs to.
 */
@Composable
fun DellDesktop(
    shortcuts: List<DellShortcut>,
    nav: DellDesktopNav,
    iconTick: Int,
    appOpen: Boolean,
    openShortcut: DellShortcut?,
    onLaunch: (DellShortcut) -> Unit,
    onWindowShow: (Boolean) -> Unit,
    onWindowClose: () -> Unit,
    onShutDown: () -> Unit,
    onScreenOff: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    DellWifi.load(context)
    val home = shortcuts.firstOrNull()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { DellWallpaper.saveFrom(context, uri) }
                if (ok) {
                    DellWallpaper.changed()
                } else {
                    Toast.makeText(context, "That picture could not be used", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val lockPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { DellLockPicture.saveFrom(context, uri) }
                if (ok) {
                    DellLockPicture.changed()
                } else {
                    Toast.makeText(context, "That picture could not be used", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // The Start menu: the lines and what they do. The keyboard (Up, Down, Enter) uses the same list.
    val hasCustomWallpaper = DellWallpaper.version >= 0 && DellWallpaper.hasCustom(context)
    val menu = ArrayList<Pair<String, () -> Unit>>()
    if (home != null) menu.add("Neribo Group" to { onLaunch(home) })
    menu.add("Change wallpaper" to { picker.launch("image/*") })
    if (hasCustomWallpaper) menu.add("Use the original wallpaper" to { DellWallpaper.reset(context) })
    val hasCustomLock = DellLockPicture.version >= 0 && DellLockPicture.hasCustom(context)
    menu.add("Change lock screen picture" to { lockPicker.launch("image/*") })
    if (hasCustomLock) menu.add("Use the original lock screen picture" to { DellLockPicture.reset(context) })
    menu.add("Turn off screen" to { onScreenOff() })
    menu.add("Shut down" to { onShutDown() })
    nav.menuActions = menu.map { it.second }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Wallpaper()

        if (!appOpen) {
            DesktopPages(shortcuts = shortcuts, nav = nav, iconTick = iconTick, onLaunch = onLaunch)
        }

        if (appOpen) {
            Column(modifier = Modifier.fillMaxSize().padding(bottom = TaskbarHeight)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(TitleBarHeight).background(WindowTitle),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    if (openShortcut != null) ShortcutIcon(openShortcut, 16, iconTick)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(openShortcut?.name ?: "Neribo Group", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    WindowButton("—") { onWindowShow(false) }
                    WindowButton("✕") { onWindowClose() }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .background(MaterialTheme.colorScheme.background)
                        // The window is not under the phone's status bar, nav bar or keyboard, so the app adds no gaps for them.
                        .consumeWindowInsets(WindowInsets.safeDrawing)
                ) {
                    // With no Wi-Fi connected, every window except OPay asks for an internet connection.
                    if (DellWifi.connected == null && openShortcut?.key != DELL_OPAY_KEY) {
                        DellOfflineNotice(onOpenWifi = {
                            nav.startOpen = false
                            nav.wifiOpen = true
                        })
                    } else {
                        content()
                    }
                }
            }
        }

        if (nav.startOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { nav.startOpen = false }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TaskbarHeight + 6.dp)
                    .width(250.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MenuColor)
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .padding(vertical = 6.dp)
            ) {
                for ((index, line) in menu.withIndex()) {
                    if (line.first == "Turn off screen") {
                        Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x33FFFFFF)))
                    }
                    MenuItem(line.first, selected = nav.keys && index == nav.startIndex) {
                        nav.startOpen = false
                        nav.keys = false
                        line.second()
                    }
                }
            }
        }

        if (nav.wifiOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { nav.wifiOpen = false }
            )
            DellWifiPanel(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 8.dp, bottom = TaskbarHeight + 6.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(TaskbarHeight)
                .background(TaskbarColor)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight())
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (nav.startOpen) Color(0x33FFFFFF) else Color.Transparent)
                    .clickable {
                        nav.keys = false
                        nav.wifiOpen = false
                        nav.startOpen = !nav.startOpen
                        nav.startIndex = 0
                    },
                contentAlignment = Alignment.Center
            ) {
                WindowsLogo()
            }
            Spacer(modifier = Modifier.width(4.dp))
            // The taskbar shows the icon of the open window; with no window open it shows Neribo Group.
            val barItem = openShortcut ?: home
            if (barItem != null) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (appOpen) Color(0x33FFFFFF) else Color.Transparent)
                        .clickable {
                            if (openShortcut == null) onLaunch(barItem) else onWindowShow(!appOpen)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    ShortcutIcon(barItem, 22, iconTick)
                    if (appOpen) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 1.dp)
                                .width(12.dp)
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(WinBlue)
                        )
                    }
                }
            }
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (nav.wifiOpen) Color(0x33FFFFFF) else Color.Transparent)
                            .clickable {
                                nav.keys = false
                                nav.startOpen = false
                                nav.wifiOpen = !nav.wifiOpen
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        DellWifiIcon(connected = DellWifi.connected != null, color = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Clock()
                }
            }
        }
    }
}

/** The desktop: 5 icons on a screen (3 and 2), swipe sideways or use the keys to change screen, dots show where you are. */
@Composable
private fun DesktopPages(
    shortcuts: List<DellShortcut>,
    nav: DellDesktopNav,
    iconTick: Int,
    onLaunch: (DellShortcut) -> Unit
) {
    val pages = maxOf(1, (shortcuts.size + DELL_PER_PAGE - 1) / DELL_PER_PAGE)
    val page = nav.page.coerceIn(0, pages - 1)
    val start = page * DELL_PER_PAGE
    val items = shortcuts.drop(start).take(DELL_PER_PAGE)

    fun goToPage(target: Int) {
        val clean = target.coerceIn(0, pages - 1)
        nav.page = clean
        nav.selected = clean * DELL_PER_PAGE
        nav.keys = false
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Big, easy icons: as wide as the room allows (3 across), but two rows must always fit above the dots.
        val iconSize = minOf(maxWidth.value / 3f * 0.6f, (maxHeight.value - TaskbarHeight.value - 40f) / 3.5f).coerceIn(40f, 104f)
        val labelSp = (iconSize * 0.2f).coerceIn(11f, 20f)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = TaskbarHeight + 22.dp)
                .pointerInput(pages, page) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -60f) goToPage(page + 1) else if (total > 60f) goToPage(page - 1)
                        },
                        onDragCancel = { total = 0f }
                    ) { _, amount -> total += amount }
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                for (i in 0 until 3) {
                    val item = items.getOrNull(i)
                    if (item != null) {
                        ShortcutCell(item, nav, start + i, i + 1, iconTick, iconSize, labelSp, onLaunch, Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
            if (items.size > 3) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
                    Spacer(modifier = Modifier.weight(0.5f))
                    for (i in 3 until 5) {
                        val item = items.getOrNull(i)
                        if (item != null) {
                            ShortcutCell(item, nav, start + i, i + 1, iconTick, iconSize, labelSp, onLaunch, Modifier.weight(1f))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.weight(0.5f))
                }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TaskbarHeight + 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (p in 0 until pages) {
                Box(modifier = Modifier.clickable { goToPage(p) }.padding(horizontal = 5.dp, vertical = 6.dp)) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (p == page) Color.White else Color(0x73FFFFFF))
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortcutCell(
    item: DellShortcut,
    nav: DellDesktopNav,
    index: Int,
    number: Int,
    iconTick: Int,
    iconSize: Float,
    labelSp: Float,
    onLaunch: (DellShortcut) -> Unit,
    modifier: Modifier
) {
    val picked = nav.keys && nav.selected == index
    val shape = RoundedCornerShape(10.dp)
    var cell = modifier
        .clip(shape)
        .background(if (picked) Color(0x40FFFFFF) else Color.Transparent)
    if (picked) cell = cell.border(1.5.dp, WinBlue, shape)
    Column(
        modifier = cell
            .clickable {
                nav.keys = false
                nav.select(index)
                onLaunch(item)
            }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            ShortcutIcon(item, iconSize.toInt(), iconTick)
            if (nav.keys) {
                Text(
                    text = number.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xB31B1E23))
                        .padding(horizontal = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.name,
            color = Color.White,
            fontSize = labelSp.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = (labelSp * 1.2f).sp,
            textAlign = TextAlign.Center
        )
    }
}

/** The picture of a desktop icon: the owner's own icon for an app he added, the "N" tile for Neribo Group, or the built-in picture. */
@Composable
private fun ShortcutIcon(item: DellShortcut, size: Int, tick: Int) {
    if (item.key == DELL_HOME_KEY) {
        AppIcon(size)
        return
    }
    val file = item.iconFile
    val custom by produceState<ImageBitmap?>(initialValue = null, file?.path, tick) {
        value = if (file == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { if (file.exists()) BitmapFactory.decodeFile(file.path)?.asImageBitmap() else null }.getOrNull()
            }
        }
    }
    val picture = custom
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size * 0.24f).dp))
        )
    } else {
        Image(
            painter = painterResource(item.icon),
            contentDescription = null,
            modifier = Modifier.size(size.dp)
        )
    }
}

@Composable
private fun Wallpaper() {
    val context = LocalContext.current
    val version = DellWallpaper.version
    val hasCustom = DellWallpaper.hasCustom(context)
    val custom by produceState<ImageBitmap?>(initialValue = null, version) {
        value = withContext(Dispatchers.IO) { DellWallpaper.loadCustom(context) }
    }
    val picture = custom
    if (picture != null) {
        Image(
            bitmap = picture,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    } else if (!hasCustom) {
        Image(
            painter = painterResource(R.drawable.dell_wallpaper),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun AppIcon(size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape((size / 4).dp))
            .background(Color(0xFF16302F))
            .border(1.dp, IconTeal, RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "N",
            color = IconTeal,
            fontWeight = FontWeight.Bold,
            fontSize = (size * 0.5f).sp
        )
    }
}

@Composable
private fun WindowsLogo() {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(modifier = Modifier.size(8.dp).background(WinBlue))
            Box(modifier = Modifier.size(8.dp).background(WinBlue))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(modifier = Modifier.size(8.dp).background(WinBlue))
            Box(modifier = Modifier.size(8.dp).background(WinBlue))
        }
    }
}

@Composable
private fun WindowButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.width(36.dp).fillMaxHeight().clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
private fun MenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0x33FFFFFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}

@Composable
private fun Clock() {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000L)
        }
    }
    val time = remember(now / 60_000L) { SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 60_000L) { SimpleDateFormat("d/M/yyyy", Locale.getDefault()).format(Date(now)) }
    Column(horizontalAlignment = Alignment.End) {
        Text(time, color = Color.White, fontSize = 11.sp, lineHeight = 13.sp)
        Text(date, color = Color(0xFFB8BEC2), fontSize = 10.sp, lineHeight = 12.sp)
    }
}
