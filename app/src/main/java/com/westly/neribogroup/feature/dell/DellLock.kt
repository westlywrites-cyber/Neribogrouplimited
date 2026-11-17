package com.westly.neribogroup.feature.dell

import android.content.Context
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.westly.neribogroup.feature.common.LocalBiometricGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The lock of the Dell: the PIN, when the screen turns off by itself and when the Dell locks.
 * The PIN is kept on this phone only, as a salted hash (never the PIN itself).
 * The screen turns off after 3 minutes without a touch or key, and the Dell locks after 5 minutes.
 * It also locks every time the Dell is powered off and on, and when NCO is opened fresh.
 */
object DellLock {
    private const val PREFS = "neribo_dell"
    private const val KEY_HASH = "dell_pin_hash"
    private const val KEY_SALT = "dell_pin_salt"

    const val SCREEN_OFF_MS = 3 * 60 * 1000L
    const val LOCK_MS = 5 * 60 * 1000L
    const val PIN_MIN = 4
    const val PIN_MAX = 6

    /** True while the lock screen (or the "Create a PIN" screen) is in front. */
    var locked by mutableStateOf(true)
        private set

    /** True while only the screen is off (the Dell is still on). */
    var screenOff by mutableStateOf(false)
        private set

    /** True once a PIN has been created on this phone. */
    var pinSet by mutableStateOf(false)
        private set

    /** True after "I forgot my PIN" (verified with the phone fingerprint or screen lock): a new PIN must be created. */
    var resetting by mutableStateOf(false)
        private set

    /** The digits typed so far on the lock screen. */
    var entry by mutableStateOf("")
        private set

    /** The first PIN, while the second one (to confirm it) is being typed. */
    var firstPin by mutableStateOf<String?>(null)
        private set

    var message by mutableStateOf("")
        private set

    private var loaded = false
    private var lastActive = SystemClock.elapsedRealtime()
    private var wokeAt = 0L
    private var wrong = 0
    private var blockedUntil = 0L

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        pinSet = try {
            prefs(context).getString(KEY_HASH, null) != null
        } catch (e: Exception) {
            false
        }
    }

    /** Any touch or key press on the Dell: it counts as activity, and it wakes a screen that was off. */
    fun touch() {
        val now = SystemClock.elapsedRealtime()
        lastActive = now
        if (screenOff) {
            screenOff = false
            wokeAt = now
        }
    }

    /** True for a moment after a touch woke the screen: the key that did it must not also type or click. */
    fun recentlyWoke(): Boolean = SystemClock.elapsedRealtime() - wokeAt < 600L

    /** Called every second while the Dell is on. */
    fun tick() {
        val idle = SystemClock.elapsedRealtime() - lastActive
        if (idle >= SCREEN_OFF_MS && !screenOff) screenOff = true
        if (idle >= LOCK_MS && !locked) lock()
    }

    fun turnOffScreen() {
        screenOff = true
    }

    /** Locks the Dell: the PIN is asked again. */
    fun lock() {
        locked = true
        entry = ""
        firstPin = null
        message = ""
    }

    /** The Dell was powered off (or is starting): it will be locked when it is on, and its screen is on. */
    fun powerCycled() {
        lock()
        screenOff = false
        resetting = false
    }

    private fun unlock() {
        locked = false
        lastActive = SystemClock.elapsedRealtime()
    }

    /** The owner was verified with the phone fingerprint or screen lock: let him create a new PIN. */
    fun startReset() {
        resetting = true
        entry = ""
        firstPin = null
        message = ""
    }

    fun cancelReset() {
        resetting = false
        entry = ""
        firstPin = null
        message = ""
    }

    /** A key of the Dell keyboard while the lock screen is in front. */
    fun onKey(context: Context, code: Int) {
        load(context)
        if (code >= AndroidKeyEvent.KEYCODE_0 && code <= AndroidKeyEvent.KEYCODE_9) {
            if (entry.length < PIN_MAX) {
                entry += (code - AndroidKeyEvent.KEYCODE_0).toString()
                message = ""
            }
        } else if (code == AndroidKeyEvent.KEYCODE_DEL) {
            entry = entry.dropLast(1)
        } else if (code == AndroidKeyEvent.KEYCODE_ENTER) {
            submit(context)
        }
    }

    fun submit(context: Context) {
        load(context)
        val now = SystemClock.elapsedRealtime()
        if (!pinSet || resetting) {
            if (entry.length < PIN_MIN) {
                message = "Use 4 to 6 digits"
                return
            }
            val first = firstPin
            if (first == null) {
                firstPin = entry
                entry = ""
                message = ""
            } else if (first == entry) {
                savePin(context, entry)
                pinSet = true
                resetting = false
                firstPin = null
                entry = ""
                message = ""
                wrong = 0
                unlock()
            } else {
                firstPin = null
                entry = ""
                message = "The two PINs were not the same. Try again."
            }
            return
        }
        if (now < blockedUntil) {
            entry = ""
            message = "Too many tries. Please wait."
            return
        }
        if (checkPin(context, entry)) {
            wrong = 0
            entry = ""
            message = ""
            unlock()
        } else {
            wrong += 1
            entry = ""
            if (wrong >= 5) {
                wrong = 0
                blockedUntil = now + 30_000L
                message = "Too many tries. Wait 30 seconds."
            } else {
                message = "Wrong PIN"
            }
        }
    }

    private fun hash(salt: String, pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest((salt + ":" + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun savePin(context: Context, pin: String) {
        try {
            val salt = newSalt()
            prefs(context).edit().putString(KEY_SALT, salt).putString(KEY_HASH, hash(salt, pin)).apply()
        } catch (e: Exception) {
            // The PIN still works until the app is closed.
        }
    }

    private fun checkPin(context: Context, pin: String): Boolean {
        return try {
            val p = prefs(context)
            val salt = p.getString(KEY_SALT, null) ?: return false
            val stored = p.getString(KEY_HASH, null) ?: return false
            hash(salt, pin) == stored
        } catch (e: Exception) {
            false
        }
    }
}

/** The lock screen: the picture, then either "Create a PIN" or the time, the date and the PIN box. Typed with the Dell keyboard. */
@Composable
fun DellLockScreen() {
    val context = LocalContext.current
    DellLock.load(context)
    val creating = !DellLock.pinSet || DellLock.resetting
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Nothing under the lock screen can be touched.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
    ) {
        LockPicture()
        Box(modifier = Modifier.fillMaxSize().background(Color(0x40000000)))
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (creating) LockCreate() else LockEnter()
        }
    }
}

@Composable
private fun LockPicture() {
    val context = LocalContext.current
    val version = DellLockPicture.version
    val hasCustom = DellLockPicture.hasCustom(context)
    val custom by produceState<ImageBitmap?>(initialValue = null, version) {
        value = withContext(Dispatchers.IO) { DellLockPicture.loadCustom(context) }
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
            painter = painterResource(R.drawable.dell_lock),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun LockEnter() {
    val context = LocalContext.current
    val gate = LocalBiometricGate.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000L)
        }
    }
    val time = remember(now / 60_000L) { SimpleDateFormat("h:mm", Locale.getDefault()).format(Date(now)) }
    val date = remember(now / 60_000L) { SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(now)) }
    Text(time, color = Color.White, fontSize = 54.sp, fontWeight = FontWeight.Light)
    Text(date, color = Color.White, fontSize = 16.sp)
    Spacer(modifier = Modifier.height(26.dp))
    Text("Neribo Group", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PinField(DellLock.entry.length)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xCC15181A))
                .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))
                .clickable { DellLock.submit(context) },
            contentAlignment = Alignment.Center
        ) {
            Text("→", color = Color.White, fontSize = 18.sp)
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    LockMessage("Type your PIN with the keyboard")
    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = "I forgot my PIN",
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .clickable {
                gate.verify(
                    title = "Dell PIN",
                    subtitle = "Verify it is you to create a new Dell PIN.",
                    logText = "Dell PIN reset",
                    always = true,
                    onSuccess = { DellLock.startReset() }
                )
            }
            .padding(8.dp)
    )
}

@Composable
private fun LockCreate() {
    val context = LocalContext.current
    val confirming = DellLock.firstPin != null
    Text(
        text = if (confirming) "Confirm your PIN" else "Create a PIN",
        color = Color.White,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text("4 to 6 digits, typed with the keyboard", color = Color(0xFFE0E0E0), fontSize = 12.sp)
    Spacer(modifier = Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val slots = maxOf(DellLock.PIN_MIN, DellLock.entry.length)
        for (i in 0 until slots) {
            Box(
                modifier = Modifier
                    .size(34.dp, 40.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xCC15181A))
                    .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (i < DellLock.entry.length) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    LockMessage("")
    Spacer(modifier = Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (DellLock.resetting) {
            LockButton("Cancel") { DellLock.cancelReset() }
        }
        LockButton(if (confirming) "Save ›" else "Next ›") { DellLock.submit(context) }
    }
}

@Composable
private fun PinField(count: Int) {
    Row(
        modifier = Modifier
            .width(190.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xCC15181A))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (count == 0) Text("PIN", color = Color(0xFF9AA3A7), fontSize = 14.sp)
        repeat(count) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.White))
        }
    }
}

/** The line under the PIN: the problem in red, or [hint] when there is none. */
@Composable
private fun LockMessage(hint: String) {
    val message = DellLock.message
    if (message.isNotEmpty()) {
        Text(message, color = Color(0xFFFF8A80), fontSize = 12.sp, textAlign = TextAlign.Center)
    } else if (hint.isNotEmpty()) {
        Text(hint, color = Color(0xFFE0E0E0), fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LockButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC15181A))
            .border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(6.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp)
    }
}
