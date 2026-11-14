package com.westly.neribogroup.feature.storageroom

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.StorageRoomRules
import com.westly.neribogroup.data.StorageRules
import com.westly.neribogroup.data.StorageUnit

/** What the owner is typing on the monitor. [row] is the plan row (Free, Pro, Premium) for plan edits. */
data class RoomEdit(
    val kind: RoomEditKind,
    val appId: String,
    val row: Int,
    val buf: String,
    val unit: StorageUnit
)

enum class RoomEditKind { TOTAL, SHARE, SIZE, USERS }

/** The PIN question shown over the monitor when a change lowers a limit. [redo] runs the change once the PIN is right. */
data class RoomPin(
    val title: String,
    val buf: String,
    val message: String,
    val redo: (Ledger) -> Unit
)

/** The line under the monitor: [bad] is true for a refusal. */
data class RoomMessage(val text: String, val bad: Boolean)

/** What the room can do. Every function is the same one the Storage page and the app dashboard use. */
class RoomActions(
    val setTotal: (Long, Boolean) -> Unit,
    val setShare: (String, Long, Boolean) -> Unit,
    val setOffer: (String, Long, Long, Long, Boolean) -> Unit,
    val setUsers: (String, Long, Long, Long) -> Unit,
    val backUpOffsite: () -> Unit
)

/** Counts wrong PINs for the whole run of the app, so leaving and coming back does not give new tries. */
object RoomPinGuard {
    private const val MAX_TRIES = 5
    private const val LOCK_MILLIS = 30_000L
    private var failures = 0
    private var lockedUntil = 0L

    /** Seconds left until the PIN can be tried again (0 = it can be tried now). */
    fun waitSeconds(): Long {
        val left = lockedUntil - SystemClock.elapsedRealtime()
        return if (left > 0L) (left + 999L) / 1000L else 0L
    }

    /** Records a wrong PIN. True when this one locked the PIN for a short time. */
    fun failure(): Boolean {
        failures += 1
        if (failures >= MAX_TRIES) {
            failures = 0
            lockedUntil = SystemClock.elapsedRealtime() + LOCK_MILLIS
            return true
        }
        return false
    }

    fun success() {
        failures = 0
        lockedUntil = 0L
    }
}

/**
 * The monitor and its keyboard: which page is open, which line is picked, what is being typed and what each key
 * does. It follows the preview (O A P C L and F1 to F5, arrows, Enter, Esc, Tab on Plans, digits and "." and
 * M, G, T). Every change is checked by [StorageRoomRules] first and done by [RoomActions].
 */
class RoomController(
    initial: Ledger,
    private val power: () -> PowerView = { PowerView.ON },
    /** Called with true to switch the server on and false to turn it off. Local to this phone only. */
    private val onPower: (Boolean) -> Unit = {},
    private val actions: () -> RoomActions
) {

    var page by mutableStateOf(OVERVIEW)
        private set
    var sel by mutableStateOf(0)
        private set
    var appIndex by mutableStateOf(0)
        private set
    var col by mutableStateOf(0)
        private set
    var edit by mutableStateOf<RoomEdit?>(null)
        private set
    var pin by mutableStateOf<RoomPin?>(null)
        private set
    var message by mutableStateOf<RoomMessage?>(null)
        private set

    /** True while the "Turn off the server?" warning is on the monitor. */
    var warn by mutableStateOf(false)
        private set

    private var current: Ledger = initial

    /** Called on every redraw, so a check always uses the newest numbers. */
    fun update(ledger: Ledger) {
        current = ledger
    }

    fun clearMessage() {
        message = null
    }

    private fun apps(): List<AppData> = StorageRoomRules.roomApps(current.apps)

    /** The app shown on the Plans page. */
    fun shownAppIndex(): Int {
        val count = apps().size
        return if (count == 0) 0 else appIndex.coerceIn(0, count - 1)
    }

    // ---- Which keys work right now ----

    fun powerView(): PowerView = power()

    fun isLit(k: String): Boolean {
        val phase = power().phase
        if (k == "PWR") return (phase == PowerPhase.ON && pin == null && edit == null && !warn) || phase == PowerPhase.OFF
        if (phase != PowerPhase.ON) return false
        if (warn) return k == "Escape" || k == "Enter"
        val digit = k.length == 1 && k[0] in '0'..'9'
        if (pin != null) return digit || k == "Backspace" || k == "Escape" || k == "Enter"
        val e = edit
        if (e != null) {
            return digit || k == "Backspace" || k == "Enter" || k == "Escape" ||
                (e.kind != RoomEditKind.USERS && (k == "." || k == "m" || k == "g" || k == "t"))
        }
        if (k.length == 1 && "oapcl".contains(k)) return true
        if (k.startsWith("Arrow") || k == "Enter" || k == "Escape") return true
        if (k.length == 2 && k[0] == 'F' && k[1] in '1'..'5') return true
        return k == "Tab" && page == PLANS
    }

    fun hint(): String {
        when (power().phase) {
            PowerPhase.OFF -> return "The server is off. Press PWR to switch it on."
            PowerPhase.SHUTTING -> return "The server is shutting down. Please wait."
            PowerPhase.BOOTING -> return "The server is starting up. Please wait."
            else -> Unit
        }
        if (warn) return "Enter continues. Esc cancels."
        val e = edit
        if (e != null) {
            return if (e.kind == RoomEditKind.USERS) {
                "Type how many users, then press Enter to save. Esc cancels."
            } else {
                "Type the number. Press M for MB, G for GB or T for TB. Enter saves, Esc cancels."
            }
        }
        return when (page) {
            OVERVIEW -> "This is the whole company. Press A for apps, P for plans, C for copies."
            APPS -> "Press ↓ or ↑ to pick a line, then press Enter to change it."
            PLANS -> "↓ ↑ pick Free, Pro, Premium or Custom. ← → pick size or users. Tab for the next app. Enter to change."
            COPIES -> "↓ ↑ pick a copy. On Off-site, press Enter to make a copy now."
            else -> "Every change is listed here, newest first."
        }
    }

    // ---- Keys ----

    fun press(k: String) {
        val phase = power().phase
        if (k == "PWR") {
            if (phase == PowerPhase.OFF) {
                onPower(true)
            } else if (phase == PowerPhase.ON && pin == null && edit == null && !warn) {
                warn = true
                message = null
            }
            return
        }
        if (phase != PowerPhase.ON) return
        if (warn) {
            if (k == "Escape") {
                warn = false
            } else if (k == "Enter") {
                warn = false
                askPin("Turn off the server") { onPower(false) }
            }
            return
        }
        if (pin != null) {
            pinKey(k)
            return
        }
        if (edit != null) {
            editKey(k)
            return
        }
        navKey(k)
    }

    /** A tap on a tab of the monitor, like F1 to F5. */
    fun tapTab(p: Int) {
        if (pin != null || edit != null || warn || power().phase != PowerPhase.ON) return
        go(p)
    }

    /** A tap on a line of the monitor: the first tap picks it, a tap on the picked line changes it. */
    fun tapRow(index: Int, column: Int) {
        if (pin != null || edit != null || warn || power().phase != PowerPhase.ON) return
        if (page == PLANS) {
            if (sel == index && col == column) enter() else {
                sel = index
                col = column
            }
        } else {
            if (sel == index) enter() else sel = index
        }
    }

    private fun go(p: Int) {
        page = p
        sel = 0
    }

    private fun maxSel(): Int = when (page) {
        APPS -> apps().size
        PLANS -> 3
        COPIES -> 2
        else -> 0
    }

    private fun navKey(k: String) {
        when {
            k.length == 1 && "oapcl".contains(k) -> go("oapcl".indexOf(k) + 1)
            k.length == 2 && k[0] == 'F' && k[1] in '1'..'5' -> go(k[1] - '0')
            k == "Escape" -> go(OVERVIEW)
            k == "ArrowDown" -> sel = minOf(sel + 1, maxSel())
            k == "ArrowUp" -> sel = maxOf(0, sel - 1)
            k == "ArrowLeft" || k == "ArrowRight" -> {
                if (page == PLANS) {
                    col = if (col == 0) 1 else 0
                } else {
                    go(if (k == "ArrowRight") page % 5 + 1 else (page + 3) % 5 + 1)
                }
            }
            k == "Tab" -> {
                if (page == PLANS) {
                    val count = apps().size
                    appIndex = if (count == 0) 0 else (shownAppIndex() + 1) % count
                }
            }
            k == "Enter" -> enter()
        }
    }

    private fun enter() {
        val list = apps()
        when (page) {
            APPS -> {
                if (sel == 0) {
                    startEdit(RoomEditKind.TOTAL, "", 0, current.assets.storageTotalMb)
                } else {
                    val app = list.getOrNull(sel - 1)
                    if (app != null) startEdit(RoomEditKind.SHARE, app.id, 0, app.storageShareMb)
                }
            }
            PLANS -> {
                val app = list.getOrNull(shownAppIndex())
                if (app != null) enterPlan(app)
            }
            COPIES -> if (sel == 2) makeOffsite()
        }
    }

    private fun enterPlan(app: AppData) {
        val row = sel
        if (col == 1) {
            if (StorageRoomRules.usersEditable(app, row)) {
                startEdit(RoomEditKind.USERS, app.id, row, StorageRoomRules.planUsers(app, row))
            } else if (row == StorageRoomRules.CUSTOM) {
                err("Custom storage is set for each user in the app.")
            } else {
                err(app.name + "'s users come from its user list. Add or remove them in the app.")
            }
        } else {
            if (StorageRoomRules.sizeEditable(row)) {
                startEdit(RoomEditKind.SIZE, app.id, row, StorageRoomRules.planSizeMb(app, row))
            } else {
                err("Custom storage is set for each user in the app.")
            }
        }
    }

    private fun startEdit(kind: RoomEditKind, appId: String, row: Int, currentValue: Long) {
        val unit = if (currentValue <= 0L) StorageUnit.GB else StorageRules.bestUnit(currentValue)
        edit = RoomEdit(kind, appId, row, "", unit)
        message = null
    }

    private fun editKey(k: String) {
        val e = edit ?: return
        when {
            k == "Escape" -> edit = null
            k == "Enter" -> commit()
            k == "Backspace" -> edit = e.copy(buf = e.buf.dropLast(1))
            e.kind != RoomEditKind.USERS && (k == "m" || k == "g" || k == "t") -> {
                val unit = when (k) {
                    "m" -> StorageUnit.MB
                    "g" -> StorageUnit.GB
                    else -> StorageUnit.TB
                }
                edit = e.copy(unit = unit)
            }
            k.length == 1 && k[0] in '0'..'9' -> {
                if (e.buf.length < MAX_TYPED_CHARS) edit = e.copy(buf = e.buf + k)
            }
            k == "." && e.kind != RoomEditKind.USERS -> {
                if (!e.buf.contains('.') && e.buf.length < MAX_TYPED_CHARS) edit = e.copy(buf = e.buf + ".")
            }
        }
    }

    // ---- Saving a typed value ----

    private fun commit() {
        val e = edit ?: return
        edit = null
        if (e.buf.isEmpty() || e.buf == ".") return
        when (e.kind) {
            RoomEditKind.TOTAL -> {
                val mb = StorageRoomRules.typedToMb(e.buf, e.unit) ?: return
                runTotal(current, mb, false)
            }
            RoomEditKind.SHARE -> {
                val mb = StorageRoomRules.typedToMb(e.buf, e.unit) ?: return
                runShare(current, e.appId, mb, false)
            }
            RoomEditKind.SIZE -> {
                val mb = StorageRoomRules.typedToMb(e.buf, e.unit) ?: return
                runSize(current, e.appId, e.row, mb, false)
            }
            RoomEditKind.USERS -> {
                val n = StorageRoomRules.typedToCount(e.buf) ?: return
                runUsers(current, e.appId, e.row, n)
            }
        }
    }

    private fun runTotal(l: Ledger, mb: Long, pinOk: Boolean) {
        val verdict = StorageRoomRules.checkTotal(l.assets, l.apps, mb)
        val refused = verdict.refused
        if (refused != null) {
            err(refused)
            return
        }
        if (verdict.needsPin && !pinOk) {
            askPin("Lower company storage") { fresh -> runTotal(fresh, mb, true) }
            return
        }
        actions().setTotal(mb, verdict.needsPin)
        ok("Company storage: " + StorageRules.textMb(mb))
    }

    private fun runShare(l: Ledger, appId: String, mb: Long, pinOk: Boolean) {
        val app = StorageRoomRules.roomApps(l.apps).firstOrNull { it.id == appId } ?: return
        val verdict = StorageRoomRules.checkShare(l.assets, l.apps, app, mb)
        val refused = verdict.refused
        if (refused != null) {
            err(refused)
            return
        }
        if (verdict.needsPin && !pinOk) {
            askPin("Lower " + app.name + "'s share") { fresh -> runShare(fresh, appId, mb, true) }
            return
        }
        actions().setShare(app.id, mb, verdict.needsPin)
        ok(app.name + " share: " + StorageRules.textMb(mb))
    }

    private fun runSize(l: Ledger, appId: String, row: Int, mb: Long, pinOk: Boolean) {
        val app = StorageRoomRules.roomApps(l.apps).firstOrNull { it.id == appId } ?: return
        val verdict = StorageRoomRules.checkSize(l.assets, l.apps, app, row, mb)
        val refused = verdict.refused
        if (refused != null) {
            err(refused)
            return
        }
        if (verdict.needsPin && !pinOk) {
            askPin("Lower " + app.name + " " + StorageRoomRules.PLAN_NAMES[row]) { fresh ->
                runSize(fresh, appId, row, mb, true)
            }
            return
        }
        val updated = StorageRoomRules.withSize(app, row, mb)
        actions().setOffer(
            app.id, updated.storageFreeMb, updated.storageProMb, updated.storagePremiumMb, verdict.needsPin
        )
        ok(app.name + " " + StorageRoomRules.PLAN_NAMES[row] + " gets " + StorageRules.textMb(mb) + " each")
    }

    private fun runUsers(l: Ledger, appId: String, row: Int, count: Long) {
        val app = StorageRoomRules.roomApps(l.apps).firstOrNull { it.id == appId } ?: return
        val verdict = StorageRoomRules.checkUsers(l.assets, l.apps, app, row, count)
        val refused = verdict.refused
        if (refused != null) {
            err(refused)
            return
        }
        val updated = StorageRoomRules.withUsers(app, row, count)
        actions().setUsers(app.id, updated.freeUsers, updated.proUsers, updated.premiumUsers)
        ok(app.name + " " + StorageRoomRules.PLAN_NAMES[row] + " users: " + count)
    }

    private fun makeOffsite() {
        val problem = StorageRoomRules.offsiteRefusal(current.assets, current.apps)
        if (problem != null) {
            err(problem)
            return
        }
        actions().backUpOffsite()
        ok("Off-site copy made")
    }

    // ---- The PIN question ----

    private fun askPin(title: String, redo: (Ledger) -> Unit) {
        pin = RoomPin(title, "", "", redo)
        message = null
    }

    private fun pinKey(k: String) {
        val p = pin ?: return
        when {
            k == "Escape" -> {
                pin = null
                message = RoomMessage("Cancelled. Nothing was changed.", false)
            }
            k == "Backspace" -> pin = p.copy(buf = p.buf.dropLast(1))
            k.length == 1 && k[0] in '0'..'9' -> {
                if (p.buf.length < MAX_PIN_CHARS) pin = p.copy(buf = p.buf + k, message = "")
            }
            k == "Enter" -> checkPin(p)
        }
    }

    private fun checkPin(p: RoomPin) {
        val wait = RoomPinGuard.waitSeconds()
        if (wait > 0L) {
            pin = p.copy(buf = "", message = "Too many wrong tries. Wait $wait seconds.")
            return
        }
        if (p.buf.length < 4) {
            pin = p.copy(message = "Type your PIN (4 to 8 digits).")
            return
        }
        if (StorageRules.pinMatches(current.assets, p.buf)) {
            RoomPinGuard.success()
            StorageRoomLog.add(RoomLevel.INFO, "auth", "PIN accepted")
            val redo = p.redo
            pin = null
            redo(current)
        } else {
            val locked = RoomPinGuard.failure()
            StorageRoomLog.add(RoomLevel.WARN, "auth", "wrong PIN entered")
            pin = p.copy(
                buf = "",
                message = if (locked) "Too many wrong tries. Wait 30 seconds." else "Wrong PIN. Try again."
            )
        }
    }

    // ---- Messages ----

    private fun err(text: String) {
        StorageRoomLog.add(RoomLevel.ERROR, "rules", text)
        message = RoomMessage(text, true)
    }

    private fun ok(text: String) {
        message = RoomMessage("Saved: $text", false)
    }

    companion object {
        const val OVERVIEW = 1
        const val APPS = 2
        const val PLANS = 3
        const val COPIES = 4
        const val LOG = 5

        private const val MAX_TYPED_CHARS = 8
        private const val MAX_PIN_CHARS = 8
    }
}
