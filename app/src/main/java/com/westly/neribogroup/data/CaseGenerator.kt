package com.westly.neribogroup.data

import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/** What one daily roll really did. Cases can only be born from these facts. */
data class RollFacts(
    /** Users the orange dice renewed (a real renewal charge). */
    val renewedUsers: List<AppUser> = emptyList(),
    /** Users the red dice moved to Free, as they were before (with their old plan and end date). */
    val lapsedUsers: List<AppUser> = emptyList(),
    /** Users who upgraded to Premium or Pro in this roll, as they are now. */
    val upgradedUsers: List<AppUser> = emptyList(),
    val newUserCount: Int = 0,
    /** True when the gold dice earned ad money in this roll. */
    val adsEarned: Boolean = false
)

/** The limits inside which a case story may choose its amounts. They are the numbers the generator has always used. */
data class CaseLimits(
    val refundBaseMultipleMin: Int, val refundBaseMultipleMax: Int,
    val damagesMin: Long, val damagesMax: Long, val damagesStep: Long,
    val costsMin: Long, val costsMax: Long, val costsStep: Long,
    val penaltyMin: Long, val penaltyMax: Long, val penaltyStep: Long,
    val settlementPercentMin: Int, val settlementPercentMax: Int,
    val awardedCostsMin: Long, val awardedCostsMax: Long,
    val generalDamagesPercentMin: Int, val generalDamagesPercentMax: Int,
    val magistrateMaxClaim: Long
) {
    fun toJson(): JSONObject = JSONObject()
        .put("refundBaseMultipleMin", refundBaseMultipleMin).put("refundBaseMultipleMax", refundBaseMultipleMax)
        .put("damagesMin", damagesMin).put("damagesMax", damagesMax).put("damagesStep", damagesStep)
        .put("costsMin", costsMin).put("costsMax", costsMax).put("costsStep", costsStep)
        .put("penaltyMin", penaltyMin).put("penaltyMax", penaltyMax).put("penaltyStep", penaltyStep)
        .put("settlementPercentMin", settlementPercentMin).put("settlementPercentMax", settlementPercentMax)
        .put("awardedCostsMin", awardedCostsMin).put("awardedCostsMax", awardedCostsMax)
        .put("generalDamagesPercentMin", generalDamagesPercentMin).put("generalDamagesPercentMax", generalDamagesPercentMax)
        .put("magistrateMaxClaim", magistrateMaxClaim)

    companion object {
        /** Money step for settlement and general damages rounding (whole naira). */
        const val ROUND_STEP = 5_000L

        val STANDARD = CaseLimits(
            refundBaseMultipleMin = 1, refundBaseMultipleMax = 3,
            damagesMin = 100_000L, damagesMax = 1_500_000L, damagesStep = 50_000L,
            costsMin = 20_000L, costsMax = 100_000L, costsStep = 5_000L,
            penaltyMin = 150_000L, penaltyMax = 1_500_000L, penaltyStep = 10_000L,
            settlementPercentMin = 25, settlementPercentMax = 60,
            awardedCostsMin = 30_000L, awardedCostsMax = 150_000L,
            generalDamagesPercentMin = 15, generalDamagesPercentMax = 50,
            magistrateMaxClaim = 500_000L
        )

        fun fromJson(o: JSONObject?): CaseLimits? {
            if (o == null) return null
            return try {
                CaseLimits(
                    o.getInt("refundBaseMultipleMin"), o.getInt("refundBaseMultipleMax"),
                    o.getLong("damagesMin"), o.getLong("damagesMax"), o.getLong("damagesStep"),
                    o.getLong("costsMin"), o.getLong("costsMax"), o.getLong("costsStep"),
                    o.getLong("penaltyMin"), o.getLong("penaltyMax"), o.getLong("penaltyStep"),
                    o.getInt("settlementPercentMin"), o.getInt("settlementPercentMax"),
                    o.getLong("awardedCostsMin"), o.getLong("awardedCostsMax"),
                    o.getInt("generalDamagesPercentMin"), o.getInt("generalDamagesPercentMax"),
                    o.getLong("magistrateMaxClaim")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Everything the app decides about a complaint BEFORE any story is written: whether it happens, the category,
 * the customer, the real charge, the tier, the outcome and every limit. A story (from Claude or from the
 * built-in texts) can only fill in words, dates and amounts inside these limits.
 */
data class CaseSkeleton(
    val appId: String,
    val appName: String,
    val customerId: String,
    val customerName: String,
    val customerEmail: String,
    val customerPhone: String,
    val plan: Plan,
    val category: CaseCategory,
    val chargeMillis: Long,
    /** "d MMMM yyyy" */
    val chargeDateText: String,
    /** The real price of that plan in this app (0 for Free). */
    val priceNaira: Long,
    val agentName: String,
    /** "A" | "B" | "C" */
    val tier: String,
    /** "REPLY" or "NO_REPLY" (tier B only, else ""). */
    val tierBBranch: String,
    /** Tier A only: whether the customer writes a thank-you after the resolution. */
    val thanksReply: Boolean,
    /** "" unless tier C. */
    val regulatorShort: String,
    val regulatorFullName: String,
    /** "" | "CLOSED" | "REFUND" | "PENALTY" | "UNRESOLVED" */
    val regulatorDecision: String,
    /** "" | "DISMISSED" | "SETTLED" | "CLAIMANT_WINS" */
    val lawsuitOutcome: String,
    /** 0..2, only when there is a lawsuit. */
    val adjournments: Int,
    val outcome: CaseOutcome,
    val limits: CaseLimits,
    /** The protected "now" of the roll that decided this skeleton (used for the 72-hour wait). */
    val createdMillis: Long = 0L
) {
    /** True when the story ends in a lawsuit. */
    val hasCourtPath: Boolean get() = tier == "C" && regulatorDecision == "UNRESOLVED"

    fun toJson(): JSONObject = JSONObject()
        .put("appId", appId).put("appName", appName)
        .put("customerId", customerId).put("customerName", customerName)
        .put("customerEmail", customerEmail).put("customerPhone", customerPhone)
        .put("plan", plan.name).put("category", category.name)
        .put("chargeMillis", chargeMillis).put("chargeDateText", chargeDateText)
        .put("priceNaira", priceNaira).put("agentName", agentName)
        .put("tier", tier).put("tierBBranch", tierBBranch).put("thanksReply", thanksReply)
        .put("regulatorShort", regulatorShort).put("regulatorFullName", regulatorFullName)
        .put("regulatorDecision", regulatorDecision).put("lawsuitOutcome", lawsuitOutcome)
        .put("adjournments", adjournments).put("outcome", outcome.name)
        .put("limits", limits.toJson()).put("createdMillis", createdMillis)

    companion object {
        /** Reads a skeleton back. Returns null when anything is missing or not valid. */
        fun fromJson(o: JSONObject?): CaseSkeleton? {
            if (o == null) return null
            return try {
                val tier = o.getString("tier")
                if (tier != "A" && tier != "B" && tier != "C") return null
                val limits = CaseLimits.fromJson(o.optJSONObject("limits")) ?: return null
                CaseSkeleton(
                    appId = o.getString("appId"),
                    appName = o.getString("appName"),
                    customerId = o.getString("customerId"),
                    customerName = o.getString("customerName"),
                    customerEmail = o.getString("customerEmail"),
                    customerPhone = o.getString("customerPhone"),
                    plan = Plan.valueOf(o.getString("plan")),
                    category = CaseCategory.valueOf(o.getString("category")),
                    chargeMillis = o.getLong("chargeMillis"),
                    chargeDateText = o.getString("chargeDateText"),
                    priceNaira = o.getLong("priceNaira"),
                    agentName = o.getString("agentName"),
                    tier = tier,
                    tierBBranch = o.getString("tierBBranch"),
                    thanksReply = o.getBoolean("thanksReply"),
                    regulatorShort = o.getString("regulatorShort"),
                    regulatorFullName = o.getString("regulatorFullName"),
                    regulatorDecision = o.getString("regulatorDecision"),
                    lawsuitOutcome = o.getString("lawsuitOutcome"),
                    adjournments = o.getInt("adjournments"),
                    outcome = CaseOutcome.valueOf(o.getString("outcome")),
                    limits = limits,
                    createdMillis = o.optLong("createdMillis", 0L)
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Creates customer service cases. The whole timeline, the outcome and every amount are decided ONCE
 * here and stored. Nothing is re-rolled when a case is shown. All of it is a simulation.
 */
object CaseGenerator {

    private const val MIN_DICE_USERS = 20
    private const val MIN_ROLLS = 14
    private const val MIN_DAYS_BETWEEN = 7L
    private const val MAX_ACTIVE_PER_APP = 2
    private const val HOUR = 3_600_000L
    private const val MINUTE = 60_000L

    fun regulatorFullName(short: String): String = when (short) {
        "FCCPC" -> "Federal Competition and Consumer Protection Commission (FCCPC), Abuja"
        "NDPC" -> "Nigeria Data Protection Commission (NDPC), Abuja"
        "ARCON" -> "Advertising Regulatory Council of Nigeria (ARCON), Lagos"
        else -> short
    }

    private fun regulatorShort(category: CaseCategory): String = when (category) {
        CaseCategory.DATA_PRIVACY -> "NDPC"
        CaseCategory.ADS_CONTENT -> "ARCON"
        else -> "FCCPC"
    }

    private fun naira(v: Long): String = "₦" + NumberFormat.getIntegerInstance(Locale.US).format(v)

    private fun longDate(millis: Long): String =
        SimpleDateFormat("d MMMM yyyy", Locale.ENGLISH).format(Date(millis))

    private fun yearOf(millis: Long): String = SimpleDateFormat("yyyy", Locale.US).format(Date(millis))

    private fun roundTo(value: Long, step: Long): Long = ((value + step / 2L) / step) * step

    private fun priceOf(app: AppData, plan: Plan): Long = when (plan) {
        Plan.PREMIUM -> app.premiumPrice
        Plan.PRO -> app.proPrice
        Plan.FREE -> 0L
    }

    private fun triggered(count: Int, probability: Double, random: Random): Boolean {
        if (count <= 0) return false
        val chance = 1.0 - Math.pow(1.0 - probability, count.toDouble())
        return random.nextDouble() < chance
    }

    /**
     * Called at the end of a Google Play app's daily roll. [ledger] already holds the roll's result.
     * Returns the ledger with at most one new case added, or unchanged.
     * Phase 5: this is [decideSkeleton] followed by [buildFromTemplates].
     */
    fun maybeCreate(ledger: Ledger, appId: String, facts: RollFacts, now: Long, random: Random): Ledger {
        val skeleton = decideSkeleton(ledger, appId, facts, now, random) ?: return ledger
        val c = buildFromTemplates(ledger, skeleton, now, random)
        return ledger.withCaseAdded(c, now)
    }

    /**
     * Step 1: the honest random part. Whether a case happens (the gates and probabilities), the category, the
     * customer, the real charge, the tier, the regulator decision, the lawsuit outcome and the limits.
     * Returns null when no case happens. Nothing is added to the ledger here.
     */
    fun decideSkeleton(ledger: Ledger, appId: String, facts: RollFacts, now: Long, random: Random): CaseSkeleton? {
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return null
        if (!app.googlePlayEnabled) return null
        if (app.users.count { it.simulated } < MIN_DICE_USERS) return null
        if (app.rolls.size < MIN_ROLLS) return null

        val effectiveNow = maxOf(now, ledger.casesClockMillis)
        val appCases = ledger.cases.filter { it.appId == app.id }
        val active = appCases.count { !CaseEngine.isFinal(CaseEngine.statusAt(it, effectiveNow)) }
        if (active >= MAX_ACTIVE_PER_APP) return null
        val lastCreated = appCases.maxOfOrNull { it.createdMillis }
        if (lastCreated != null && now - lastCreated < MIN_DAYS_BETWEEN * DAY_MILLIS) return null

        val hits = ArrayList<CaseCategory>()
        if (triggered(facts.renewedUsers.size, 0.0015, random)) hits.add(CaseCategory.BILLING_RENEWAL)
        if (triggered(facts.upgradedUsers.size, 0.0010, random)) hits.add(CaseCategory.BILLING_UPGRADE)
        if (triggered(facts.lapsedUsers.size, 0.0010, random)) hits.add(CaseCategory.PLAN_ACCESS)
        if (triggered(facts.newUserCount, 0.0001, random)) hits.add(CaseCategory.DATA_PRIVACY)
        if (facts.adsEarned && random.nextDouble() < 0.004) hits.add(CaseCategory.ADS_CONTENT)
        if (hits.isEmpty()) return null

        val category = hits[random.nextInt(hits.size)]
        val diceUsers = app.users.filter { it.simulated }
        if (diceUsers.isEmpty()) return null

        // The customer and the real charge behind the complaint.
        val customer: AppUser
        val chargeMillis: Long
        when (category) {
            CaseCategory.BILLING_RENEWAL -> {
                customer = facts.renewedUsers[random.nextInt(facts.renewedUsers.size)]
                chargeMillis = now
            }
            CaseCategory.BILLING_UPGRADE -> {
                customer = facts.upgradedUsers[random.nextInt(facts.upgradedUsers.size)]
                chargeMillis = if (customer.planStartMillis > 0L) customer.planStartMillis else now
            }
            CaseCategory.PLAN_ACCESS -> {
                customer = facts.lapsedUsers[random.nextInt(facts.lapsedUsers.size)]
                chargeMillis = if (customer.planExpiryMillis > 0L) customer.planExpiryMillis else now
            }
            else -> {
                customer = diceUsers[random.nextInt(diceUsers.size)]
                chargeMillis = now
            }
        }

        val agent = CaseText.AGENTS[random.nextInt(CaseText.AGENTS.size)]
        val regShort = regulatorShort(category)

        var tier = "A"
        var branch = ""
        var thanks = false
        var regulatorDecision = ""
        var lawsuit = ""
        var adjournments = 0
        var outcome = CaseOutcome.RESOLVED_BY_SUPPORT

        val roll = random.nextInt(100)
        when {
            roll < 85 -> {
                tier = "A"
                thanks = random.nextInt(100) < 60
            }
            roll < 96 -> {
                tier = "B"
                branch = if (random.nextInt(100) < 80) "REPLY" else "NO_REPLY"
            }
            else -> {
                tier = "C"
                val pick = random.nextInt(100)
                // Penalty (5 points) is only for data privacy. For every other category it becomes "Closed".
                regulatorDecision = when {
                    pick < 55 -> "CLOSED"
                    pick < 70 -> "REFUND"
                    pick < 75 -> if (category == CaseCategory.DATA_PRIVACY) "PENALTY" else "CLOSED"
                    else -> "UNRESOLVED"
                }
                when (regulatorDecision) {
                    "CLOSED" -> outcome = CaseOutcome.REGULATOR_CLOSED
                    "REFUND" -> outcome = CaseOutcome.REGULATOR_REFUND
                    "PENALTY" -> outcome = CaseOutcome.REGULATOR_PENALTY
                    else -> {
                        val adjRoll = random.nextInt(100)
                        adjournments = if (adjRoll < 40) 0 else if (adjRoll < 80) 1 else 2
                        val outRoll = random.nextInt(100)
                        if (outRoll < 45) {
                            lawsuit = "DISMISSED"
                            outcome = CaseOutcome.COMPANY_WON
                        } else if (outRoll < 65) {
                            lawsuit = "SETTLED"
                            outcome = CaseOutcome.SETTLED
                        } else {
                            lawsuit = "CLAIMANT_WINS"
                            outcome = CaseOutcome.CUSTOMER_WON
                        }
                    }
                }
            }
        }

        return CaseSkeleton(
            appId = app.id,
            appName = app.name,
            customerId = customer.id,
            customerName = customer.name,
            customerEmail = customer.email,
            customerPhone = customer.phone,
            plan = customer.plan,
            category = category,
            chargeMillis = chargeMillis,
            chargeDateText = longDate(chargeMillis),
            priceNaira = priceOf(app, customer.plan),
            agentName = agent,
            tier = tier,
            tierBBranch = branch,
            thanksReply = thanks,
            regulatorShort = if (tier == "C") regShort else "",
            regulatorFullName = if (tier == "C") regulatorFullName(regShort) else "",
            regulatorDecision = regulatorDecision,
            lawsuitOutcome = lawsuit,
            adjournments = adjournments,
            outcome = outcome,
            limits = CaseLimits.STANDARD,
            createdMillis = now
        )
    }

    /**
     * Step 2 (AI): builds the case from Claude's story. Returns null when anything in the answer breaks a rule,
     * so the caller can fall back to [buildFromTemplates]. Nothing is added to the ledger here.
     */
    fun buildFromAi(ledger: Ledger, skeleton: CaseSkeleton, result: AiCaseResult, now: Long): ComplaintCase? =
        CaseAi.assemble(ledger, skeleton, result, now)

    // ---------------------------------------------------------------------------------------
    // Building one case from the built-in texts
    // ---------------------------------------------------------------------------------------

    private class Timeline(private val start: Long, private val random: Random) {
        val events = ArrayList<CaseEvent>()
        private var last = start

        /** A time between 09:00 and 17:00 on day [day] after the first email, never before the last event. */
        fun atDay(day: Int): Long {
            val base = dayStartMillis(start + day * DAY_MILLIS) + 9 * HOUR +
                random.nextLong(0L, 8 * HOUR)
            return maxOf(base, last + 30 * MINUTE)
        }

        fun add(
            dueMillis: Long,
            kind: CaseEventKind,
            actor: CaseActor,
            title: String,
            body: String,
            status: CaseStatus? = null
        ): Long {
            val due = maxOf(dueMillis, last)
            events.add(CaseEvent(dueMillis = due, kind = kind, actor = actor, title = title, body = body, statusAfter = status))
            last = due
            return due
        }

        fun addOnDay(
            day: Int, kind: CaseEventKind, actor: CaseActor, title: String, body: String,
            status: CaseStatus? = null
        ): Long = add(atDay(day), kind, actor, title, body, status)

        fun sameDay(
            kind: CaseEventKind, actor: CaseActor, title: String, body: String, status: CaseStatus? = null
        ): Long = add(last + random.nextLong(15L * MINUTE, 3 * HOUR), kind, actor, title, body, status)
    }

    /**
     * Step 2 (built-in): builds the whole case from the skeleton with the texts of [CaseText] and [CaseCourtText].
     * The skeleton decides the tier, the decision and the outcome. The random only picks wording, times and the
     * amounts inside the skeleton's limits.
     */
    fun buildFromTemplates(ledger: Ledger, skeleton: CaseSkeleton, now: Long, random: Random): ComplaintCase {
        val number = ledger.nextCaseNumber
        val ref = "NG-CS-" + yearOf(now) + "-" + number.toString().padStart(4, '0')
        val category = skeleton.category
        val price = skeleton.priceNaira
        val first = skeleton.customerName.trim().substringBefore(' ')
        val regShort = regulatorShort(category)
        val vars = hashMapOf(
            "name" to skeleton.customerName,
            "first" to first,
            "app" to skeleton.appName,
            "plan" to (if (skeleton.plan == Plan.FREE) "Free" else if (skeleton.plan == Plan.PREMIUM) "Premium" else "Pro"),
            "amount" to naira(price),
            "date" to skeleton.chargeDateText,
            "ref" to ref,
            "agent" to skeleton.agentName,
            "regulator" to regulatorFullName(regShort)
        )
        fun f(t: String): String = CaseText.fill(t, vars)

        val email = CaseText.customerEmail(category, random)
        val subject = f(email.subject)
        val reSubject = "Re: $subject [$ref]"

        val tl = Timeline(now, random)
        tl.add(now, CaseEventKind.CUSTOMER_EMAIL, CaseActor.CUSTOMER, subject, f(email.body), CaseStatus.OPEN)

        var outcome = skeleton.outcome
        var regulator = ""
        var regulatorRef = ""
        var court = ""
        var suitNo = ""
        var claimAmount = 0L
        var amountDue = 0L
        var dueReason = ""
        var payBy = 0L

        when (skeleton.tier) {
            "A" -> {
                // Tier A: resolved by support.
                if (random.nextInt(100) < 70) {
                    tl.sameDay(CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, reSubject, f(CaseText.acknowledgement(random)))
                } else {
                    tl.addOnDay(1, CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, reSubject, f(CaseText.acknowledgement(random)))
                }
                val res = CaseText.resolution(category, random)
                val resDay = random.nextInt(2, 7)
                val resDue = tl.addOnDay(resDay, CaseEventKind.RESOLUTION, CaseActor.SUPPORT, f(res.subject), f(res.body), CaseStatus.RESOLVED)
                if (skeleton.thanksReply) {
                    tl.add(
                        resDue + random.nextLong(DAY_MILLIS, 2 * DAY_MILLIS),
                        CaseEventKind.CUSTOMER_REPLY, CaseActor.CUSTOMER, "Re: $subject", f(CaseText.thanks(random))
                    )
                }
            }
            "B" -> {
                // Tier B: pending, then resolved.
                tl.addOnDay(random.nextInt(1, 3), CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, reSubject,
                    f(CaseText.infoRequest(category, random)), CaseStatus.PENDING)
                if (skeleton.tierBBranch == "REPLY") {
                    tl.addOnDay(random.nextInt(3, 6), CaseEventKind.CUSTOMER_REPLY, CaseActor.CUSTOMER, "Re: $subject", f(CaseText.details(random)))
                    val res = CaseText.resolution(category, random)
                    tl.addOnDay(random.nextInt(8, 21), CaseEventKind.RESOLUTION, CaseActor.SUPPORT, f(res.subject), f(res.body), CaseStatus.RESOLVED)
                } else {
                    tl.addOnDay(14, CaseEventKind.RESOLUTION, CaseActor.SUPPORT, "Case closed: no reply", f(CaseText.closedNoReply(random)), CaseStatus.RESOLVED)
                }
            }
            else -> {
                // Tier C: escalated to a regulator.
                regulator = regShort
                tl.addOnDay(1, CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, reSubject, f(CaseText.acknowledgement(random)), CaseStatus.OPEN)
                val offerDay = random.nextInt(4, 7)
                val offerDue = tl.addOnDay(offerDay, CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, reSubject, f(CaseText.offer(random)), CaseStatus.PENDING)
                tl.add(offerDue + random.nextLong(2 * HOUR, DAY_MILLIS), CaseEventKind.CUSTOMER_REPLY, CaseActor.CUSTOMER, "Re: $subject", f(CaseText.rejection(random)))
                val day = random.nextInt(10, 26)
                val escDue = tl.addOnDay(day, CaseEventKind.CUSTOMER_ESCALATION, CaseActor.CUSTOMER, "Complaint to the regulator: $subject", f(CaseText.escalation(random)), CaseStatus.ESCALATED)

                regulatorRef = regShort + "/" + (if (category == CaseCategory.DATA_PRIVACY) "CMP" else if (regShort == "FCCPC") "CPD" else "CMP") +
                    "/" + yearOf(now) + "/" + random.nextInt(1000, 10000)
                vars["regref"] = regulatorRef

                val letterDue = tl.add(escDue + random.nextLong(3 * DAY_MILLIS, 7 * DAY_MILLIS) , CaseEventKind.REGULATOR_LETTER, CaseActor.REGULATOR,
                    "Acknowledgement of complaint " + regulatorRef, f(CaseCourtText.regulatorLetter(random)), CaseStatus.ESCALATED)
                tl.add(letterDue + random.nextLong(DAY_MILLIS, 3 * DAY_MILLIS), CaseEventKind.COMPANY_RESPONSE, CaseActor.COMPANY,
                    "Response to " + regulatorRef, f(CaseCourtText.companyResponse(random)), CaseStatus.ESCALATED)
                val decisionDue0 = letterDue + random.nextLong(21 * DAY_MILLIS, 45 * DAY_MILLIS)

                when (skeleton.regulatorDecision) {
                    "CLOSED" -> {
                        tl.add(decisionDue0, CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, "Decision: complaint closed", f(CaseCourtText.decisionClosed(random)), CaseStatus.RESOLVED)
                    }
                    "REFUND" -> {
                        tl.add(decisionDue0, CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, "Decision: refund directed", f(CaseCourtText.decisionRefund(random)), CaseStatus.RESOLVED)
                    }
                    "PENALTY" -> {
                        val lim = skeleton.limits
                        amountDue = roundTo(random.nextLong(lim.penaltyMin, lim.penaltyMax + 1L), lim.penaltyStep)
                        payBy = decisionDue0 + 30 * DAY_MILLIS
                        dueReason = "Regulatory penalty — NDPC"
                        vars["amount"] = naira(amountDue)
                        vars["payby"] = longDate(payBy)
                        tl.add(decisionDue0, CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, "Decision: penalty imposed", f(CaseCourtText.decisionPenalty(random)), CaseStatus.PAYMENT_DUE)
                    }
                    else -> {
                        tl.add(decisionDue0, CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, "Decision: not resolved", f(CaseCourtText.decisionUnresolved(random)), CaseStatus.ESCALATED)
                        val courtInfo = addCourtPath(tl, vars, skeleton, now, decisionDue0, random)
                        court = courtInfo.court
                        suitNo = courtInfo.suitNo
                        claimAmount = courtInfo.claim
                        outcome = courtInfo.outcome
                        amountDue = courtInfo.amountDue
                        dueReason = courtInfo.reason
                        payBy = courtInfo.payBy
                    }
                }
            }
        }

        return ComplaintCase(
            ref = ref,
            appId = skeleton.appId,
            appName = skeleton.appName,
            userId = skeleton.customerId,
            customerName = skeleton.customerName,
            customerEmail = skeleton.customerEmail,
            customerPhone = skeleton.customerPhone,
            plan = skeleton.plan,
            category = category,
            subject = subject,
            createdMillis = now,
            events = tl.events.toList(),
            outcome = outcome,
            regulator = regulator,
            regulatorRef = regulatorRef,
            court = court,
            suitNo = suitNo,
            claimAmount = claimAmount,
            amountDue = amountDue,
            dueReason = dueReason,
            payByMillis = payBy,
            paidMillis = 0L,
            expenseId = "",
            notes = emptyList(),
            writer = "App"
        )
    }

    private class CourtInfo(
        val court: String,
        val suitNo: String,
        val claim: Long,
        val outcome: CaseOutcome,
        val amountDue: Long,
        val reason: String,
        val payBy: Long
    )

    /** Adds the lawsuit events to [tl] (court days are compressed to about a third of real time). */
    private fun addCourtPath(
        tl: Timeline,
        vars: HashMap<String, String>,
        skeleton: CaseSkeleton,
        now: Long,
        afterMillis: Long,
        random: Random
    ): CourtInfo {
        val lim = skeleton.limits
        val price = skeleton.priceNaira
        val refundBase = price * random.nextInt(lim.refundBaseMultipleMin, lim.refundBaseMultipleMax + 1)
        val damages = roundTo(random.nextLong(lim.damagesMin, lim.damagesMax + 1L), lim.damagesStep)
        val costs = roundTo(random.nextLong(lim.costsMin, lim.costsMax + 1L), lim.costsStep)
        val claim = refundBase + damages + costs

        val year = yearOf(afterMillis)
        val court: String
        val suitNo: String
        if (claim <= lim.magistrateMaxClaim) {
            court = "Magistrate's Court, Ikeja, Lagos"
            suitNo = "MIK/" + random.nextInt(100, 10000) + "/" + year
        } else {
            court = "High Court of Lagos State, Ikeja"
            suitNo = "LD/" + random.nextInt(1000, 10000) + "/" + year
        }
        vars["court"] = court
        vars["suit"] = suitNo
        vars["claim"] = naira(claim)
        fun f(t: String): String = CaseText.fill(t, vars)

        val filedAt = afterMillis + random.nextLong(7 * DAY_MILLIS, 21 * DAY_MILLIS)
        fun at(offsetDays: Int): Long =
            dayStartMillis(filedAt + offsetDays * DAY_MILLIS) + 9 * HOUR + random.nextLong(0L, 6 * HOUR)

        tl.add(at(0), CaseEventKind.COURT_FILED, CaseActor.COURT, "Suit filed: $suitNo", f(CaseCourtText.filed(random)), CaseStatus.IN_COURT)
        tl.add(at(7), CaseEventKind.COURT_SERVICE, CaseActor.COURT, "Company served", f(CaseCourtText.service(random)))
        var day = 35
        tl.add(at(day), CaseEventKind.COURT_HEARING, CaseActor.COURT, "First hearing", f(CaseCourtText.firstHearing(random)))

        repeat(skeleton.adjournments) {
            day += 28
            tl.add(at(day), CaseEventKind.COURT_ADJOURNED, CaseActor.COURT, "Adjourned", f(CaseCourtText.adjourned(random)))
        }
        day += 28
        tl.add(at(day), CaseEventKind.COURT_HEARING, CaseActor.COURT, "Trial and final addresses", f(CaseCourtText.finalAddresses(random)))
        day += 21
        tl.add(at(day), CaseEventKind.COURT_HEARING, CaseActor.COURT, "Judgment reserved", f(CaseCourtText.reserved(random)))
        val judgmentDay = day + 35

        return when (skeleton.lawsuitOutcome) {
            "DISMISSED" -> {
                tl.add(at(judgmentDay), CaseEventKind.COURT_JUDGMENT, CaseActor.COURT, "Judgment: claim dismissed", f(CaseCourtText.judgmentDismissed(random)), CaseStatus.CLOSED)
                CourtInfo(court, suitNo, claim, CaseOutcome.COMPANY_WON, 0L, "", 0L)
            }
            "SETTLED" -> {
                val settlementDay = judgmentDay - random.nextInt(14, 22)
                val settleAt = at(settlementDay)
                val percent = random.nextInt(lim.settlementPercentMin, lim.settlementPercentMax + 1)
                val award = CaseMoney.settlementAmount(claim, percent)
                val payBy = settleAt + 14 * DAY_MILLIS
                vars["award"] = naira(award)
                vars["payby"] = longDate(payBy)
                tl.add(settleAt, CaseEventKind.SETTLEMENT, CaseActor.COURT, "Settlement agreed", f(CaseCourtText.settlement(random)), CaseStatus.PAYMENT_DUE)
                CourtInfo(court, suitNo, claim, CaseOutcome.SETTLED, award, CaseMoney.REASON_SETTLED, payBy)
            }
            else -> {
                val percent = random.nextInt(lim.generalDamagesPercentMin, lim.generalDamagesPercentMax + 1)
                val awardedCosts = roundTo(random.nextLong(lim.awardedCostsMin, lim.awardedCostsMax + 1L), CaseLimits.ROUND_STEP)
                val award = CaseMoney.claimantAward(refundBase, damages, percent, awardedCosts, claim)
                val judgmentAt = at(judgmentDay)
                val payBy = judgmentAt + 30 * DAY_MILLIS
                vars["award"] = naira(award)
                vars["payby"] = longDate(payBy)
                tl.add(judgmentAt, CaseEventKind.COURT_JUDGMENT, CaseActor.COURT, "Judgment for the claimant", f(CaseCourtText.judgmentClaimant(random)), CaseStatus.PAYMENT_DUE)
                CourtInfo(court, suitNo, claim, CaseOutcome.CUSTOMER_WON, award, CaseMoney.REASON_CLAIMANT, payBy)
            }
        }
    }
}
