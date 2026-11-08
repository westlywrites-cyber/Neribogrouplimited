package com.westly.neribogroup.cloud

import android.content.Context
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.DiceKind
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.PresetIdentity
import com.westly.neribogroup.data.PresetRoll
import com.westly.neribogroup.data.RollBlock
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.dayKey
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.max

// =================================================================================================
// Phase 4B: "Roll next". The app puts one small job per dice in the cloud mailbox, Claude answers
// them, and the app rolls its own engine with Claude's faces and names. Nothing here changes money:
// only faces and names pass through, and every amount is worked out by DiceEngine.
// =================================================================================================

/** How many dice of one app are answered out of how many are waiting. */
data class AiCounts(val answered: Int, val total: Int)

/** What the "Today's roll" card of one app shows. */
enum class AiRollKind { WAITING, ROLLED_CLAUDE, ROLLED_BUILTIN, ROLLED_OTHER, NO_JOBS, OFFLINE }

data class AiRollState(
    val kind: AiRollKind,
    val answered: Int = 0,
    val total: Int = 0,
    val timeMillis: Long = 0L
)

/** The AI settings kept in the cloud (the owner changes them in Settings). */
data class AiSettings(
    val paused: Boolean,
    val minGapMinutes: Int,
    val maxJobsPerDay: Int,
    val leaseMinutes: Int,
    val allowedDomains: List<String>
)

/** The answer of reading or changing the AI settings. [message] is empty when it worked. */
data class AiSettingsOutcome(val settings: AiSettings?, val message: String)

/** How one pass over the jobs ended. [ran] is false when nothing was done (switched off, or busy). */
data class AiRunResult(
    val ran: Boolean,
    val offline: Boolean = false,
    val message: String = "",
    val counts: Map<String, AiCounts> = emptyMap(),
    val waiting: Int = 0,
    val answered: Int = 0,
    val notices: List<String> = emptyList(),
    val rolledAny: Boolean = false,
    /** True only when the pass reached its end, so [counts] can be trusted. */
    val complete: Boolean = false
)

/** What the engine needs from the app. */
enum class JobRollResult { ROLLED, BLOCKED, FAILED }

interface AiJobHost {
    /** The phone's live ledger right now. */
    fun currentLedger(): Ledger

    /**
     * Rolls one app with [preset] (null = the built-in random dice) on the main thread. ledger.json is
     * written first; only then is the live ledger replaced. ROLLED only when the roll happened and was saved.
     */
    fun rollForJobs(appId: String, preset: PresetRoll?, wallNow: Long): JobRollResult
}

/** One job as read from the cloud. */
class JobRow(
    val id: String,
    val dedupeKey: String,
    val appId: String,
    val dayKey: String,
    val status: String,
    val result: JSONObject?,
    val kind: DiceKind?
)

/** The check of one answer. */
class JobCheck(val ok: Boolean, val reason: String, val face: Int, val users: List<PresetIdentity>)

/** How a finished AI-mode roll is remembered in cloud_sync.json: "day|millis|C or B|jobs". */
object AiDone {
    class Info(val dayKey: String, val millis: Long, val byClaude: Boolean, val jobs: Int)

    fun encode(dayKey: String, millis: Long, byClaude: Boolean, jobs: Int): String =
        dayKey + "|" + millis + "|" + (if (byClaude) "C" else "B") + "|" + jobs

    fun parse(text: String?): Info? {
        if (text.isNullOrEmpty()) return null
        val parts = text.split("|")
        if (parts.size < 3) return null
        val millis = parts[1].toLongOrNull() ?: return null
        return Info(parts[0], millis, parts[2] == "C", parts.getOrNull(3)?.toIntOrNull() ?: 0)
    }
}

/** The pure rules: nothing here touches the network, the phone or the ledger. */
object AiJobRules {

    const val KIND = "DICE_ROLL"
    const val PRIORITY = 100
    const val MAX_USERS_CAP = 25
    const val MAX_AGE_DAYS = 14L

    /** A group that is still waiting after this many days is rolled by the built-in dice (48 hours). */
    const val FALLBACK_AGE_DAYS = 2L

    private const val CONTEXT_LIMIT_BYTES = 6000

    fun dedupeKey(day: String, appId: String, kind: DiceKind): String = day + ":" + appId + ":" + kind.name

    fun kindOfDedupeKey(key: String): DiceKind? =
        runCatching { DiceKind.valueOf(key.substringAfterLast(':')) }.getOrNull()

    fun prettyDay(key: String): String = runCatching {
        LocalDate.parse(key).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault()))
    }.getOrDefault(key)

    /** Whole days from [day] to [today]. Null when a text is not a date. Negative = [day] is in the future. */
    fun ageDays(day: String, today: String): Long? =
        runCatching { ChronoUnit.DAYS.between(LocalDate.parse(day), LocalDate.parse(today)) }.getOrNull()

    /** A domain the owner may allow, for example "example.com". */
    fun validDomain(text: String): Boolean = Regex("^[a-z0-9.-]+\\.[a-z]{2,}$").matches(text)

    /**
     * A group of jobs (one app, one day) is ready when no job is still pending or claimed and at least one
     * is submitted. A rejected, expired or cancelled job does not hold the group back: its dice gets a
     * built-in random face when the group is applied.
     */
    fun groupReady(statuses: List<String>): Boolean {
        if (statuses.any { it == "pending" || it == "claimed" }) return false
        return statuses.any { it == "submitted" }
    }

    fun resultSchema(kind: DiceKind, maxUsers: Int): JSONObject {
        val face = JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 5)
        val comment = JSONObject().put("type", "string").put("maxLength", 200)
        val props = JSONObject().put("face", face).put("comment", comment)
        val required = JSONArray().put("face")
        if (kind == DiceKind.NEW) {
            val userProps = JSONObject()
                .put("name", JSONObject().put("type", "string").put("minLength", 3).put("maxLength", 60))
                .put(
                    "email",
                    JSONObject().put("type", "string").put("maxLength", 80)
                        .put("pattern", "^[a-z0-9._-]+@[a-z0-9.-]+$")
                )
                .put(
                    "phone",
                    JSONObject().put("type", "string").put("maxLength", 16)
                        .put("pattern", "^(\\+234[789][0-9]{9}|0[789][0-9]{9})$")
                )
            val item = JSONObject()
                .put("type", "object")
                .put("properties", userProps)
                .put("required", JSONArray().put("name").put("email").put("phone"))
                .put("additionalProperties", false)
            props.put(
                "users",
                JSONObject().put("type", "array").put("maxItems", maxUsers).put("items", item)
            )
            required.put("users")
        }
        return JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", required)
            .put("additionalProperties", false)
    }

    /** How many users Claude is asked to write for the NEW dice. */
    fun usersWanted(app: AppData, now: Long): Int =
        max(1, minOf(MAX_USERS_CAP, DiceEngine.maxNewUsers(app, now)))

    fun context(app: AppData, kind: DiceKind, now: Long, maxUsers: Int): JSONObject {
        var names = 40
        while (true) {
            val ctx = contextWithNames(app, kind, now, maxUsers, names)
            if (names <= 0 || ctx.toString().toByteArray(Charsets.UTF_8).size <= CONTEXT_LIMIT_BYTES) return ctx
            names = if (names > 10) 10 else 0
        }
    }

    private fun contextWithNames(app: AppData, kind: DiceKind, now: Long, maxUsers: Int, names: Int): JSONObject {
        val ctx = JSONObject()
        ctx.put("job", KIND)
        ctx.put("company", "Neribo Group")
        ctx.put("app", JSONObject().put("name", app.name).put("id", app.id))
        ctx.put(
            "dice",
            JSONObject()
                .put("kind", kind.name)
                .put("label", DiceEngine.diceTitle(kind))
                .put("faces", "0-5")
                .put("meaning", DiceEngine.diceMeaning(kind))
        )
        ctx.put("day", dayKey(now))
        ctx.put(
            "state",
            JSONObject()
                .put("totalUsers", app.totalUsers)
                .put("freeUsers", app.freeUsers)
                .put("premiumUsers", app.premiumUsers)
                .put("proUsers", app.proUsers)
                .put("premiumPriceNaira", app.premiumPrice)
                .put("proPriceNaira", app.proPrice)
        )
        val table = JSONArray()
        val effects = DiceEngine.faceTable(app, kind, now)
        for (i in effects.indices) table.put(JSONObject().put("face", i).put("effect", effects[i]))
        ctx.put("faceTable", table)
        if (kind == DiceKind.NEW) {
            val recent = JSONArray()
            for (u in app.users.takeLast(names)) recent.put(u.name)
            ctx.put(
                "identities",
                JSONObject()
                    .put("count", maxUsers)
                    .put("recentNames", recent)
                    .put("instruction", "Give exactly " + maxUsers + " different, varied names.")
            )
        }
        ctx.put("alreadyRolledToday", JSONArray())
        return ctx
    }

    /** The jobs of one app for today: one per visible dice, in dice order. */
    fun buildJobs(app: AppData, appIndex: Int, now: Long): List<JSONObject> {
        val day = dayKey(now)
        val maxUsers = usersWanted(app, now)
        val jobs = ArrayList<JSONObject>()
        for (kind in DiceEngine.visibleDice(app, now)) {
            val job = JSONObject()
            job.put("dedupe_key", dedupeKey(day, app.id, kind))
            job.put("kind", KIND)
            job.put("app_id", app.id)
            job.put("day_key", day)
            job.put("priority", PRIORITY)
            job.put("sort_order", appIndex * 10 + kind.ordinal)
            job.put("context", context(app, kind, now, maxUsers))
            job.put("result_schema", resultSchema(kind, maxUsers))
            jobs.add(job)
        }
        return jobs
    }

    /** Never trusts the server alone: the face must be a whole number 0 to 5, the users a list. */
    fun validate(kind: DiceKind, result: JSONObject?): JobCheck {
        if (result == null) return JobCheck(false, "no answer", -1, emptyList())
        val raw = result.opt("face")
        val number = raw as? Number
        if (number == null) return JobCheck(false, "face is not a number", -1, emptyList())
        val d = number.toDouble()
        if (d != Math.floor(d) || d < 0.0 || d > 5.0) return JobCheck(false, "face out of range", -1, emptyList())
        val users = ArrayList<PresetIdentity>()
        if (kind == DiceKind.NEW) {
            val list = result.opt("users")
            if (list != null && list != JSONObject.NULL) {
                if (list !is JSONArray) return JobCheck(false, "users is not a list", -1, emptyList())
                val count = minOf(list.length(), 100)
                for (i in 0 until count) {
                    val o = list.optJSONObject(i) ?: continue
                    users.add(PresetIdentity(text(o, "name"), text(o, "email"), text(o, "phone")))
                }
            }
        }
        return JobCheck(true, "", d.toInt(), users)
    }

    private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

    /** Reads the rows of a list_jobs answer. Only dice jobs are kept. */
    fun parseRows(json: JSONObject): List<JobRow> {
        val array = json.optJSONArray("jobs") ?: return emptyList()
        val rows = ArrayList<JobRow>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (text(o, "kind") != KIND) continue
            val id = text(o, "id")
            if (id.isEmpty()) continue
            val key = text(o, "dedupe_key")
            rows.add(
                JobRow(
                    id = id,
                    dedupeKey = key,
                    appId = text(o, "app_id"),
                    dayKey = text(o, "day_key"),
                    status = text(o, "status"),
                    result = o.optJSONObject("result"),
                    kind = kindOfDedupeKey(key)
                )
            )
        }
        return rows
    }

    /** The state of the "Today's roll" card. */
    fun stateFor(app: AppData, today: String, done: String?, counts: AiCounts?, offline: Boolean): AiRollState {
        if (app.lastRollDayKey == today) {
            val info = AiDone.parse(done)
            if (info != null && info.dayKey == today) {
                return AiRollState(
                    if (info.byClaude) AiRollKind.ROLLED_CLAUDE else AiRollKind.ROLLED_BUILTIN,
                    timeMillis = info.millis
                )
            }
            return AiRollState(AiRollKind.ROLLED_OTHER)
        }
        if (offline) return AiRollState(AiRollKind.OFFLINE)
        if (counts != null && counts.total > 0) {
            return AiRollState(AiRollKind.WAITING, counts.answered, counts.total)
        }
        return AiRollState(AiRollKind.NO_JOBS)
    }
}

/**
 * Publishes the dice jobs, reads Claude's answers and applies them through the existing dice engine.
 * Every function blocks: call it from a background thread. It shares the sync lock, so a sync and a
 * job pass never run at the same moment.
 */
class AiJobEngine(
    context: Context,
    private val config: CloudConfig,
    private val api: SupabaseApi,
    private val sync: SyncEngine
) {

    private val app: Context = context.applicationContext

    private fun load(): SyncData = SyncStore.load(app, config.deviceId)

    private fun update(change: (SyncData) -> SyncData) {
        SyncStore.save(app, change(load()))
    }

    private fun ready(): Boolean =
        config.aiMode && config.cloudMode == CloudMode.ON && config.signedIn && load().initialized

    private fun body(): JSONObject =
        JSONObject().put("p_op_id", UUID.randomUUID().toString()).put("p_device_id", config.deviceId)

    private fun jobBody(id: String): JSONObject = body().put("p_job_id", id)

    // ---------------------------------------------------------------------------------------------
    // The pass
    // ---------------------------------------------------------------------------------------------

    fun run(host: AiJobHost): AiRunResult {
        if (!ready()) return AiRunResult(ran = false)
        val result = sync.runExclusive(10_000L) {
            try {
                runInner(host)
            } catch (e: Throwable) {
                AiRunResult(
                    ran = true,
                    message = "Claude's jobs had a problem (" + e.javaClass.simpleName + "). Your data is safe."
                )
            }
        }
        return result ?: AiRunResult(ran = false)
    }

    private class Rows(val rows: List<JobRow>, val failure: ApiResult?)

    private fun readRows(): Rows {
        val rows = ArrayList<JobRow>()
        val lists = listOf(listOf("pending", "claimed"), listOf("submitted"))
        for (statuses in lists) {
            val asked = JSONArray()
            for (s in statuses) asked.put(s)
            val r = api.rpc("list_jobs", JSONObject().put("p_statuses", asked).put("p_limit", 100))
            if (r is ApiResult.Ok) rows.addAll(AiJobRules.parseRows(r.json)) else return Rows(emptyList(), r)
        }
        return Rows(rows, null)
    }

    private fun failure(r: ApiResult): AiRunResult {
        return when (r) {
            ApiResult.Offline -> AiRunResult(
                ran = true,
                offline = true,
                message = "No internet. Claude's answers will be checked later."
            )
            ApiResult.AuthExpired -> {
                config.clearSession()
                AiRunResult(ran = true, message = "Please sign in again. Your data on the phone is safe.")
            }
            is ApiResult.Http -> {
                if (r.code == 429 || r.code in 500..599) {
                    AiRunResult(ran = true, offline = true, message = "The cloud is busy. Try again later.")
                } else {
                    AiRunResult(ran = true, message = "The cloud had a problem (code " + r.code + "). Try again later.")
                }
            }
            is ApiResult.AppError -> AiRunResult(
                ran = true,
                message = "The cloud had a problem (" + (if (r.code.isEmpty()) "error" else r.code) + "). Try again later."
            )
            is ApiResult.Ok -> AiRunResult(ran = true, message = "The cloud answered in a way this app did not expect.")
        }
    }

    private fun runInner(host: AiJobHost): AiRunResult {
        val notices = ArrayList<String>()
        var rolledAny = false
        val ledger0 = host.currentLedger()
        val now0 = max(System.currentTimeMillis(), ledger0.casesClockMillis)
        val today = dayKey(now0)

        // Jobs applied earlier whose "applied" mark did not reach the cloud.
        val markProblem = retryMarks()
        if (markProblem != null) return failure(markProblem)

        val first = readRows()
        val firstFailure = first.failure
        if (firstFailure != null) return failure(firstFailure)
        var rows = first.rows

        // 1. Publish today's jobs, once per app per day.
        val gpApps = ledger0.apps.filter { it.googlePlayEnabled && it.id != SOFTWARE_APP_ID }
        val before = load().publishedDays
        val publishedNow = LinkedHashMap<String, String>()
        for ((id, day) in before) if (gpApps.any { it.id == id }) publishedNow[id] = day
        var publishedAny = false
        var publishProblem = ""
        for ((index, a) in gpApps.withIndex()) {
            if (DiceEngine.rollBlock(a, now0) != RollBlock.OK) continue
            if (a.lastRollDayKey == today) continue
            if (publishedNow[a.id] == today) continue
            val mine = rows.filter { it.appId == a.id }
            val ages = mine.mapNotNull { AiJobRules.ageDays(it.dayKey, today) }
            if (ages.any { it == 0L }) {
                // Today's jobs are already there (published earlier, or from another phone).
                publishedNow[a.id] = today
                continue
            }
            // Yesterday's dice are still being answered: they will be used, so no second set is made.
            if (ages.any { it == 1L }) continue
            val jobs = AiJobRules.buildJobs(a, index, now0)
            if (jobs.isEmpty()) continue
            val list = JSONArray()
            for (j in jobs) list.put(j)
            val r = api.rpc("publish_roll_jobs", body().put("p_jobs", list))
            if (r is ApiResult.Ok) {
                publishedNow[a.id] = today
                publishedAny = true
            } else if (r is ApiResult.AppError) {
                publishProblem = "Could not put today's dice jobs in the cloud (" +
                    (if (r.code.isEmpty()) "error" else r.code) + ")."
            } else {
                return failure(r)
            }
        }
        if (publishedNow != before) update { it.copy(publishedDays = publishedNow) }
        if (publishedAny) {
            val again = readRows()
            val againFailure = again.failure
            if (againFailure != null) return failure(againFailure)
            rows = again.rows
        }

        // 2. Read Claude's answers and apply every group that is ready.
        val groups = LinkedHashMap<String, MutableList<JobRow>>()
        for (row in rows) groups.getOrPut(row.appId + "|" + row.dayKey) { ArrayList() }.add(row)
        val ordered = groups.values.sortedWith(
            compareBy<MutableList<JobRow>>({ it[0].dayKey }, { group ->
                gpApps.indexOfFirst { it.id == group[0].appId }
            })
        )

        val counts = LinkedHashMap<String, AiCounts>()
        var waiting = 0
        var answered = 0
        val doneUpdates = LinkedHashMap<String, String>()
        var domains: List<String>? = null

        for (group in ordered) {
            val appId = group[0].appId
            val day = group[0].dayKey
            val submitted = group.filter { it.status == "submitted" }
            val openCount = group.count { it.status == "pending" || it.status == "claimed" }
            val live = host.currentLedger().apps.firstOrNull { it.id == appId }
            val age = AiJobRules.ageDays(day, today)

            if (live == null || !live.googlePlayEnabled) {
                rejectAll(submitted, "app not available")
                continue
            }
            if (age == null) {
                rejectAll(submitted, "bad day")
                continue
            }
            if (age < 0L) {
                rejectAll(submitted, "future day")
                continue
            }
            if (age > AiJobRules.MAX_AGE_DAYS) {
                rejectAll(submitted, "too old")
                continue
            }

            val statuses = group.map { it.status }
            if (!AiJobRules.groupReady(statuses)) {
                if (age >= AiJobRules.FALLBACK_AGE_DAYS && live.lastRollDayKey != today) {
                    // Claude took more than 48 hours: the app rolls with the built-in dice.
                    val wall = System.currentTimeMillis()
                    if (host.rollForJobs(appId, null, wall) == JobRollResult.ROLLED) {
                        rejectAll(submitted, "fallback to built-in dice")
                        doneUpdates[appId] = AiDone.encode(today, wall, false, 0)
                        notices.add(live.name + " was rolled with the built-in dice (no answer in time).")
                        rolledAny = true
                        continue
                    }
                }
                counts[appId] = AiCounts(submitted.size, submitted.size + openCount)
                waiting += openCount
                answered += submitted.size
                continue
            }

            // Already rolled for that day (applied before, but the mark did not reach the cloud).
            if (live.lastRollDayKey == day) {
                markApplied(submitted)
                continue
            }
            val wallCheck = System.currentTimeMillis()
            if (DiceEngine.rollBlock(live, max(wallCheck, host.currentLedger().casesClockMillis)) != RollBlock.OK) {
                rejectAll(submitted, "roll blocked")
                continue
            }

            // Check every answer in Kotlin; a bad one is rejected and its dice gets a random face.
            val faces = IntArray(DiceKind.values().size) { -1 }
            var identities: List<PresetIdentity> = emptyList()
            val usable = ArrayList<JobRow>()
            for (row in submitted) {
                val kind = row.kind
                if (kind == null) {
                    reject(row, "unknown dice")
                    continue
                }
                val check = AiJobRules.validate(kind, row.result)
                if (!check.ok) {
                    reject(row, check.reason)
                    continue
                }
                faces[kind.ordinal] = check.face
                if (kind == DiceKind.NEW) identities = check.users
                usable.add(row)
            }

            var preset: PresetRoll? = null
            if (usable.isNotEmpty()) {
                var allowed = domains
                if (allowed == null) {
                    allowed = fetchDomains()
                    domains = allowed
                }
                preset = PresetRoll(faces, identities, allowed)
            }

            val wall = System.currentTimeMillis()
            when (host.rollForJobs(appId, preset, wall)) {
                JobRollResult.ROLLED -> {
                    markApplied(usable)
                    doneUpdates[appId] = AiDone.encode(today, wall, preset != null, usable.size)
                    notices.add(
                        if (preset != null) "Claude rolled " + live.name + " for " + AiJobRules.prettyDay(today)
                        else live.name + " was rolled with the built-in dice."
                    )
                    rolledAny = true
                }
                JobRollResult.BLOCKED -> rejectAll(usable, "roll blocked")
                JobRollResult.FAILED -> {
                    // The phone could not save. Nothing was marked: the next pass tries again.
                    counts[appId] = AiCounts(submitted.size, submitted.size + openCount)
                    answered += submitted.size
                }
            }
        }

        if (doneUpdates.isNotEmpty()) {
            val keep = ledger0.apps.map { it.id }.toSet()
            update { data ->
                val merged = LinkedHashMap<String, String>()
                for ((k, v) in data.aiDone) if (k in keep) merged[k] = v
                merged.putAll(doneUpdates)
                data.copy(aiDone = merged)
            }
        }

        val message = when {
            publishProblem.isNotEmpty() -> publishProblem
            rolledAny -> ""
            waiting + answered > 0 -> "Checked just now. Claude has not finished yet."
            else -> "Checked just now. Nothing new from Claude yet."
        }
        return AiRunResult(
            ran = true,
            message = message,
            counts = counts,
            waiting = waiting,
            answered = answered,
            notices = notices,
            rolledAny = rolledAny,
            complete = true
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Marking and rejecting
    // ---------------------------------------------------------------------------------------------

    /** Tells the cloud these jobs are applied. A job that cannot be marked now is remembered and retried. */
    private fun markApplied(rows: List<JobRow>) {
        val later = ArrayList<String>()
        for (row in rows) {
            val r = api.rpc("mark_job_applied", jobBody(row.id))
            if (r is ApiResult.Ok || r is ApiResult.AppError) continue
            later.add(row.id)
        }
        if (later.isNotEmpty()) {
            update { it.copy(appliedPendingMark = (it.appliedPendingMark + later).distinct()) }
        }
    }

    private fun retryMarks(): ApiResult? {
        val ids = load().appliedPendingMark
        if (ids.isEmpty()) return null
        val remaining = ArrayList<String>()
        var problem: ApiResult? = null
        for (id in ids) {
            if (problem != null) {
                remaining.add(id)
                continue
            }
            val r = api.rpc("mark_job_applied", jobBody(id))
            if (r is ApiResult.Ok || r is ApiResult.AppError) continue
            remaining.add(id)
            problem = r
        }
        update { it.copy(appliedPendingMark = remaining) }
        return problem
    }

    /** Best effort: a failure is ignored. */
    private fun reject(row: JobRow, reason: String) {
        api.rpc("reject_job", jobBody(row.id).put("p_reason", reason))
    }

    private fun rejectAll(rows: List<JobRow>, reason: String) {
        for (row in rows) reject(row, reason)
    }

    // ---------------------------------------------------------------------------------------------
    // The owner rolled with the built-in dice instead
    // ---------------------------------------------------------------------------------------------

    /**
     * After "Roll with built-in dice instead": remembers it for the card and rejects the jobs of that app
     * that are still waiting (best effort; a job that is not submitted yet cannot be rejected).
     */
    fun afterBuiltInRoll(appId: String, rolledMillis: Long, todayMillis: Long) {
        if (!ready()) return
        sync.runExclusive(10_000L) {
            try {
                val today = dayKey(todayMillis)
                update { data ->
                    val merged = LinkedHashMap(data.aiDone)
                    merged[appId] = AiDone.encode(today, rolledMillis, false, 0)
                    data.copy(aiDone = merged)
                }
                val read = readRows()
                if (read.failure == null) {
                    for (row in read.rows) {
                        if (row.appId != appId) continue
                        val age = AiJobRules.ageDays(row.dayKey, today) ?: continue
                        if (age < 0L) continue
                        reject(row, "fallback to built-in dice")
                    }
                }
            } catch (e: Throwable) {
                // Best effort only.
            }
            true
        }
    }

    // ---------------------------------------------------------------------------------------------
    // AI settings in the cloud
    // ---------------------------------------------------------------------------------------------

    private fun parseSettings(json: JSONObject): AiSettings? {
        val s = json.optJSONObject("settings") ?: return null
        val domains = ArrayList<String>()
        val array = s.optJSONArray("allowed_email_domains")
        if (array != null) {
            for (i in 0 until array.length()) {
                val d = array.optString(i, "").trim().lowercase()
                if (d.isNotEmpty()) domains.add(d)
            }
        }
        return AiSettings(
            paused = s.optBoolean("paused", false),
            minGapMinutes = s.optInt("min_gap_minutes", 30),
            maxJobsPerDay = s.optInt("max_jobs_per_day", 50),
            leaseMinutes = s.optInt("lease_minutes", 30),
            allowedDomains = domains
        )
    }

    private fun calm(r: ApiResult): String = failure(r).message

    fun fetchSettings(): AiSettingsOutcome {
        return try {
            val r = api.rpc("get_ai_settings", JSONObject())
            if (r is ApiResult.Ok) {
                val s = parseSettings(r.json)
                if (s != null) AiSettingsOutcome(s, "")
                else AiSettingsOutcome(null, "The cloud answered in a way this app did not expect.")
            } else {
                AiSettingsOutcome(null, calm(r))
            }
        } catch (e: Throwable) {
            AiSettingsOutcome(null, "Could not read the AI settings. Try again later.")
        }
    }

    fun saveSettings(s: AiSettings): AiSettingsOutcome {
        return try {
            val domains = JSONArray()
            for (d in s.allowedDomains) domains.put(d)
            val request = JSONObject()
                .put("p_paused", s.paused)
                .put("p_min_gap_minutes", s.minGapMinutes)
                .put("p_max_jobs_per_day", s.maxJobsPerDay)
                .put("p_lease_minutes", s.leaseMinutes)
                .put("p_allowed_email_domains", domains)
            val r = api.rpc("update_ai_settings", request)
            if (r is ApiResult.Ok) {
                val saved = parseSettings(r.json)
                if (saved != null) AiSettingsOutcome(saved, "")
                else AiSettingsOutcome(null, "The cloud answered in a way this app did not expect.")
            } else if (r is ApiResult.AppError) {
                AiSettingsOutcome(null, "The cloud did not accept these settings (" + r.code + ").")
            } else {
                AiSettingsOutcome(null, calm(r))
            }
        } catch (e: Throwable) {
            AiSettingsOutcome(null, "Could not save the AI settings. Try again later.")
        }
    }

    /** The allowed email domains from the cloud, or the example domains when they cannot be read. */
    private fun fetchDomains(): List<String> {
        val outcome = fetchSettings()
        val domains = outcome.settings?.allowedDomains
        return if (domains.isNullOrEmpty()) com.westly.neribogroup.data.IdentityGenerator.DEFAULT_AI_DOMAINS else domains
    }
}
