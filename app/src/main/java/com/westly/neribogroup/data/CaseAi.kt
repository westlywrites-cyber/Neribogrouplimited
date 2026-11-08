package com.westly.neribogroup.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

// =================================================================================================
// Phase 5: Claude writes the customer complaints. Everything here is a PURE function (no Android, no
// network): the template of a case, the job that asks Claude, the check of Claude's answer and the
// money maths. Claude only supplies words, day offsets and numbers inside the skeleton's limits.
// =================================================================================================

/** One event of Claude's answer, exactly as sent. */
data class AiCaseEvent(
    val kind: String,
    val title: String,
    val body: String,
    val dayOffset: Int,
    val hour: Int,
    val minute: Int
)

/** Claude's whole answer for one WRITE_CASE job, exactly as sent (nothing is checked yet). */
data class AiCaseResult(
    val subject: String,
    val events: List<AiCaseEvent>,
    val regulatorRef: String,
    val court: String,
    val suitNo: String,
    val refundBaseMultiple: Int,
    val damages: Long,
    val costs: Long,
    val penalty: Long,
    val settlementPercent: Int,
    val awardedCosts: Long,
    val generalDamagesPercent: Int,
    val dueReason: String
)

/** Whole-naira money maths for cases. Long only, never floating point. */
object CaseMoney {
    const val MAGISTRATE = "Magistrate's Court, Ikeja, Lagos"
    const val HIGH_COURT = "High Court of Lagos State, Ikeja"
    const val REASON_SETTLED = "Settlement agreed out of court"
    const val REASON_CLAIMANT = "Court-ordered refund, damages and costs"
    const val REASON_PENALTY = "Regulatory penalty — NDPC"

    /** Rounds half up to the nearest [step] naira. For example 182,400 with step 5,000 gives 180,000. */
    fun roundTo(value: Long, step: Long): Long = ((value + step / 2L) / step) * step

    fun claim(price: Long, multiple: Int, damages: Long, costs: Long): Long = price * multiple + damages + costs

    fun courtFor(claim: Long, limits: CaseLimits): String =
        if (claim <= limits.magistrateMaxClaim) MAGISTRATE else HIGH_COURT

    /** Settled out of court: [percent] of the claim, rounded to 5,000, at least 5,000. */
    fun settlementAmount(claim: Long, percent: Int): Long =
        maxOf(5_000L, roundTo(claim * percent / 100L, CaseLimits.ROUND_STEP))

    /** Claimant wins: the real refund plus general damages plus awarded costs, never more than the claim. */
    fun claimantAward(refundBase: Long, damages: Long, generalDamagesPercent: Int, awardedCosts: Long, claim: Long): Long {
        val general = roundTo(damages * generalDamagesPercent / 100L, CaseLimits.ROUND_STEP)
        return minOf(claim, refundBase + general + awardedCosts)
    }
}

/** One required event of a case: what it is, and where Claude may put it. */
class CaseStep(
    val kind: CaseEventKind,
    val actor: CaseActor,
    val status: CaseStatus?,
    /** Allowed day offset from the first email (day 0). */
    val absMin: Int,
    val absMax: Int,
    /** Index of the step this one is measured from (-1 = no relative rule), and the allowed days after it. */
    val relTo: Int,
    val relMin: Int,
    val relMax: Int,
    /** Court events: the app fixes the day, Claude's dayOffset is ignored. */
    val appFixesDay: Boolean,
    /** Minimum minutes after the previous event (the app moves the time forward when needed). */
    val minGapMinutes: Int,
    /** When not empty, the app sets this title itself (hearing titles are used by the court stage label). */
    val fixedTitle: String,
    val hint: String
)

object CaseAi {

    const val KIND = "WRITE_CASE"
    const val PRIORITY = 90
    const val CONTEXT_LIMIT_BYTES = 8000

    /** A job that is not answered within this time is written by the built-in generator (72 hours). */
    const val TIMEOUT_MILLIS = 72L * 3_600_000L

    private const val HOUR = 3_600_000L
    private const val MINUTE = 60_000L

    private val LONG_DIGITS = Regex("[0-9]{10,}")
    private val REG_REF = Regex("^(FCCPC|NDPC|ARCON)/(CPD|CMP)/[0-9]{4}/[0-9]{4}$")
    private val SUIT_MIK = Regex("^MIK/[0-9]{3,4}/[0-9]{4}$")
    private val SUIT_LD = Regex("^LD/[0-9]{4}/[0-9]{4}$")

    // ---------------------------------------------------------------------------------------------
    // Names and ids
    // ---------------------------------------------------------------------------------------------

    fun dedupeKey(sk: CaseSkeleton): String = "case:" + dayKey(sk.createdMillis) + ":" + sk.appId

    /**
     * The id of the case made for a job. It is worked out from the job's dedupe key (one key = one job), so any
     * phone, and the built-in fallback, make the SAME id: a case is never added twice.
     */
    fun caseId(dedupeKey: String): String =
        UUID.nameUUIDFromBytes(("case-id:" + dedupeKey).toByteArray(Charsets.UTF_8)).toString()

    private fun yearOf(millis: Long): String = SimpleDateFormat("yyyy", Locale.US).format(Date(millis))

    // ---------------------------------------------------------------------------------------------
    // The template: the required events in order, with their allowed days
    // ---------------------------------------------------------------------------------------------

    private fun step(
        kind: CaseEventKind,
        actor: CaseActor,
        status: CaseStatus?,
        hint: String,
        absMin: Int = 0,
        absMax: Int = 365,
        relTo: Int = -1,
        relMin: Int = 0,
        relMax: Int = 0,
        appFixesDay: Boolean = false,
        minGapMinutes: Int = 15,
        fixedTitle: String = ""
    ): CaseStep = CaseStep(kind, actor, status, absMin, absMax, relTo, relMin, relMax, appFixesDay, minGapMinutes, fixedTitle, hint)

    /** The ordered events a case of this skeleton must have. The number is exact: no more, no fewer. */
    fun steps(sk: CaseSkeleton): List<CaseStep> {
        val l = ArrayList<CaseStep>()
        l.add(step(CaseEventKind.CUSTOMER_EMAIL, CaseActor.CUSTOMER, CaseStatus.OPEN,
            "The customer's first email.", absMin = 0, absMax = 0))
        when (sk.tier) {
            "A" -> {
                l.add(step(CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, null,
                    "The agent thanks the customer and says the case is being looked at.", absMin = 0, absMax = 1))
                l.add(step(CaseEventKind.RESOLUTION, CaseActor.SUPPORT, CaseStatus.RESOLVED,
                    "The agent solves the case. No company money is paid.", absMin = 2, absMax = 6))
                if (sk.thanksReply) {
                    l.add(step(CaseEventKind.CUSTOMER_REPLY, CaseActor.CUSTOMER, null,
                        "A short thank-you from the customer.", relTo = 2, relMin = 1, relMax = 2))
                }
            }
            "B" -> {
                l.add(step(CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, CaseStatus.PENDING,
                    "The agent asks for details (receipt, screenshot or similar).", absMin = 1, absMax = 2))
                if (sk.tierBBranch == "REPLY") {
                    l.add(step(CaseEventKind.CUSTOMER_REPLY, CaseActor.CUSTOMER, null,
                        "The customer sends the details.", absMin = 3, absMax = 5))
                    l.add(step(CaseEventKind.RESOLUTION, CaseActor.SUPPORT, CaseStatus.RESOLVED,
                        "The agent solves the case. No company money is paid.", absMin = 8, absMax = 20))
                } else {
                    l.add(step(CaseEventKind.RESOLUTION, CaseActor.SUPPORT, CaseStatus.RESOLVED,
                        "The agent closes the case because the customer never replied.", absMin = 14, absMax = 14))
                }
            }
            else -> {
                l.add(step(CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, CaseStatus.OPEN,
                    "The agent acknowledges the complaint.", absMin = 1, absMax = 1))
                l.add(step(CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, CaseStatus.PENDING,
                    "The agent offers an explanation and a refund request through Google Play.", absMin = 4, absMax = 6))
                l.add(step(CaseEventKind.CUSTOMER_REPLY, CaseActor.CUSTOMER, null,
                    "The customer rejects the offer.", relTo = 2, relMin = 0, relMax = 1, minGapMinutes = 120))
                l.add(step(CaseEventKind.CUSTOMER_ESCALATION, CaseActor.CUSTOMER, CaseStatus.ESCALATED,
                    "The customer says they will report the company to the regulator.", absMin = 10, absMax = 25))
                l.add(step(CaseEventKind.REGULATOR_LETTER, CaseActor.REGULATOR, CaseStatus.ESCALATED,
                    "The regulator acknowledges the complaint and asks the company to respond in 14 days.",
                    relTo = 4, relMin = 3, relMax = 7))
                l.add(step(CaseEventKind.COMPANY_RESPONSE, CaseActor.COMPANY, CaseStatus.ESCALATED,
                    "The company answers the regulator.", relTo = 5, relMin = 1, relMax = 3))
                val decisionStatus = when (sk.regulatorDecision) {
                    "PENALTY" -> CaseStatus.PAYMENT_DUE
                    "UNRESOLVED" -> CaseStatus.ESCALATED
                    else -> CaseStatus.RESOLVED
                }
                val decisionHint = when (sk.regulatorDecision) {
                    "CLOSED" -> "The regulator closes the complaint with no further action."
                    "REFUND" -> "The regulator directs that the real charge is refunded through Google Play."
                    "PENALTY" -> "The regulator imposes a penalty on the company (the app sets the amount from your penalty number)."
                    else -> "The regulator says the matter is not resolved. The customer will go to court."
                }
                l.add(step(CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, decisionStatus, decisionHint,
                    relTo = 5, relMin = 21, relMax = 45))
                if (sk.hasCourtPath) {
                    l.add(step(CaseEventKind.COURT_FILED, CaseActor.COURT, CaseStatus.IN_COURT,
                        "The court filing: the customer sues the company.", appFixesDay = true))
                    l.add(step(CaseEventKind.COURT_SERVICE, CaseActor.COURT, null,
                        "The company is served with the court papers.", appFixesDay = true))
                    l.add(step(CaseEventKind.COURT_HEARING, CaseActor.COURT, null,
                        "The first hearing.", appFixesDay = true, fixedTitle = "First hearing"))
                    repeat(sk.adjournments) {
                        l.add(step(CaseEventKind.COURT_ADJOURNED, CaseActor.COURT, null,
                            "The court adjourns the matter to a new date.", appFixesDay = true))
                    }
                    l.add(step(CaseEventKind.COURT_HEARING, CaseActor.COURT, null,
                        "Trial and final addresses.", appFixesDay = true, fixedTitle = "Trial and final addresses"))
                    l.add(step(CaseEventKind.COURT_HEARING, CaseActor.COURT, null,
                        "The court reserves its judgment.", appFixesDay = true, fixedTitle = "Judgment reserved"))
                    when (sk.lawsuitOutcome) {
                        "DISMISSED" -> l.add(step(CaseEventKind.COURT_JUDGMENT, CaseActor.COURT, CaseStatus.CLOSED,
                            "Judgment: the claim is dismissed. The company wins.", appFixesDay = true))
                        "SETTLED" -> l.add(step(CaseEventKind.SETTLEMENT, CaseActor.COURT, CaseStatus.PAYMENT_DUE,
                            "The parties settle out of court and the court records it.", appFixesDay = true))
                        else -> l.add(step(CaseEventKind.COURT_JUDGMENT, CaseActor.COURT, CaseStatus.PAYMENT_DUE,
                            "Judgment for the claimant: the company must pay.", appFixesDay = true))
                    }
                }
            }
        }
        return l
    }

    /** The days (from the day the suit is filed) of the court events, in order. The app fixes these. */
    fun courtDayOffsets(sk: CaseSkeleton): List<Int> {
        val out = ArrayList<Int>()
        out.add(0)
        out.add(7)
        var day = 35
        out.add(day)
        repeat(sk.adjournments) {
            day += 28
            out.add(day)
        }
        day += 28
        out.add(day)
        day += 21
        out.add(day)
        val judgment = day + 35
        out.add(if (sk.lawsuitOutcome == "SETTLED") judgment - settleShift(sk) else judgment)
        return out
    }

    /** 14..21 days before the judgment day (the built-in texts pick this at random; here it is fixed by the skeleton). */
    private fun settleShift(sk: CaseSkeleton): Int = 14 + Math.floorMod(sk.customerId.hashCode(), 8)

    /** The reply delay (15 minutes to 3 hours) when the support reply is sent on day 0. */
    private fun sameDayDelayMinutes(sk: CaseSkeleton): Int = 15 + Math.floorMod(sk.customerId.hashCode(), 166)

    /** The days Claude may use for step [index], worked out from the earlier steps. Null when the app fixes the day. */
    fun dayRange(steps: List<CaseStep>, index: Int): Pair<Int, Int>? {
        val s = steps[index]
        if (s.appFixesDay) return null
        var lo = s.absMin
        var hi = s.absMax
        if (s.relTo >= 0) {
            val anchor = dayRange(steps, s.relTo)
            if (anchor != null) {
                lo = maxOf(lo, anchor.first + s.relMin)
                hi = minOf(hi, anchor.second + s.relMax)
            }
        }
        if (index > 0) {
            val before = dayRange(steps, index - 1)
            if (before != null) lo = maxOf(lo, before.first)
        }
        return Pair(lo, hi)
    }

    // ---------------------------------------------------------------------------------------------
    // Reading Claude's answer
    // ---------------------------------------------------------------------------------------------

    private val RESULT_KEYS = setOf(
        "subject", "events", "regulatorRef", "court", "suitNo", "refundBaseMultiple", "damages", "costs",
        "penalty", "settlementPercent", "awardedCosts", "generalDamagesPercent", "dueReason"
    )
    private val EVENT_KEYS = setOf("kind", "title", "body", "dayOffset", "hour", "minute")

    private fun longOf(o: JSONObject, key: String): Long? {
        val v = o.opt(key)
        if (v !is Number) return null
        val d = v.toDouble()
        if (d != Math.floor(d) || Math.abs(d) > 9.0e15) return null
        return d.toLong()
    }

    private fun intOf(o: JSONObject, key: String): Int? {
        val v = longOf(o, key) ?: return null
        if (v < Int.MIN_VALUE || v > Int.MAX_VALUE) return null
        return v.toInt()
    }

    private fun stringOf(o: JSONObject, key: String): String? = o.opt(key) as? String

    private fun onlyKnownKeys(o: JSONObject, allowed: Set<String>): Boolean {
        val keys = o.keys()
        while (keys.hasNext()) {
            if (keys.next() !in allowed) return false
        }
        return allowed.all { o.has(it) }
    }

    /** Reads the JSON of Claude's answer. Returns null for any unknown shape. Nothing is checked beyond the shape. */
    fun parseResult(json: JSONObject?): AiCaseResult? {
        if (json == null) return null
        if (!onlyKnownKeys(json, RESULT_KEYS)) return null
        val array = json.opt("events") as? JSONArray ?: return null
        if (array.length() > 40) return null
        val events = ArrayList<AiCaseEvent>()
        for (i in 0 until array.length()) {
            val o = array.opt(i) as? JSONObject ?: return null
            if (!onlyKnownKeys(o, EVENT_KEYS)) return null
            events.add(
                AiCaseEvent(
                    kind = stringOf(o, "kind") ?: return null,
                    title = stringOf(o, "title") ?: return null,
                    body = stringOf(o, "body") ?: return null,
                    dayOffset = intOf(o, "dayOffset") ?: return null,
                    hour = intOf(o, "hour") ?: return null,
                    minute = intOf(o, "minute") ?: return null
                )
            )
        }
        return AiCaseResult(
            subject = stringOf(json, "subject") ?: return null,
            events = events,
            regulatorRef = stringOf(json, "regulatorRef") ?: return null,
            court = stringOf(json, "court") ?: return null,
            suitNo = stringOf(json, "suitNo") ?: return null,
            refundBaseMultiple = intOf(json, "refundBaseMultiple") ?: return null,
            damages = longOf(json, "damages") ?: return null,
            costs = longOf(json, "costs") ?: return null,
            penalty = longOf(json, "penalty") ?: return null,
            settlementPercent = intOf(json, "settlementPercent") ?: return null,
            awardedCosts = longOf(json, "awardedCosts") ?: return null,
            generalDamagesPercent = intOf(json, "generalDamagesPercent") ?: return null,
            dueReason = stringOf(json, "dueReason") ?: return null
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Checking Claude's text
    // ---------------------------------------------------------------------------------------------

    /**
     * Null when the text is fine, otherwise a short reason. Length in characters, no control characters (a new
     * line is allowed), no link, no markdown fence, no e-mail address except the customer's own, no number of
     * 10 or more digits (it could look like a bank account).
     */
    fun textProblem(text: String, minLength: Int, maxLength: Int, customerEmail: String): String? {
        if (text.length < minLength || text.length > maxLength) return "length"
        for (ch in text) {
            if (ch == '\n') continue
            if (ch.isISOControl()) return "control character"
        }
        if (text.contains("http", ignoreCase = true)) return "link"
        if (text.contains("```")) return "markdown"
        val withoutOwn = if (customerEmail.isNotEmpty()) text.replace(customerEmail, "", ignoreCase = true) else text
        if (withoutOwn.contains('@')) return "e-mail address"
        if (LONG_DIGITS.containsMatchIn(text)) return "long number"
        return null
    }

    // ---------------------------------------------------------------------------------------------
    // Building the case from Claude's story (null on any failure)
    // ---------------------------------------------------------------------------------------------

    /** Why [assemble] would refuse this answer, or null when it is accepted. Used by the self-check and the logs. */
    fun problem(ledger: Ledger, sk: CaseSkeleton, r: AiCaseResult, now: Long): String? {
        val built = build(ledger, sk, r, now)
        return built.second
    }

    fun assemble(ledger: Ledger, sk: CaseSkeleton, r: AiCaseResult, now: Long): ComplaintCase? =
        build(ledger, sk, r, now).first

    private fun build(ledger: Ledger, sk: CaseSkeleton, r: AiCaseResult, now: Long): Pair<ComplaintCase?, String?> {
        fun no(reason: String): Pair<ComplaintCase?, String?> = Pair(null, reason)

        val st = steps(sk)
        if (r.events.size != st.size) return no("wrong number of events")
        val email = sk.customerEmail

        if (textProblem(r.subject, 8, 120, email) != null) return no("subject")
        for (i in st.indices) {
            val e = r.events[i]
            if (e.kind != st[i].kind.name) return no("wrong kind at " + i)
            if (textProblem(e.title, 3, 140, email) != null) return no("title " + i)
            if (textProblem(e.body, 40, 1600, email) != null) return no("body " + i)
            if (e.hour < 0 || e.hour > 23 || e.minute < 0 || e.minute > 59) return no("time " + i)
            if (e.dayOffset < 0 || e.dayOffset > 365) return no("day " + i)
        }

        // Days: non-decreasing and inside the ranges. Court events: the app fixes the day.
        val firstFixed = st.indexOfFirst { it.appFixesDay }
        val nonFixedCount = if (firstFixed < 0) st.size else firstFixed
        val days = IntArray(st.size)
        for (i in 1 until nonFixedCount) {
            val s = st[i]
            val e = r.events[i]
            val d = e.dayOffset
            if (d < s.absMin || d > s.absMax) return no("day range " + i)
            if (s.relTo >= 0) {
                val delta = d - days[s.relTo]
                if (delta < s.relMin || delta > s.relMax) return no("day after " + i)
            }
            if (d < days[i - 1]) return no("days go back " + i)
            days[i] = d
            val sameDayReply = i == 1 && sk.tier == "A" && d == 0
            if (!sameDayReply && (e.hour < 9 || e.hour > 17)) return no("working hours " + i)
        }
        for (i in nonFixedCount until st.size) {
            val e = r.events[i]
            if (e.hour < 9 || e.hour > 17) return no("working hours " + i)
        }

        // Moments: event 0 is "now"; every later event is at least its gap after the one before.
        val dues = LongArray(st.size)
        dues[0] = now
        var last = now
        for (i in 1 until nonFixedCount) {
            val s = st[i]
            val e = r.events[i]
            val sameDayReply = i == 1 && sk.tier == "A" && e.dayOffset == 0
            var due = if (sameDayReply) {
                last + sameDayDelayMinutes(sk) * MINUTE
            } else {
                dayStartMillis(now + days[i] * DAY_MILLIS) + e.hour * HOUR + e.minute * MINUTE
            }
            due = maxOf(due, last + s.minGapMinutes * MINUTE)
            dues[i] = due
            last = due
        }

        // Regulator reference, court and money.
        val lim = sk.limits
        var regulator = ""
        var regulatorRef = ""
        var court = ""
        var suitNo = ""
        var claim = 0L
        var amountDue = 0L
        var dueReason = ""
        var payBy = 0L

        if (sk.tier == "C") {
            regulator = sk.regulatorShort
            if (!REG_REF.matches(r.regulatorRef) || !r.regulatorRef.startsWith(sk.regulatorShort + "/")) return no("regulatorRef")
            regulatorRef = r.regulatorRef
        } else if (r.regulatorRef.isNotEmpty()) {
            return no("regulatorRef not allowed")
        }

        val decisionIndex = nonFixedCount - 1
        if (sk.tier == "C" && sk.regulatorDecision == "PENALTY") {
            if (r.penalty < lim.penaltyMin || r.penalty > lim.penaltyMax || r.penalty % lim.penaltyStep != 0L) return no("penalty")
            amountDue = r.penalty
            dueReason = CaseMoney.REASON_PENALTY
            payBy = dues[decisionIndex] + 30 * DAY_MILLIS
        }

        if (sk.hasCourtPath) {
            if (r.refundBaseMultiple < lim.refundBaseMultipleMin || r.refundBaseMultiple > lim.refundBaseMultipleMax) return no("refund multiple")
            if (r.damages < lim.damagesMin || r.damages > lim.damagesMax || r.damages % lim.damagesStep != 0L) return no("damages")
            if (r.costs < lim.costsMin || r.costs > lim.costsMax || r.costs % lim.costsStep != 0L) return no("costs")
            val refundBase = sk.priceNaira * r.refundBaseMultiple
            claim = CaseMoney.claim(sk.priceNaira, r.refundBaseMultiple, r.damages, r.costs)
            court = CaseMoney.courtFor(claim, lim)
            if (r.court != court) return no("court does not match the claim")
            val suitOk = if (court == CaseMoney.MAGISTRATE) SUIT_MIK.matches(r.suitNo) else SUIT_LD.matches(r.suitNo)
            if (!suitOk) return no("suit number")
            suitNo = r.suitNo

            // The court events: the app fixes the days (counted from the filing, 14 days after the decision).
            val filedAt = dues[decisionIndex] + 14 * DAY_MILLIS
            val offsets = courtDayOffsets(sk)
            var k = 0
            for (i in nonFixedCount until st.size) {
                val e = r.events[i]
                val due0 = dayStartMillis(filedAt + offsets[k] * DAY_MILLIS) + e.hour * HOUR + e.minute * MINUTE
                k++
                val due = maxOf(due0, last + 15 * MINUTE)
                dues[i] = due
                last = due
            }

            when (sk.lawsuitOutcome) {
                "SETTLED" -> {
                    if (r.settlementPercent < lim.settlementPercentMin || r.settlementPercent > lim.settlementPercentMax) return no("settlement percent")
                    amountDue = CaseMoney.settlementAmount(claim, r.settlementPercent)
                    dueReason = CaseMoney.REASON_SETTLED
                    payBy = dues[st.size - 1] + 14 * DAY_MILLIS
                }
                "CLAIMANT_WINS" -> {
                    if (r.generalDamagesPercent < lim.generalDamagesPercentMin || r.generalDamagesPercent > lim.generalDamagesPercentMax) return no("general damages percent")
                    if (r.awardedCosts < lim.awardedCostsMin || r.awardedCosts > lim.awardedCostsMax || r.awardedCosts % CaseLimits.ROUND_STEP != 0L) return no("awarded costs")
                    amountDue = CaseMoney.claimantAward(refundBase, r.damages, r.generalDamagesPercent, r.awardedCosts, claim)
                    dueReason = CaseMoney.REASON_CLAIMANT
                    payBy = dues[st.size - 1] + 30 * DAY_MILLIS
                }
                else -> {
                    amountDue = 0L
                    dueReason = ""
                    payBy = 0L
                }
            }
        } else {
            if (r.court.isNotEmpty() || r.suitNo.isNotEmpty()) return no("court not allowed")
        }

        val events = ArrayList<CaseEvent>()
        for (i in st.indices) {
            val s = st[i]
            val e = r.events[i]
            events.add(
                CaseEvent(
                    dueMillis = dues[i],
                    kind = s.kind,
                    actor = s.actor,
                    title = if (s.fixedTitle.isNotEmpty()) s.fixedTitle else e.title,
                    body = e.body,
                    statusAfter = s.status
                )
            )
        }

        val number = ledger.nextCaseNumber
        val ref = "NG-CS-" + yearOf(now) + "-" + number.toString().padStart(4, '0')
        val c = ComplaintCase(
            ref = ref,
            appId = sk.appId,
            appName = sk.appName,
            userId = sk.customerId,
            customerName = sk.customerName,
            customerEmail = sk.customerEmail,
            customerPhone = sk.customerPhone,
            plan = sk.plan,
            category = sk.category,
            subject = r.subject,
            createdMillis = now,
            events = events,
            outcome = sk.outcome,
            regulator = regulator,
            regulatorRef = regulatorRef,
            court = court,
            suitNo = suitNo,
            claimAmount = claim,
            amountDue = amountDue,
            dueReason = dueReason,
            payByMillis = payBy,
            paidMillis = 0L,
            expenseId = "",
            notes = emptyList(),
            writer = "Claude"
        )
        return Pair(c, null)
    }

    // ---------------------------------------------------------------------------------------------
    // The job that asks Claude
    // ---------------------------------------------------------------------------------------------

    /** The whole WRITE_CASE job as sent to publish_roll_jobs. The skeleton lives inside its context. */
    fun buildJob(sk: CaseSkeleton): JSONObject {
        val st = steps(sk)
        val job = JSONObject()
        job.put("dedupe_key", dedupeKey(sk))
        job.put("kind", KIND)
        job.put("app_id", sk.appId)
        job.put("day_key", dayKey(sk.createdMillis))
        job.put("priority", PRIORITY)
        job.put("sort_order", 0)
        // Level 2 drops the style sample, level 3 also drops the one-line hints. The skeleton always stays.
        var ctx = context(sk, st, 1)
        if (ctx.toString().toByteArray(Charsets.UTF_8).size > CONTEXT_LIMIT_BYTES) ctx = context(sk, st, 2)
        if (ctx.toString().toByteArray(Charsets.UTF_8).size > CONTEXT_LIMIT_BYTES) ctx = context(sk, st, 3)
        job.put("context", ctx)
        job.put("result_schema", resultSchema(sk, st))
        return job
    }

    private fun naira(v: Long): String = "₦" + java.text.NumberFormat.getIntegerInstance(Locale.US).format(v)

    private fun planWord(p: Plan): String = if (p == Plan.FREE) "Free" else if (p == Plan.PREMIUM) "Premium" else "Pro"

    private fun whatHappened(sk: CaseSkeleton): String {
        val plan = planWord(sk.plan)
        return when (sk.category) {
            CaseCategory.BILLING_RENEWAL ->
                "On " + sk.chargeDateText + " the customer's " + plan + " plan on " + sk.appName +
                    " was renewed and " + naira(sk.priceNaira) + " was charged through Google Play."
            CaseCategory.BILLING_UPGRADE ->
                "On " + sk.chargeDateText + " the customer upgraded to " + plan + " on " + sk.appName +
                    " and " + naira(sk.priceNaira) + " was charged through Google Play."
            CaseCategory.PLAN_ACCESS ->
                "The customer's " + plan + " plan on " + sk.appName + " ended on " + sk.chargeDateText +
                    " and the customer moved to the Free plan. The plan costs " + naira(sk.priceNaira) + "."
            CaseCategory.DATA_PRIVACY ->
                "The customer worries about personal data after signing up to " + sk.appName +
                    " (the app keeps name, email and phone number)."
            CaseCategory.ADS_CONTENT ->
                "The customer saw adverts in " + sk.appName + " and is unhappy about how many or how suitable they are."
        }
    }

    private fun ending(sk: CaseSkeleton): String {
        return when (sk.tier) {
            "A" -> "Support solves the case. No company money is paid." +
                (if (sk.thanksReply) " The customer writes a short thank-you." else " The customer does not reply again.")
            "B" -> if (sk.tierBBranch == "REPLY") {
                "Support asks for details, the customer sends them, then support solves the case."
            } else {
                "Support asks for details, the customer never replies, so support closes the case."
            }
            else -> {
                val base = "The customer is not satisfied and complains to " + sk.regulatorFullName + ". "
                base + when (sk.regulatorDecision) {
                    "CLOSED" -> "The regulator closes the complaint with no further action."
                    "REFUND" -> "The regulator directs that the real charge is refunded through Google Play."
                    "PENALTY" -> "The regulator imposes a penalty on the company."
                    else -> when (sk.lawsuitOutcome) {
                        "DISMISSED" -> "The matter is not resolved, the customer sues, and the court dismisses the claim. The company wins."
                        "SETTLED" -> "The matter is not resolved, the customer sues, and the parties settle out of court. The company pays part of the claim."
                        else -> "The matter is not resolved, the customer sues, and the court gives judgment for the customer. The company pays."
                    }
                }
            }
        }
    }

    private fun dayText(steps: List<CaseStep>, index: Int): String {
        val s = steps[index]
        if (index == 0) return "0, app sets it"
        if (s.appFixesDay) return "app fixes it, send 0"
        val r = dayRange(steps, index) ?: return "any"
        val base = if (r.first == r.second) r.first.toString() else r.first.toString() + " to " + r.second
        return if (s.relTo >= 0) base + " (" + s.relMin + "-" + s.relMax + " after event " + s.relTo + ")" else base
    }

    private fun templateJson(steps: List<CaseStep>, withHints: Boolean): JSONArray {
        val array = JSONArray()
        for (i in steps.indices) {
            val s = steps[i]
            val o = JSONObject()
            o.put("i", i)
            o.put("kind", s.kind.name)
            if (s.status != null) o.put("statusAfter", s.status.name)
            o.put("day", dayText(steps, i))
            if (withHints) o.put("what", s.hint)
            array.put(o)
        }
        return array
    }

    private fun limitsJson(sk: CaseSkeleton): JSONObject {
        val lim = sk.limits
        val o = JSONObject()
        if (sk.tier == "C" && sk.regulatorDecision == "PENALTY") {
            o.put("penalty", "whole naira from " + lim.penaltyMin + " to " + lim.penaltyMax + " in steps of " + lim.penaltyStep)
        }
        if (sk.hasCourtPath) {
            o.put("refundBaseMultiple", "whole number from " + lim.refundBaseMultipleMin + " to " + lim.refundBaseMultipleMax +
                " (the refund claimed is priceNaira times this)")
            o.put("damages", "whole naira from " + lim.damagesMin + " to " + lim.damagesMax + " in steps of " + lim.damagesStep)
            o.put("costs", "whole naira from " + lim.costsMin + " to " + lim.costsMax + " in steps of " + lim.costsStep)
            o.put("claim", "refund + damages + costs. Court: " + CaseMoney.MAGISTRATE + " when the claim is at most " +
                lim.magistrateMaxClaim + ", otherwise " + CaseMoney.HIGH_COURT)
            if (sk.lawsuitOutcome == "SETTLED") {
                o.put("settlementPercent", "whole number from " + lim.settlementPercentMin + " to " + lim.settlementPercentMax + " (share of the claim the company pays)")
            }
            if (sk.lawsuitOutcome == "CLAIMANT_WINS") {
                o.put("generalDamagesPercent", "whole number from " + lim.generalDamagesPercentMin + " to " + lim.generalDamagesPercentMax + " (share of damages awarded)")
                o.put("awardedCosts", "whole naira from " + lim.awardedCostsMin + " to " + lim.awardedCostsMax + " in steps of " + CaseLimits.ROUND_STEP)
            }
        }
        return o
    }

    private fun context(sk: CaseSkeleton, steps: List<CaseStep>, level: Int): JSONObject {
        val ctx = JSONObject()
        ctx.put("job", KIND)
        ctx.put("company", "Neribo Group Limited, a simulated Nigerian software and app company. Everything is fiction.")
        ctx.put("app", sk.appName)
        ctx.put("customer", JSONObject().put("name", sk.customerName).put("phone", sk.customerPhone))
        ctx.put("plan", planWord(sk.plan))
        ctx.put("priceNaira", sk.priceNaira)
        ctx.put("charge", sk.chargeDateText)
        ctx.put("category", sk.category.name)
        ctx.put("whatHappened", whatHappened(sk))
        ctx.put("agentName", sk.agentName)
        ctx.put("tier", sk.tier)
        ctx.put("ending", ending(sk))
        ctx.put("template", templateJson(steps, level < 3))
        val limits = limitsJson(sk)
        if (limits.length() > 0) ctx.put("limits", limits)
        if (sk.tier == "C") {
            ctx.put("regulator", JSONObject().put("short", sk.regulatorShort).put("fullName", sk.regulatorFullName))
            ctx.put("regulatorRefForm", sk.regulatorShort + "/CPD or CMP/year/4 digits, for example " + sk.regulatorShort + "/CMP/" + yearOf(sk.createdMillis) + "/4821")
        }
        if (sk.hasCourtPath) {
            ctx.put("suitNoForm", "MIK/3 or 4 digits/year for the Magistrate's Court, or LD/4 digits/year for the High Court")
        }
        val rules = JSONArray()
        rules.put("Write exactly " + steps.size + " events, in the order of the template. Each kind must match the template.")
        rules.put("day is the dayOffset: days from the first email (day 0). Stay inside each range and never go backwards. Use hour 9 to 17 for every event except event 0 (send 0, 9, 0). The app sets the title of hearing events.")
        if (sk.tier == "A") {
            rules.put("Event 1: if dayOffset is 0 its hour and minute are ignored (the app sends it 15 minutes to 3 hours after the first email); if 1 use hour 9 to 17.")
        }
        if (sk.hasCourtPath) {
            rules.put("Court events: the app fixes the day, so send dayOffset 0 for them, but still write hour (9 to 17), minute, a title and a body.")
        }
        rules.put("Use exactly the plan, price and date given. Do not invent plan names, amounts, regulators, courts or endings.")
        rules.put("Never write an email address, the @ sign, a link, markdown, a case number or any number of 10 or more digits.")
        rules.put("Send 0 for any number that does not apply, and an empty string for regulatorRef, court and suitNo when they do not apply. dueReason is only wording: write a short phrase or an empty string.")
        rules.put("Everything is a simulation: use fictional names and numbers only.")
        ctx.put("rules", rules)
        if (level < 2) {
            val random = Random(sk.customerId.hashCode())
            val msg = CaseText.customerEmail(sk.category, random)
            val vars = mapOf(
                "name" to sk.customerName,
                "first" to sk.customerName.trim().substringBefore(' '),
                "app" to sk.appName,
                "plan" to planWord(sk.plan),
                "amount" to naira(sk.priceNaira),
                "date" to sk.chargeDateText
            )
            ctx.put("styleSample", JSONObject().put("subject", CaseText.fill(msg.subject, vars)).put("body", CaseText.fill(msg.body, vars)))
        }
        ctx.put("skeleton", sk.toJson())
        return ctx
    }

    private fun str(min: Int, max: Int): JSONObject =
        JSONObject().put("type", "string").put("minLength", min).put("maxLength", max)

    private fun whole(min: Long, max: Long): JSONObject =
        JSONObject().put("type", "integer").put("minimum", min).put("maximum", max)

    private fun onlyEmpty(): JSONObject {
        val e = JSONArray()
        e.put("")
        return JSONObject().put("type", "string").put("enum", e)
    }

    /**
     * The result schema for this job. The server checks the shapes and the ranges; the app checks the rest.
     * The number and kinds of the events come from the template. regulatorRef, court and suitNo are tightened
     * per job so that "" is allowed exactly when they do not apply.
     */
    fun resultSchema(sk: CaseSkeleton, steps: List<CaseStep>): JSONObject {
        val kinds = JSONArray()
        for (k in steps.map { it.kind.name }.distinct()) kinds.put(k)
        val required = JSONArray()
        for (k in listOf("kind", "title", "body", "dayOffset", "hour", "minute")) required.put(k)
        val item = JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("kind", JSONObject().put("type", "string").put("enum", kinds))
                    .put("title", str(3, 140))
                    .put("body", str(40, 1600))
                    .put("dayOffset", whole(0, 365))
                    .put("hour", whole(0, 23))
                    .put("minute", whole(0, 59))
            )
            .put("required", required)
            .put("additionalProperties", false)
        val events = JSONObject()
            .put("type", "array")
            .put("minItems", steps.size)
            .put("maxItems", steps.size)
            .put("items", item)

        val regulatorRef = if (sk.tier == "C") {
            JSONObject().put("type", "string").put("pattern", "^" + sk.regulatorShort + "/(CPD|CMP)/[0-9]{4}/[0-9]{4}$")
        } else {
            onlyEmpty()
        }
        val court: JSONObject
        val suitNo: JSONObject
        if (sk.hasCourtPath) {
            val courts = JSONArray()
            courts.put(CaseMoney.MAGISTRATE)
            courts.put(CaseMoney.HIGH_COURT)
            court = JSONObject().put("type", "string").put("enum", courts)
            suitNo = JSONObject().put("type", "string").put("pattern", "^(MIK/[0-9]{3,4}/[0-9]{4}|LD/[0-9]{4}/[0-9]{4})$")
        } else {
            court = onlyEmpty()
            suitNo = onlyEmpty()
        }

        fun money(max: Long, step: Long): JSONObject =
            JSONObject().put("type", "integer").put("minimum", 0).put("maximum", max).put("multipleOf", step)

        val props = JSONObject()
            .put("subject", str(8, 120))
            .put("events", events)
            .put("regulatorRef", regulatorRef)
            .put("court", court)
            .put("suitNo", suitNo)
            .put("refundBaseMultiple", whole(0, 3))
            .put("damages", money(1_500_000L, 50_000L))
            .put("costs", money(100_000L, 5_000L))
            .put("penalty", money(1_500_000L, 10_000L))
            .put("settlementPercent", whole(0, 60))
            .put("awardedCosts", money(150_000L, 5_000L))
            .put("generalDamagesPercent", whole(0, 50))
            .put("dueReason", JSONObject().put("type", "string").put("maxLength", 120))
        val top = JSONArray()
        for (k in listOf(
            "subject", "events", "regulatorRef", "court", "suitNo", "refundBaseMultiple", "damages", "costs",
            "penalty", "settlementPercent", "awardedCosts", "generalDamagesPercent", "dueReason"
        )) top.put(k)
        return JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", top)
            .put("additionalProperties", false)
    }
}
