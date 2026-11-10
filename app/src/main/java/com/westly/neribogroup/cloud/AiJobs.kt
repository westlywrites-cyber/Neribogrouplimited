package com.westly.neribogroup.cloud

import android.content.Context
import com.westly.neribogroup.data.AiCaseResult
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.CaseAi
import com.westly.neribogroup.data.CaseGenerator
import com.westly.neribogroup.data.CaseSkeleton
import com.westly.neribogroup.data.ComplaintCase
import com.westly.neribogroup.data.DiceEngine
import com.westly.neribogroup.data.DiceKind
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.PresetIdentity
import com.westly.neribogroup.data.PresetRoll
import com.westly.neribogroup.data.RollBlock
import com.westly.neribogroup.data.ProposalSlot
import com.westly.neribogroup.data.SOCIALS_APP_ID
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.SOFTWARE_APP_NAME
import com.westly.neribogroup.data.SocialDice
import com.westly.neribogroup.data.SocialsEngine
import com.westly.neribogroup.data.SoftwareAi
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.SoftwareGenerator
import com.westly.neribogroup.data.SoftwarePreset
import com.westly.neribogroup.data.dayKey
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import kotlin.math.max
import kotlin.random.Random

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
    val complete: Boolean = false,
    /** Phase 5: customer cases still waiting for their story. -1 = not known in this pass. */
    val casesWaiting: Int = -1,
    /** Part C-2: a plain line for the Socials page (for example when Claude's answer could not be used). Empty = nothing to say. */
    val socialsMessage: String = ""
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

    /**
     * Phase 5: like [rollForJobs] with a preset, but the roll does NOT create a case. When the roll happened and
     * was saved, the skeleton of the case that the roll caused (or null when no case happens) comes back.
     */
    fun rollWithSkeleton(appId: String, preset: PresetRoll?, wallNow: Long): JobRollOutcome

    /**
     * Phase 5: adds ONE case to the live ledger on the main thread, saved first like a roll. [build] gets the live
     * ledger and the protected "now" and returns the case (or null). Nothing is added when a case with [caseId]
     * is already there.
     */
    fun applyCase(caseId: String, wallNow: Long, build: (Ledger, Long) -> ComplaintCase?): CaseApplyResult

    /**
     * Phase 6: rolls the 2 software dice on the main thread with [preset] (null = the built-in random dice).
     * ledger.json is written first. ROLLED only when the roll happened and was saved.
     */
    fun rollSoftwareForJobs(preset: SoftwarePreset?, wallNow: Long): JobRollResult

    /**
     * Part C: applies Claude's software roll on the main thread. [build] gets the LIVE ledger and the
     * protected "now" and returns the new ledger, or null when nothing may be made (then BLOCKED comes back).
     * ledger.json is written first. Nothing is applied when today was already rolled.
     */
    fun applySoftwareRoll(dayKey: String, wallNow: Long, build: (Ledger, Long) -> Ledger?): JobRollResult

    /**
     * Part C-2: applies ONE day of the Socials roll on the main thread. [faces] maps a platform key to Claude's
     * face; an empty map closes the day with nothing gained. ledger.json is written first. ROLLED only when the
     * day was applied and saved; BLOCKED when it was already applied (or may not be applied); FAILED when saving
     * failed.
     */
    fun rollSocialsForJobs(day: String, wallNow: Long, faces: Map<String, Int>): JobRollResult
}

class JobRollOutcome(val result: JobRollResult, val skeleton: CaseSkeleton?)

enum class CaseApplyResult { ADDED, ALREADY_THERE, NOT_BUILT, FAILED }

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

/** One WRITE_CASE job as read from the cloud (Phase 5). */
class CaseJobRow(
    val id: String,
    val dedupeKey: String,
    val appId: String,
    val dayKey: String,
    val status: String,
    val result: JSONObject?,
    val context: JSONObject?
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

/** At most this many days are asked of Claude in one publish call (catch-up of missed days). */
private const val MAX_SOCIAL_JOBS_PER_CALL = 8

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
            // The two software dice are also DICE_ROLL jobs, but they have their own stages (Phase 6).
            if (text(o, "app_id") == SOFTWARE_APP_ID) continue
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

    /** Reads the WRITE_CASE rows of a list_jobs answer (Phase 5). */
    fun parseCaseRows(json: JSONObject): List<CaseJobRow> {
        val array = json.optJSONArray("jobs") ?: return emptyList()
        val rows = ArrayList<CaseJobRow>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            if (text(o, "kind") != CaseAi.KIND) continue
            val id = text(o, "id")
            val key = text(o, "dedupe_key")
            if (id.isEmpty() || key.isEmpty()) continue
            rows.add(
                CaseJobRow(
                    id = id,
                    dedupeKey = key,
                    appId = text(o, "app_id"),
                    dayKey = text(o, "day_key"),
                    status = text(o, "status"),
                    result = o.optJSONObject("result"),
                    context = o.optJSONObject("context")
                )
            )
        }
        return rows
    }

    /** The state of the "Today's roll" card. */
    fun stateFor(app: AppData, today: String, done: String?, counts: AiCounts?, offline: Boolean): AiRollState =
        stateForKey(app.lastRollDayKey, today, done, counts, offline)

    /** Same as [stateFor], for an app whose "last roll day" is kept elsewhere (the software dice). */
    fun stateForKey(lastRollDayKey: String, today: String, done: String?, counts: AiCounts?, offline: Boolean): AiRollState {
        if (lastRollDayKey == today) {
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

    private class Rows(
        val rows: List<JobRow>,
        val failure: ApiResult?,
        val cases: List<CaseJobRow> = emptyList(),
        val software: List<SwRow> = emptyList(),
        val socials: List<SocialRow> = emptyList()
    )

    private fun readRows(): Rows {
        val rows = ArrayList<JobRow>()
        val caseRows = ArrayList<CaseJobRow>()
        val swRows = ArrayList<SwRow>()
        val soRows = ArrayList<SocialRow>()
        val lists = listOf(listOf("pending", "claimed"), listOf("submitted"))
        for (statuses in lists) {
            val asked = JSONArray()
            for (s in statuses) asked.put(s)
            val r = api.rpc("list_jobs", JSONObject().put("p_statuses", asked).put("p_limit", 100))
            if (r is ApiResult.Ok) {
                rows.addAll(AiJobRules.parseRows(r.json))
                caseRows.addAll(AiJobRules.parseCaseRows(r.json))
                swRows.addAll(SoftwareAiRules.parseRows(r.json))
                soRows.addAll(SocialAiRules.parseRows(r.json))
            } else {
                return Rows(emptyList(), r)
            }
        }
        return Rows(rows, null, caseRows, swRows, soRows)
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
        var caseRows = first.cases
        val swRows = first.software
        val socialRows = first.socials

        val counts = LinkedHashMap<String, AiCounts>()
        var waiting = 0
        var answered = 0
        val doneUpdates = LinkedHashMap<String, String>()
        var domains: List<String>? = null
        var publishProblem = ""

        // 0. Software Development Clients come FIRST (Part C): one SOFTWARE_ROLL job a day, published and
        // applied before any Google Play dice job. Software is rolled only by Claude; there is no fallback.
        val swRun = processSoftwareRoll(host, swRows, notices, doneUpdates)
        if (swRun.failure != null) return failure(swRun.failure)
        if (swRun.rolled) rolledAny = true
        if (swRun.counts != null) {
            counts[SOFTWARE_APP_ID] = swRun.counts
            waiting += swRun.counts.total - swRun.counts.answered
            answered += swRun.counts.answered
        }
        if (swRun.publishProblem.isNotEmpty()) publishProblem = swRun.publishProblem

        // 1. Publish today's jobs, once per app per day.
        val gpApps = ledger0.apps.filter { it.googlePlayEnabled && it.id != SOFTWARE_APP_ID }
        val before = load().publishedDays
        val publishedNow = LinkedHashMap<String, String>()
        for ((id, day) in before) if (gpApps.any { it.id == id }) publishedNow[id] = day
        var publishedAny = false
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
            caseRows = again.cases
        }

        // 2. Read Claude's answers and apply every group that is ready.
        val groups = LinkedHashMap<String, MutableList<JobRow>>()
        for (row in rows) groups.getOrPut(row.appId + "|" + row.dayKey) { ArrayList() }.add(row)
        val ordered = groups.values.sortedWith(
            compareBy<MutableList<JobRow>>({ it[0].dayKey }, { group ->
                gpApps.indexOfFirst { it.id == group[0].appId }
            })
        )

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
            var skeleton: CaseSkeleton? = null
            val rollResult: JobRollResult
            if (preset != null) {
                // Phase 5: the roll does not write the case itself; its skeleton is sent to Claude.
                val outcome = host.rollWithSkeleton(appId, preset, wall)
                rollResult = outcome.result
                skeleton = outcome.skeleton
            } else {
                rollResult = host.rollForJobs(appId, null, wall)
            }
            when (rollResult) {
                JobRollResult.ROLLED -> {
                    if (skeleton != null) queueCaseJob(host, skeleton, caseRows)
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

        // 3. Socials (Part C-2): after the apps have rolled, because today's social roll waits for them.
        // Socials are rolled only by Claude; there is no built-in fallback.
        val soRun = processSocialsRoll(host, socialRows, notices, doneUpdates)
        if (soRun.changed) rolledAny = true
        if (soRun.counts != null) {
            counts[SOCIALS_APP_ID] = soRun.counts
            waiting += soRun.counts.total - soRun.counts.answered
            answered += soRun.counts.answered
        }
        if (soRun.publishProblem.isNotEmpty() && publishProblem.isEmpty()) publishProblem = soRun.publishProblem
        val soFailure = soRun.failure?.let { failure(it) }

        if (doneUpdates.isNotEmpty()) {
            val keep = ledger0.apps.map { it.id }.toSet() + SOCIALS_APP_ID
            update { data ->
                val merged = LinkedHashMap<String, String>()
                for ((k, v) in data.aiDone) if (k in keep) merged[k] = v
                merged.putAll(doneUpdates)
                data.copy(aiDone = merged)
            }
        }

        // Phase 5: publish new case jobs, read Claude's stories and add the cases.
        val caseRun = processCases(host, caseRows, false, notices)
        if (caseRun.added > 0) rolledAny = true

        val message = when {
            publishProblem.isNotEmpty() -> publishProblem
            caseRun.failure != null && !rolledAny -> failure(caseRun.failure).message
            soFailure != null && !rolledAny -> soFailure.message
            rolledAny -> ""
            waiting + answered > 0 -> "Checked just now. Claude has not finished yet."
            else -> "Checked just now. Nothing new from Claude yet."
        }
        return AiRunResult(
            ran = true,
            offline = soFailure?.offline == true,
            message = message,
            counts = counts,
            waiting = waiting,
            answered = answered,
            notices = notices,
            rolledAny = rolledAny,
            complete = true,
            casesWaiting = caseRun.waiting,
            socialsMessage = soRun.message
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Part C: Software Development Clients, ONE job a day (SOFTWARE_ROLL), rolled only by Claude.
    // Claude rolls both dice and writes the proposals in one answer. The app checks everything again,
    // creates the proposals once and marks the job applied. A rejected answer is asked again, up to 3
    // attempts a day. There is NO built-in fallback.
    // ---------------------------------------------------------------------------------------------

    private fun processSoftwareRoll(
        host: AiJobHost,
        rows0: List<SwRow>,
        notices: MutableList<String>,
        doneUpdates: MutableMap<String, String>
    ): SoftwareRun {
        val ledger0 = host.currentLedger()
        if (ledger0.apps.none { it.id == SOFTWARE_APP_ID }) return SoftwareRun()

        // Jobs of the older two-stage software roll are never used any more.
        rejectSw(rows0.filter { !it.isRoll && it.status == "submitted" }, "software is rolled by one job now")

        var rolled = false
        var counts: AiCounts? = null
        var openToday = false
        var publishProblem = ""
        val rollRows = rows0.filter { it.isRoll }.sortedWith(
            compareBy<SwRow>({ it.dayKey }, { SoftwareAi.attemptOfDedupeKey(it.dedupeKey) ?: 0 })
        )

        for (row in rollRows) {
            val live = host.currentLedger()
            val wallNow = System.currentTimeMillis()
            val liveToday = dayKey(SoftwareEngine.effectiveNow(live, wallNow))
            val age = AiJobRules.ageDays(row.dayKey, liveToday)
            val submitted = row.status == "submitted"
            if (age == null) {
                if (submitted) rejectId(row.id, "bad day")
                continue
            }
            if (age < 0L) {
                if (submitted) rejectId(row.id, "future day")
                continue
            }
            if (age > AiJobRules.MAX_AGE_DAYS) {
                if (submitted) rejectId(row.id, "too old")
                continue
            }
            if (!submitted) {
                // Still waiting for Claude.
                openToday = true
                counts = AiCounts(0, 1)
                continue
            }
            // Today (or a later day) was already rolled: nothing to create, just close the job.
            if (!SoftwareEngine.canRoll(live, wallNow) || live.software.lastRollDayKey >= row.dayKey) {
                markAppliedIds(listOf(row.id))
                continue
            }
            val check = SoftwareAi.validate(live, row.result, wallNow)
            if (!check.ok) {
                rejectId(row.id, check.reason)
                continue
            }
            val wall = System.currentTimeMillis()
            when (host.applySoftwareRoll(row.dayKey, wall) { current, now ->
                SoftwareAi.applyClaudeRoll(current, row.result, now, Random.Default)
            }) {
                JobRollResult.ROLLED -> {
                    markAppliedIds(listOf(row.id))
                    doneUpdates[SOFTWARE_APP_ID] = AiDone.encode(liveToday, wall, true, 1)
                    val made = host.currentLedger().software.rolls.firstOrNull()?.offers ?: 0
                    notices.add(
                        "Claude rolled the software dice: " + made + " new proposal" + (if (made == 1) "" else "s")
                    )
                    rolled = true
                }
                JobRollResult.BLOCKED -> rejectId(row.id, "roll blocked")
                JobRollResult.FAILED -> {
                    // The phone could not save. Nothing was marked: the next pass tries again.
                    openToday = true
                    counts = AiCounts(1, 1)
                }
            }
        }

        // Publish today's job (or the next attempt) when software can still roll and nothing is open.
        val live = host.currentLedger()
        val wall = System.currentTimeMillis()
        if (!openToday && SoftwareEngine.canRoll(live, wall)) {
            val today = dayKey(SoftwareEngine.effectiveNow(live, wall))
            val attempts = load().softwareAttempts[today] ?: 0
            if (attempts < SoftwareAi.MAX_ATTEMPTS) {
                val attempt = attempts + 1
                val list = JSONArray().put(SoftwareAi.buildJob(live, wall, attempt))
                val r = api.rpc("publish_roll_jobs", body().put("p_jobs", list))
                if (r is ApiResult.Ok) {
                    update { data ->
                        val merged = LinkedHashMap(data.softwareAttempts)
                        merged[today] = attempt
                        val keep = merged.keys.sortedDescending().take(5).toSet()
                        data.copy(softwareAttempts = merged.filterKeys { it in keep })
                    }
                    counts = AiCounts(0, 1)
                } else if (r is ApiResult.AppError) {
                    publishProblem = "Could not put the software roll job in the cloud (" +
                        (if (r.code.isEmpty()) "error" else r.code) + ")."
                } else {
                    return SoftwareRun(rolled = rolled, counts = counts, failure = r)
                }
            }
        }
        return SoftwareRun(rolled = rolled, counts = counts, publishProblem = publishProblem)
    }

    // ---------------------------------------------------------------------------------------------
    // Phase 6: Software Development Clients
    // Stage 0: the two dice jobs are published. Stage 1: when both are answered, the app plans the slots and
    // publishes one WRITE_PROPOSAL job per proposal. Stage 2: when every proposal is answered (or 48 hours
    // passed) the app rolls once with Claude's faces and proposals. A proposal that is missing or not valid is
    // written by the built-in generator from the same slot, so a day is never lost.
    // ---------------------------------------------------------------------------------------------

    private class SoftwareRun(
        val rolled: Boolean = false,
        val counts: AiCounts? = null,
        val failure: ApiResult? = null,
        val publishProblem: String = ""
    )

    private fun facesFromContext(ctx: JSONObject?): List<Int>? {
        val arr = ctx?.optJSONArray("faces") ?: return null
        if (arr.length() < 2) return null
        val out = ArrayList<Int>()
        for (i in 0 until 2) {
            val n = arr.opt(i) as? Number ?: return null
            val v = n.toInt()
            if (v !in 0..5) return null
            out.add(v)
        }
        return out
    }

    private fun rejectSw(rows: List<SwRow>, reason: String) {
        for (row in rows) rejectId(row.id, reason)
    }

    private fun processSoftware(
        host: AiJobHost,
        rows0: List<SwRow>,
        notices: MutableList<String>,
        doneUpdates: MutableMap<String, String>
    ): SoftwareRun {
        var rows = rows0
        val ledger0 = host.currentLedger()
        if (ledger0.apps.none { it.id == SOFTWARE_APP_ID }) return SoftwareRun()
        val wall0 = System.currentTimeMillis()
        val today0 = dayKey(SoftwareEngine.effectiveNow(ledger0, wall0))
        var publishProblem = ""

        // Stage 0: publish today's two dice (once; the cloud also refuses a second copy).
        if (SoftwareEngine.canRoll(ledger0, wall0)) {
            val ages = rows.mapNotNull { AiJobRules.ageDays(it.dayKey, today0) }
            if (ages.none { it == 0L || it == 1L }) {
                val list = JSONArray()
                for (j in SoftwareAiRules.buildDiceJobs(ledger0, wall0)) list.put(j)
                val r = api.rpc("publish_roll_jobs", body().put("p_jobs", list))
                if (r is ApiResult.Ok) {
                    val again = readRows()
                    val againFailure = again.failure
                    if (againFailure != null) return SoftwareRun(failure = againFailure)
                    rows = again.software
                } else if (r is ApiResult.AppError) {
                    publishProblem = "Could not put the software dice jobs in the cloud (" +
                        (if (r.code.isEmpty()) "error" else r.code) + ")."
                } else {
                    return SoftwareRun(failure = r)
                }
            }
        }

        var counts: AiCounts? = null
        var rolled = false
        var domains: List<String>? = null

        for (day in rows.map { it.dayKey }.distinct().sorted()) {
            val group = rows.filter { it.dayKey == day }
            val dice = group.filter { it.isDice }
            val props = group.filter { it.isProposal }
            val submitted = group.filter { it.status == "submitted" }
            val live = host.currentLedger()
            val wallNow = System.currentTimeMillis()
            val liveToday = dayKey(SoftwareEngine.effectiveNow(live, wallNow))
            val age = AiJobRules.ageDays(day, liveToday)

            if (age == null) {
                rejectSw(submitted, "bad day")
                continue
            }
            if (age < 0L) {
                rejectSw(submitted, "future day")
                continue
            }
            if (age > AiJobRules.MAX_AGE_DAYS) {
                rejectSw(submitted, "too old")
                continue
            }
            // Already rolled (here, or from another phone): nothing to apply, just close the jobs.
            if (live.software.lastRollDayKey.isNotEmpty() && live.software.lastRollDayKey >= day) {
                markAppliedIds(submitted.map { it.id })
                continue
            }
            if (dice.isEmpty()) {
                rejectSw(submitted, "no dice jobs")
                continue
            }

            // The two dice are not all answered yet.
            if (!AiJobRules.groupReady(dice.map { it.status })) {
                if (age >= AiJobRules.FALLBACK_AGE_DAYS) {
                    val wall = System.currentTimeMillis()
                    if (host.rollSoftwareForJobs(null, wall) == JobRollResult.ROLLED) {
                        rejectSw(submitted, "fallback to built-in dice")
                        doneUpdates[SOFTWARE_APP_ID] = AiDone.encode(liveToday, wall, false, 0)
                        notices.add(SOFTWARE_APP_NAME + " was rolled with the built-in dice (no answer in time).")
                        rolled = true
                        continue
                    }
                }
                val answeredDice = dice.count { it.status == "submitted" }
                counts = AiCounts(answeredDice, dice.size)
                continue
            }

            // The faces. Once proposal jobs exist, the faces are read from them, so they never change.
            val fromContext = props.firstNotNullOfOrNull { facesFromContext(it.context) }
            val faces: List<Int>
            if (fromContext != null) {
                faces = fromContext
            } else {
                val f = intArrayOf(-1, -1)
                for (row in dice) {
                    if (row.status != "submitted") continue
                    val idx = when (row.dice) {
                        SoftwareAiRules.DICE_CONTACTS -> 0
                        SoftwareAiRules.DICE_PROPOSALS -> 1
                        else -> -1
                    }
                    val face = SoftwareAiRules.faceOf(row.result)
                    if (idx < 0 || face < 0) {
                        rejectId(row.id, "face not usable")
                        continue
                    }
                    f[idx] = face
                }
                for (i in 0..1) if (f[i] !in 0..5) f[i] = Random.Default.nextInt(0, 6)
                faces = listOf(f[0], f[1])
            }

            var preset: SoftwarePreset? = null

            if (props.isEmpty()) {
                // Stage 1: how many proposals, then one job for each.
                val wanted = SoftwareEngine.wantedFor(faces[0], faces[1])
                val n = minOf(wanted, SoftwareEngine.roomFor(live, wallNow))
                if (n <= 0) {
                    // Nothing to write: the day is applied at once.
                    preset = SoftwarePreset(faces = faces)
                } else {
                    val slots = (0 until n).map { SoftwareGenerator.planSlot(it, live.software.rates, Random.Default) }
                    val list = JSONArray()
                    for (j in SoftwareAiRules.buildProposalJobs(live, slots, faces, day, wallNow)) list.put(j)
                    val r = api.rpc("publish_roll_jobs", body().put("p_jobs", list))
                    if (r is ApiResult.Ok) {
                        counts = AiCounts(dice.size, dice.size + n)
                    } else if (r is ApiResult.AppError) {
                        publishProblem = "Could not put the software proposal jobs in the cloud (" +
                            (if (r.code.isEmpty()) "error" else r.code) + ")."
                        counts = AiCounts(dice.size, dice.size)
                    } else {
                        return SoftwareRun(rolled = rolled, counts = counts, failure = r, publishProblem = publishProblem)
                    }
                    continue
                }
            } else {
                // Stage 2: wait for the proposals, or use what is there after 48 hours.
                val open = props.count { it.status == "pending" || it.status == "claimed" }
                if (open > 0 && age < AiJobRules.FALLBACK_AGE_DAYS) {
                    val done = dice.size + props.count { it.status == "submitted" }
                    counts = AiCounts(done, dice.size + props.size)
                    continue
                }
                val slotsByIndex = HashMap<Int, ProposalSlot>()
                for (row in props) {
                    val slot = SoftwareAiRules.slotFromJson(row.context?.optJSONObject("slot"))
                    if (slot != null && row.proposalIndex >= 0) slotsByIndex[row.proposalIndex] = slot
                }
                val top = slotsByIndex.keys.maxOrNull() ?: -1
                val slotList = (0..top).map { slotsByIndex[it] }
                val proposalList = (0..top).map { idx ->
                    val row = props.firstOrNull { it.proposalIndex == idx && it.status == "submitted" }
                    if (row == null) null else SoftwareAiRules.parseProposal(row.result)
                }
                var allowed = domains
                if (allowed == null) {
                    allowed = fetchDomains()
                    domains = allowed
                }
                preset = SoftwarePreset(faces = faces, slots = slotList, proposals = proposalList, allowedDomains = allowed)
            }

            // Apply the day once.
            val wall = System.currentTimeMillis()
            when (host.rollSoftwareForJobs(preset, wall)) {
                JobRollResult.ROLLED -> {
                    markAppliedIds(submitted.map { it.id })
                    doneUpdates[SOFTWARE_APP_ID] = AiDone.encode(liveToday, wall, true, submitted.size)
                    val made = host.currentLedger().software.rolls.firstOrNull()?.offers ?: 0
                    notices.add(
                        "Claude rolled the software dice: " + made + " new proposal" + (if (made == 1) "" else "s")
                    )
                    rolled = true
                }
                JobRollResult.BLOCKED -> rejectSw(submitted, "roll blocked")
                JobRollResult.FAILED -> {
                    // The phone could not save. Nothing was marked: the next pass tries again.
                    counts = AiCounts(submitted.size, submitted.size)
                }
            }
        }
        return SoftwareRun(rolled = rolled, counts = counts, publishProblem = publishProblem)
    }

    // ---------------------------------------------------------------------------------------------
    // Part C-2: the Socials roll. ONE SOCIAL_ROLL job for every day that has followers to give. Claude gives one
    // face 0 to 5 per platform; the app checks the answer again and turns the faces into followers. Days are
    // applied oldest first and each at most once. Only Claude rolls: there is NO built-in fallback.
    // ---------------------------------------------------------------------------------------------

    private class SocialsRun(
        /** True when the ledger changed (a day was applied or closed), so a sync should follow. */
        val changed: Boolean = false,
        val counts: AiCounts? = null,
        val failure: ApiResult? = null,
        val publishProblem: String = "",
        val message: String = ""
    )

    private fun processSocialsRoll(
        host: AiJobHost,
        rows0: List<SocialRow>,
        notices: MutableList<String>,
        doneUpdates: MutableMap<String, String>
    ): SocialsRun {
        var changed = false
        var counts: AiCounts? = null
        var message = ""
        var publishProblem = ""
        var apiProblem: ApiResult? = null
        var appliedDays = 0
        var lastApplied = ""

        val start = host.currentLedger()
        val today = dayKey(SocialDice.nowFor(start, System.currentTimeMillis()))

        // Answers that can no longer be used: too old, from the future, or for a day that is already rolled.
        for (row in rows0) {
            if (row.status != "submitted") continue
            val age = AiJobRules.ageDays(row.dayKey, today)
            when {
                age == null -> rejectId(row.id, "bad day")
                age < 0L -> rejectId(row.id, "future day")
                age > SocialAiRules.MAX_AGE_DAYS -> rejectId(row.id, "too old")
                row.dayKey <= start.socials.lastRollDayKey -> markAppliedIds(listOf(row.id))
            }
        }

        val days = SocialsEngine.pendingDays(start.socials, today)
        for ((index, day) in days.withIndex()) {
            val live = host.currentLedger()
            if (day <= live.socials.lastRollDayKey) continue
            // Today waits until every app that has a running advert has rolled today. Past days do not wait.
            if (day == today && SocialsEngine.waitingForApps(live, day)) break

            val bases = SocialsEngine.basesOn(live, day).filter { it.base > 0L }
            val dayRows = rows0.filter { it.dayKey == day }.sortedBy { it.attempt }

            if (bases.isEmpty()) {
                // No platform has followers to give that day: it is closed without contacting Claude.
                for (row in dayRows) if (row.status == "submitted") rejectId(row.id, "nothing to roll")
                val closed = host.rollSocialsForJobs(day, System.currentTimeMillis(), emptyMap())
                if (closed == JobRollResult.FAILED) break
                if (closed == JobRollResult.ROLLED) changed = true
                continue
            }

            // Claude is still working on this day: the later days wait, so days are never applied out of order.
            if (dayRows.any { it.status == "pending" || it.status == "claimed" }) {
                counts = AiCounts(0, 1)
                break
            }

            var dayDone = false
            var saveFailed = false
            for (row in dayRows) {
                if (row.status != "submitted") continue
                val expected = (SocialAiRules.keysOf(row.context) ?: bases.map { it.key }).toSet()
                val check = SocialAiRules.validate(expected, row.result)
                if (!check.ok) {
                    // A bad answer is refused; the next attempt (at most 3 a day) is asked below.
                    rejectId(row.id, check.reason)
                    continue
                }
                when (host.rollSocialsForJobs(day, System.currentTimeMillis(), check.faces)) {
                    JobRollResult.ROLLED -> {
                        markAppliedIds(listOf(row.id))
                        changed = true
                        dayDone = true
                        appliedDays += 1
                        lastApplied = day
                    }
                    JobRollResult.BLOCKED -> {
                        if (host.currentLedger().socials.lastRollDayKey >= day) {
                            markAppliedIds(listOf(row.id))
                            dayDone = true
                        } else {
                            rejectId(row.id, "roll blocked")
                        }
                    }
                    // The phone could not save. Nothing is marked: the next pass tries again.
                    JobRollResult.FAILED -> saveFailed = true
                }
                if (dayDone || saveFailed) break
            }
            if (saveFailed) {
                counts = AiCounts(1, 1)
                break
            }
            if (dayDone) continue

            // Not rolled yet: ask Claude (again), up to 3 times for a day.
            val used = load().socialAttempts[day] ?: 0
            if (used >= SocialAiRules.MAX_ATTEMPTS) {
                if (day == today) {
                    message = "Claude's answer could not be used. Try again later."
                    break
                }
                // All three requests of a past day are used (the cloud never takes a fourth), so it can never be
                // answered. It is closed with nothing gained so that the later days can go on.
                val closed = host.rollSocialsForJobs(day, System.currentTimeMillis(), emptyMap())
                if (closed == JobRollResult.FAILED) break
                if (closed == JobRollResult.ROLLED) changed = true
                continue
            }

            // Publish this day and the later days that still need a job (a job that already exists is never made again).
            val jobs = JSONArray()
            val made = LinkedHashMap<String, Int>()
            for (d in days.subList(index, days.size)) {
                if (made.size >= MAX_SOCIAL_JOBS_PER_CALL) break
                val l2 = host.currentLedger()
                if (d <= l2.socials.lastRollDayKey) continue
                if (d == today && SocialsEngine.waitingForApps(l2, d)) break
                if (d != day && rows0.any { it.dayKey == d }) continue
                val n = load().socialAttempts[d] ?: 0
                if (n >= SocialAiRules.MAX_ATTEMPTS) continue
                val job = SocialAiRules.buildJob(l2, d, n + 1) ?: continue
                jobs.put(job)
                made[d] = n + 1
            }
            if (jobs.length() > 0) {
                val r = api.rpc("publish_roll_jobs", body().put("p_jobs", jobs))
                if (r is ApiResult.Ok) {
                    update { data ->
                        val merged = LinkedHashMap(data.socialAttempts)
                        merged.putAll(made)
                        val keep = merged.keys.sortedDescending().take(40).toSet()
                        data.copy(socialAttempts = merged.filterKeys { it in keep })
                    }
                    counts = AiCounts(0, 1)
                } else if (r is ApiResult.AppError) {
                    publishProblem = "Could not put the Socials roll job in the cloud (" +
                        (if (r.code.isEmpty()) "error" else r.code) + ")."
                } else {
                    apiProblem = r
                }
            }
            break
        }

        if (appliedDays > 0) {
            notices.add(
                if (appliedDays == 1) "Claude rolled the Socials for " + AiJobRules.prettyDay(lastApplied)
                else "Claude rolled $appliedDays days of Socials."
            )
            doneUpdates[SOCIALS_APP_ID] = AiDone.encode(lastApplied, System.currentTimeMillis(), true, appliedDays)
        }
        return SocialsRun(
            changed = changed,
            counts = counts,
            failure = apiProblem,
            publishProblem = publishProblem,
            message = message
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
    // Phase 5: Claude writes the customer complaints
    // ---------------------------------------------------------------------------------------------

    private class CaseRun(val added: Int, val waiting: Int, val failure: ApiResult?)

    /** The case jobs this phone decided and has not finished yet, by dedupe key. */
    private fun localCaseJobs(): LinkedHashMap<String, JSONObject> {
        val out = LinkedHashMap<String, JSONObject>()
        for (text in load().caseJobs) {
            val o = runCatching { JSONObject(text) }.getOrNull() ?: continue
            val key = o.optString("dedupe_key", "")
            if (key.isNotEmpty()) out[key] = o
        }
        return out
    }

    /**
     * Remembers the job of a new skeleton on the phone (it is published in the same pass). Only one unfinished
     * case job per app exists at a time, so the "at most 2 open cases" and "7 days apart" rules stay true.
     */
    private fun queueCaseJob(host: AiJobHost, sk: CaseSkeleton, caseRows: List<CaseJobRow>) {
        val job = CaseAi.buildJob(sk)
        val key = job.getString("dedupe_key")
        val ledger = host.currentLedger()
        val local = localCaseJobs()
        if (local.containsKey(key)) return
        val busyLocal = local.entries.any { (k, o) ->
            o.optString("app_id") == sk.appId && ledger.cases.none { it.id == CaseAi.caseId(k) }
        }
        val busyCloud = caseRows.any { it.appId == sk.appId }
        if (busyLocal || busyCloud) return
        update { it.copy(caseJobs = it.caseJobs + job.toString()) }
    }

    private fun markAppliedIds(ids: List<String>) {
        val later = ArrayList<String>()
        for (id in ids) {
            val r = api.rpc("mark_job_applied", jobBody(id))
            if (r is ApiResult.Ok || r is ApiResult.AppError) continue
            later.add(id)
        }
        if (later.isNotEmpty()) {
            update { it.copy(appliedPendingMark = (it.appliedPendingMark + later).distinct()) }
        }
    }

    /** Best effort: the cloud only accepts it for a submitted job. */
    private fun rejectId(id: String, reason: String) {
        api.rpc("reject_job", jobBody(id).put("p_reason", reason))
    }

    private fun applyAi(host: AiJobHost, id: String, sk: CaseSkeleton, parsed: AiCaseResult?, wall: Long): CaseApplyResult {
        if (parsed == null) return CaseApplyResult.NOT_BUILT
        return host.applyCase(id, wall) { ledger, now ->
            CaseGenerator.buildFromAi(ledger, sk, parsed, now)?.copy(id = id)
        }
    }

    private fun applyTemplates(host: AiJobHost, id: String, sk: CaseSkeleton, wall: Long): CaseApplyResult =
        host.applyCase(id, wall) { ledger, now ->
            CaseGenerator.buildFromTemplates(ledger, sk, now, Random.Default).copy(id = id)
        }

    /**
     * Turns every waiting case job into a case: Claude's story when it passes every check, otherwise the built-in
     * texts. A job that is still waiting after 72 hours (or any job when [builtInOnly] is true) is written by the
     * built-in generator. The case id comes from the job, so a case is never added twice.
     */
    private fun processCases(
        host: AiJobHost,
        caseRows: List<CaseJobRow>,
        builtInOnly: Boolean,
        notices: MutableList<String>
    ): CaseRun {
        val local = localCaseJobs()
        val byKey = LinkedHashMap<String, CaseJobRow>()
        for (r in caseRows) if (!byKey.containsKey(r.dedupeKey)) byKey[r.dedupeKey] = r
        val keys = LinkedHashSet<String>()
        keys.addAll(local.keys)
        keys.addAll(byKey.keys)

        val finished = HashSet<String>()
        var added = 0
        var waiting = 0
        var failure: ApiResult? = null

        for (key in keys) {
            val id = CaseAi.caseId(key)
            val row = byKey[key]
            val before = host.currentLedger()

            if (before.cases.any { it.id == id }) {
                // Already a case (another phone, or an earlier pass): only tell the cloud.
                if (row != null && row.status == "submitted") markAppliedIds(listOf(row.id))
                finished.add(key)
                continue
            }
            val context = row?.context ?: local[key]?.optJSONObject("context")
            val skeleton = CaseSkeleton.fromJson(context?.optJSONObject("skeleton"))
            if (skeleton == null) {
                if (row != null && row.status == "submitted") rejectId(row.id, "unreadable case job")
                finished.add(key)
                continue
            }
            val wall = System.currentTimeMillis()
            val overdue = max(wall, before.casesClockMillis) - skeleton.createdMillis >= CaseAi.TIMEOUT_MILLIS

            var useTemplates = false
            var rejectRowId: String? = null

            if (row != null && row.status == "submitted" && !builtInOnly) {
                when (applyAi(host, id, skeleton, CaseAi.parseResult(row.result), wall)) {
                    CaseApplyResult.ADDED -> {
                        markAppliedIds(listOf(row.id))
                        added++
                        notices.add("Claude wrote a customer complaint for " + skeleton.appName + ".")
                        finished.add(key)
                    }
                    CaseApplyResult.ALREADY_THERE -> {
                        markAppliedIds(listOf(row.id))
                        finished.add(key)
                    }
                    CaseApplyResult.FAILED -> waiting++
                    CaseApplyResult.NOT_BUILT -> {
                        // Claude's answer broke a rule: the built-in generator writes this one.
                        useTemplates = true
                        rejectRowId = row.id
                    }
                }
            } else if (builtInOnly || overdue) {
                useTemplates = true
                if (row != null && row.status == "submitted") rejectRowId = row.id
            } else if (row == null) {
                val job = local[key]
                if (job == null) {
                    finished.add(key)
                } else {
                    val r = api.rpc("publish_roll_jobs", body().put("p_jobs", JSONArray().put(job)))
                    if (r is ApiResult.Ok) {
                        if (r.json.optInt("inserted", 0) > 0) {
                            waiting++
                        } else {
                            // The cloud has this job but it is no longer open (rejected or expired).
                            useTemplates = true
                        }
                    } else if (r is ApiResult.AppError) {
                        useTemplates = true
                    } else {
                        if (failure == null) failure = r
                        waiting++
                    }
                }
            } else {
                waiting++
            }

            if (useTemplates) {
                when (applyTemplates(host, id, skeleton, wall)) {
                    CaseApplyResult.ADDED -> {
                        added++
                        notices.add("A customer complaint came in for " + skeleton.appName + ".")
                        finished.add(key)
                    }
                    CaseApplyResult.ALREADY_THERE -> finished.add(key)
                    else -> waiting++
                }
                if (finished.contains(key) && rejectRowId != null) rejectId(rejectRowId, "case written by the built-in generator")
            }
        }

        if (finished.isNotEmpty()) {
            update { data ->
                data.copy(caseJobs = data.caseJobs.filter { text ->
                    val k = runCatching { JSONObject(text).optString("dedupe_key", "") }.getOrDefault("")
                    k !in finished
                })
            }
        }
        return CaseRun(added, waiting, failure)
    }

    /** The owner tapped "Write cases with the built-in generator": every waiting case is written now. */
    fun runCasesBuiltIn(host: AiJobHost): AiRunResult {
        if (!ready()) return AiRunResult(ran = false)
        val result = sync.runExclusive(10_000L) {
            try {
                val read = readRows()
                val rows = if (read.failure == null) read.cases else emptyList()
                val notices = ArrayList<String>()
                val run = processCases(host, rows, true, notices)
                AiRunResult(
                    ran = true,
                    message = if (run.added > 0) "" else "No case was waiting.",
                    notices = notices,
                    rolledAny = run.added > 0,
                    casesWaiting = run.waiting
                )
            } catch (e: Throwable) {
                AiRunResult(
                    ran = true,
                    message = "The cases could not be written (" + e.javaClass.simpleName + "). Your data is safe."
                )
            }
        }
        return result ?: AiRunResult(ran = false)
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
                    if (appId == SOFTWARE_APP_ID) {
                        for (row in read.software) {
                            val age = AiJobRules.ageDays(row.dayKey, today) ?: continue
                            if (age < 0L) continue
                            rejectId(row.id, "fallback to built-in dice")
                        }
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
