package com.westly.neribogroup.feature.dell

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * The Wi-Fi of the Dell. There are two networks. A password is asked the first time a network is connected;
 * after that the Dell remembers it and one tap on Connect is enough. Only one network is connected at a time.
 * With no network connected, every window except OPay shows "Connect your internet connection".
 * This is only what the Dell shows: the phone's real internet is never touched.
 */
object DellWifi {
    class Network(val name: String, val password: String)

    val networks: List<Network> = listOf(
        Network("STARLINK", "4109@neribo"),
        Network("STARLINK 2", "4109@home")
    )

    private const val PREFS = "neribo_dell"
    private const val KEY_CONNECTED = "dell_wifi_connected"
    private const val KEY_SAVED = "dell_wifi_saved"

    /** The network that is connected now, or null. */
    var connected by mutableStateOf<String?>(null)
        private set

    private val saved = mutableStateListOf<String>()
    private var loaded = false

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        try {
            val p = prefs(context)
            val names = networks.map { it.name }
            saved.clear()
            saved.addAll((p.getStringSet(KEY_SAVED, emptySet()) ?: emptySet()).filter { it in names })
            connected = p.getString(KEY_CONNECTED, null)?.takeIf { it in saved }
        } catch (e: Exception) {
            connected = null
        }
    }

    fun isSaved(name: String): Boolean = name in saved

    /** Connects with a typed password. False when the password is wrong. */
    fun connectWith(context: Context, name: String, password: String): Boolean {
        val network = networks.firstOrNull { it.name == name } ?: return false
        if (password != network.password) return false
        if (name !in saved) saved.add(name)
        connected = name
        persist(context)
        return true
    }

    /** Connects a network that was connected before (its password is remembered). */
    fun connectSaved(context: Context, name: String) {
        if (name !in saved) return
        connected = name
        persist(context)
    }

    fun disconnect(context: Context) {
        connected = null
        persist(context)
    }

    /** Forgets a network: its password is asked again next time. */
    fun forget(context: Context, name: String) {
        saved.remove(name)
        if (connected == name) connected = null
        persist(context)
    }

    private fun persist(context: Context) {
        try {
            prefs(context).edit()
                .putString(KEY_CONNECTED, connected)
                .putStringSet(KEY_SAVED, HashSet(saved))
                .apply()
        } catch (e: Exception) {
            // It still works until the app is closed.
        }
    }
}

private val WifiPanelColor = Color(0xF2272B31)
private val WifiBlue = Color(0xFF4CC2FF)
private val WifiGreen = Color(0xFF6CCB5F)

/** The Wi-Fi symbol: three bars and a dot. Crossed out when [connected] is false. */
@Composable
fun DellWifiIcon(connected: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.82f
        val stroke = Stroke(width = w * 0.1f, cap = StrokeCap.Round)
        for (radius in listOf(0.28f, 0.52f, 0.76f)) {
            val r = w * radius
            drawArc(
                color = color,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2f, r * 2f),
                style = stroke
            )
        }
        drawCircle(color = color, radius = w * 0.07f, center = Offset(cx, cy))
        if (!connected) {
            drawLine(
                color = color,
                start = Offset(w * 0.12f, h * 0.12f),
                end = Offset(w * 0.88f, h * 0.9f),
                strokeWidth = w * 0.1f,
                cap = StrokeCap.Round
            )
        }
    }
}

/** What a window shows instead of its app while no Wi-Fi is connected (OPay never shows it). */
@Composable
fun DellOfflineNotice(onOpenWifi: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF14181C)).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DellWifiIcon(connected = false, color = WifiBlue, modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Connect your internet connection",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(WifiBlue)
                .clickable { onOpenWifi() }
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text("Open Wi-Fi", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The Wi-Fi list that opens from the taskbar. */
@Composable
fun DellWifiPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    DellWifi.load(context)
    var expanded by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(250.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(WifiPanelColor)
            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text("Wi-Fi", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        for (network in DellWifi.networks) {
            val name = network.name
            val isConnected = DellWifi.connected == name
            val isSaved = DellWifi.isSaved(name)
            val focus = remember { FocusRequester() }
            val asking = expanded == name && !isSaved

            LaunchedEffect(asking) {
                if (asking) {
                    delay(80L)
                    runCatching { focus.requestFocus() }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                DellWifiIcon(connected = true, color = if (isConnected) WifiBlue else Color.White, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontSize = 14.sp)
                    Text(
                        text = if (isConnected) "Connected" else if (isSaved) "Saved" else "Secured",
                        color = if (isConnected) WifiGreen else Color(0xFFB8BEC2),
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isConnected) {
                    WifiButton("Disconnect", primary = false) {
                        DellWifi.disconnect(context)
                    }
                } else if (isSaved) {
                    WifiButton("Connect", primary = true) {
                        DellWifi.connectSaved(context, name)
                    }
                    WifiButton("Forget", primary = false) {
                        DellWifi.forget(context, name)
                        if (expanded == name) expanded = null
                    }
                } else if (!asking) {
                    WifiButton("Connect", primary = true) {
                        expanded = name
                        password = ""
                        wrong = false
                        reveal = false
                    }
                }
            }
            if (asking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF14181C))
                        .border(1.dp, if (wrong) Color(0xFFFF8A80) else WifiBlue, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            wrong = false
                        },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f).focusRequester(focus)
                    )
                    Text(
                        text = if (reveal) "Hide" else "Show",
                        color = WifiBlue,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { reveal = !reveal }.padding(start = 8.dp)
                    )
                }
                if (wrong) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Can't connect", color = Color(0xFFFF8A80), fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WifiButton("Cancel", primary = false) {
                        expanded = null
                        password = ""
                        wrong = false
                    }
                    WifiButton("Connect", primary = true) {
                        if (DellWifi.connectWith(context, name, password)) {
                            expanded = null
                            password = ""
                            wrong = false
                        } else {
                            wrong = true
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun WifiButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (primary) WifiBlue else Color(0x33FFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (primary) Color.Black else Color.White, fontSize = 12.sp)
    }
}
