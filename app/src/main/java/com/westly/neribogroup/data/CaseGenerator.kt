package com.westly.neribogroup.data

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
     */
    fun maybeCreate(ledger: Ledger, appId: String, facts: RollFacts, now: Long, random: Random): Ledger {
        val app = ledger.apps.firstOrNull { it.id == appId } ?: return ledger
        if (!app.googlePlayEnabled) return ledger
        if (app.users.count { it.simulated } < MIN_DICE_USERS) return ledger
        if (app.rolls.size < MIN_ROLLS) return ledger

        val effectiveNow = maxOf(now, ledger.casesClockMillis)
        val appCases = ledger.cases.filter { it.appId == app.id }
        val active = appCases.count { !CaseEngine.isFinal(CaseEngine.statusAt(it, effectiveNow)) }
        if (active >= MAX_ACTIVE_PER_APP) return ledger
        val lastCreated = appCases.maxOfOrNull { it.createdMillis }
        if (lastCreated != null && now - lastCreated < MIN_DAYS_BETWEEN * DAY_MILLIS) return ledger

        val hits = ArrayList<CaseCategory>()
        if (triggered(facts.renewedUsers.size, 0.0015, random)) hits.add(CaseCategory.BILLING_RENEWAL)
        if (triggered(facts.upgradedUsers.size, 0.0010, random)) hits.add(CaseCategory.BILLING_UPGRADE)
        if (triggered(facts.lapsedUsers.size, 0.0010, random)) hits.add(CaseCategory.PLAN_ACCESS)
        if (triggered(facts.newUserCount, 0.0001, random)) hits.add(CaseCategory.DATA_PRIVACY)
        if (facts.adsEarned && random.nextDouble() < 0.004) hits.add(CaseCategory.ADS_CONTENT)
        if (hits.isEmpty()) return ledger

        val category = hits[random.nextInt(hits.size)]
        val diceUsers = app.users.filter { it.simulated }
        if (diceUsers.isEmpty()) return ledger

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

        val c = build(ledger, app, customer, chargeMillis, category, now, random)
        return ledger.withCaseAdded(c, now)
    }

    // ---------------------------------------------------------------------------------------
    // Building one case
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

    private fun build(
        ledger: Ledger,
        app: AppData,
        customer: AppUser,
        chargeMillis: Long,
        category: CaseCategory,
        now: Long,
        random: Random
    ): ComplaintCase {
        val number = ledger.nextCaseNumber
        val ref = "NG-CS-" + yearOf(now) + "-" + number.toString().padStart(4, '0')
        val price = priceOf(app, customer.plan)
        val first = customer.name.trim().substringBefore(' ')
        val agent = CaseText.AGENTS[random.nextInt(CaseText.AGENTS.size)]
        val regShort = regulatorShort(category)
        val vars = hashMapOf(
            "name" to customer.name,
            "first" to first,
            "app" to app.name,
            "plan" to (if (customer.plan == Plan.FREE) "Free" else if (customer.plan == Plan.PREMIUM) "Premium" else "Pro"),
            "amount" to naira(price),
            "date" to longDate(chargeMillis),
            "ref" to ref,
            "agent" to agent,
            "regulator" to regulatorFullName(regShort)
        )
        fun f(t: String): String = CaseText.fill(t, vars)

        val email = CaseText.customerEmail(category, random)
        val subject = f(email.subject)
        val reSubject = "Re: $subject [$ref]"

        val tl = Timeline(now, random)
        tl.add(now, CaseEventKind.CUSTOMER_EMAIL, CaseActor.CUSTOMER, subject, f(email.body), CaseStatus.OPEN)

        var outcome = CaseOutcome.RESOLVED_BY_SUPPORT
        var regulator = ""
        var regulatorRef = ""
        var court = ""
        var suitNo = ""
        var claimAmount = 0L
        var amountDue = 0L
        var dueReason = ""
        var payBy = 0L

        val roll = random.nextInt(100)
        when {
            roll < 85 -> {
                // Tier A: resolved by support.
                if (random.nextInt(100) < 70) {
                    tl.sameDay(CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, reSubject, f(CaseText.acknowledgement(random)))
                } else {
                    tl.addOnDay(1, CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, reSubject, f(CaseText.acknowledgement(random)))
                }
                val res = CaseText.resolution(category, random)
                val resDay = random.nextInt(2, 7)
                val resDue = tl.addOnDay(resDay, CaseEventKind.RESOLUTION, CaseActor.SUPPORT, f(res.subject), f(res.body), CaseStatus.RESOLVED)
                if (random.nextInt(100) < 60) {
                    tl.add(
                        resDue + random.nextLong(DAY_MILLIS, 2 * DAY_MILLIS),
                        CaseEventKind.CUSTOMER_REPLY, CaseActor.CUSTOMER, "Re: $subject", f(CaseText.thanks(random))
                    )
                }
            }
            roll < 96 -> {
                // Tier B: pending, then resolved.
                tl.addOnDay(random.nextInt(1, 3), CaseEventKind.SUPPORT_REPLY, CaseActor.SUPPORT, reSubject,
                    f(CaseText.infoRequest(category, random)), CaseStatus.PENDING)
                if (random.nextInt(100) < 80) {
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

                val pick = random.nextInt(100)
                // Penalty (5 points) is only for data privacy. For every other category it becomes "Closed".
                val decision = when {
                    pick < 55 -> 0
                    pick < 70 -> 1
                    pick < 75 -> if (category == CaseCategory.DATA_PRIVACY) 2 else 0
                    else -> 3
                }
                when (decision) {
                    0 -> {
                        outcome = CaseOutcome.REGULATOR_CLOSED
                        tl.add(decisionDue0, CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, "Decision: complaint closed", f(CaseCourtText.decisionClosed(random)), CaseStatus.RESOLVED)
                    }
                    1 -> {
                        outcome = CaseOutcome.REGULATOR_REFUND
                        tl.add(decisionDue0, CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, "Decision: refund directed", f(CaseCourtText.decisionRefund(random)), CaseStatus.RESOLVED)
                    }
                    2 -> {
                        outcome = CaseOutcome.REGULATOR_PENALTY
                        amountDue = roundTo(random.nextLong(150_000L, 1_500_001L), 10_000L)
                        payBy = decisionDue0 + 30 * DAY_MILLIS
                        dueReason = "Regulatory penalty — NDPC"
                        vars["amount"] = naira(amountDue)
                        vars["payby"] = longDate(payBy)
                        tl.add(decisionDue0, CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, "Decision: penalty imposed", f(CaseCourtText.decisionPenalty(random)), CaseStatus.PAYMENT_DUE)
                    }
                    else -> {
                        tl.add(decisionDue0, CaseEventKind.REGULATOR_DECISION, CaseActor.REGULATOR, "Decision: not resolved", f(CaseCourtText.decisionUnresolved(random)), CaseStatus.ESCALATED)
                        val courtInfo = addCourtPath(tl, vars, customer, price, now, decisionDue0, random)
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
            appId = app.id,
            appName = app.name,
            userId = customer.id,
            customerName = customer.name,
            customerEmail = customer.email,
            customerPhone = customer.phone,
            plan = customer.plan,
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
            notes = emptyList()
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
        customer: AppUser,
        price: Long,
        now: Long,
        afterMillis: Long,
        random: Random
    ): CourtInfo {
        val refundBase = price * random.nextInt(1, 4)
        val damages = roundTo(random.nextLong(100_000L, 1_500_001L), 50_000L)
        val costs = roundTo(random.nextLong(20_000L, 100_001L), 5_000L)
        val claim = refundBase + damages + costs

        val year = yearOf(afterMillis)
        val court: String
        val suitNo: String
        if (claim <= 500_000L) {
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

        val adjRoll = random.nextInt(100)
        val adjournments = if (adjRoll < 40) 0 else if (adjRoll < 80) 1 else 2
        repeat(adjournments) {
            day += 28
            tl.add(at(day), CaseEventKind.COURT_ADJOURNED, CaseActor.COURT, "Adjourned", f(CaseCourtText.adjourned(random)))
        }
        day += 28
        tl.add(at(day), CaseEventKind.COURT_HEARING, CaseActor.COURT, "Trial and final addresses", f(CaseCourtText.finalAddresses(random)))
        day += 21
        tl.add(at(day), CaseEventKind.COURT_HEARING, CaseActor.COURT, "Judgment reserved", f(CaseCourtText.reserved(random)))
        val judgmentDay = day + 35

        val outRoll = random.nextInt(100)
        return when {
            outRoll < 45 -> {
                tl.add(at(judgmentDay), CaseEventKind.COURT_JUDGMENT, CaseActor.COURT, "Judgment: claim dismissed", f(CaseCourtText.judgmentDismissed(random)), CaseStatus.CLOSED)
                CourtInfo(court, suitNo, claim, CaseOutcome.COMPANY_WON, 0L, "", 0L)
            }
            outRoll < 65 -> {
                val settlementDay = judgmentDay - random.nextInt(14, 22)
                val settleAt = at(settlementDay)
                val award = maxOf(5_000L, roundTo(claim * random.nextInt(25, 61) / 100L, 5_000L))
                val payBy = settleAt + 14 * DAY_MILLIS
                vars["award"] = naira(award)
                vars["payby"] = longDate(payBy)
                tl.add(settleAt, CaseEventKind.SETTLEMENT, CaseActor.COURT, "Settlement agreed", f(CaseCourtText.settlement(random)), CaseStatus.PAYMENT_DUE)
                CourtInfo(court, suitNo, claim, CaseOutcome.SETTLED, award, "Settlement agreed out of court", payBy)
            }
            else -> {
                val generalDamages = roundTo(damages * random.nextInt(15, 51) / 100L, 5_000L)
                val awardedCosts = roundTo(random.nextLong(30_000L, 150_001L), 5_000L)
                val award = minOf(claim, refundBase + generalDamages + awardedCosts)
                val judgmentAt = at(judgmentDay)
                val payBy = judgmentAt + 30 * DAY_MILLIS
                vars["award"] = naira(award)
                vars["payby"] = longDate(payBy)
                tl.add(judgmentAt, CaseEventKind.COURT_JUDGMENT, CaseActor.COURT, "Judgment for the claimant", f(CaseCourtText.judgmentClaimant(random)), CaseStatus.PAYMENT_DUE)
                CourtInfo(court, suitNo, claim, CaseOutcome.CUSTOMER_WON, award, "Court-ordered refund, damages and costs", payBy)
            }
        }
    }
}
