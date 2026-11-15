package com.westly.neribogroup.feature.dell

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 * What is on the Dell screen: the wallpaper, a desktop icon, a taskbar with a Start menu and a clock, and the whole
 * Neribo Group app in a window ([content]). [appOpen] says whether the window is open (otherwise the desktop shows).
 */
@Composable
fun DellDesktop(
    appOpen: Boolean,
    onAppOpen: (Boolean) -> Unit,
    onShutDown: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var startOpen by remember { mutableStateOf(false) }

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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Wallpaper()

        if (!appOpen) {
            Column(
                modifier = Modifier
                    .padding(start = 10.dp, top = 10.dp)
                    .width(72.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onAppOpen(true) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppIcon(size = 40)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Neribo Group",
                    color = Color.White,
                    fontSize = 11.sp,
                    maxLines = 2,
                    lineHeight = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (appOpen) {
            Column(modifier = Modifier.fillMaxSize().padding(bottom = TaskbarHeight)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(TitleBarHeight).background(WindowTitle),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    AppIcon(size = 16)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Neribo Group", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    WindowButton("—") { onAppOpen(false) }
                    WindowButton("✕") { onAppOpen(false) }
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
                    content()
                }
            }
        }

        if (startOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { startOpen = false }
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = TaskbarHeight + 6.dp)
                    .width(210.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MenuColor)
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                    .padding(vertical = 6.dp)
            ) {
                MenuItem("Neribo Group") {
                    startOpen = false
                    onAppOpen(true)
                }
                MenuItem("Change wallpaper") {
                    startOpen = false
                    picker.launch("image/*")
                }
                if (DellWallpaper.version >= 0 && DellWallpaper.hasCustom(context)) {
                    MenuItem("Use the original wallpaper") {
                        startOpen = false
                        DellWallpaper.reset(context)
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x33FFFFFF)))
                MenuItem("Shut down") {
                    startOpen = false
                    onShutDown()
                }
            }
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
                    .background(if (startOpen) Color(0x33FFFFFF) else Color.Transparent)
                    .clickable { startOpen = !startOpen },
                contentAlignment = Alignment.Center
            ) {
                WindowsLogo()
            }
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (appOpen) Color(0x33FFFFFF) else Color.Transparent)
                    .clickable { onAppOpen(!appOpen) },
                contentAlignment = Alignment.Center
            ) {
                AppIcon(size = 22)
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
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                Clock()
            }
        }
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
private fun MenuItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 14.dp, vertical = 9.dp)
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
