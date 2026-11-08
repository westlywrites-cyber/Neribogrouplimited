package com.westly.neribogroup.feature.cloud

import com.westly.neribogroup.data.AiCaseEvent
import com.westly.neribogroup.data.AiCaseResult
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.CaseAi
import com.westly.neribogroup.data.CaseCategory
import com.westly.neribogroup.data.CaseEngine
import com.westly.neribogroup.data.CaseGenerator
import com.westly.neribogroup.data.CaseLimits
import com.westly.neribogroup.data.CaseMoney
import com.westly.neribogroup.data.CaseOutcome
import com.westly.neribogroup.data.CaseSkeleton
import com.westly.neribogroup.data.CaseStatus
import com.westly.neribogroup.data.ComplaintCase
import com.westly.neribogroup.data.DAY_MILLIS
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.Plan
import com.westly.neribogroup.data.RollFacts
import com.westly.neribogroup.data.RollRecord
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Phase 5 self-check items (no network, no real data is changed). They use made-up skeletons and a made-up ledger
 * and are shown after the Phase 4B items.
 */
object CaseSelfCheck {

    private const val NOW = 1_800_000_000_000L

    private fun skeleton(
        tier: String,
        branch: String = "",
        thanks: Boolean = false,
        decision: String = "",
        lawsuit: String = "",
        adjournments: Int = 0,
        category: CaseCategory = CaseCategory.BILLING_RENEWAL,
        price: Long = 3_000L
    ): CaseSkeleton {
        val outcome = when {
            tier != "C" -> CaseOutcome.RESOLVED_BY_SUPPORT
            decision == "CLOSED" -> CaseOutcome.REGULATOR_CLOSED
            decision == "REFUND" -> CaseOutcome.REGULATOR_REFUND
            decision == "PENALTY" -> CaseOutcome.REGULATOR_PENALTY
            lawsuit == "DISMISSED" -> CaseOutcome.COMPANY_WON
            lawsuit == "SETTLED" -> CaseOutcome.SETTLED
            else -> CaseOutcome.CUSTOMER_WON
        }
        val short = if (category == CaseCategory.DATA_PRIVACY) "NDPC" else "FCCPC"
        return CaseSkeleton(
            appId = "selfcheck-app",
            appName = "Test app",
            customerId = "customer-1",
            customerName = "Chinedu Okafor",
            customerEmail = "chinedu.okafor@gmail.com",
            customerPhone = "08031234567",
            plan = Plan.PREMIUM,
            category = category,
            chargeMillis = NOW - 2 * DAY_MILLIS,
            chargeDateText = "10 January 2027",
            priceNaira = price,
            agentName = "Amaka O.",
            tier = tier,
            tierBBranch = branch,
            thanksReply = thanks,
            regulatorShort = if (tier == "C") short else "",
            regulatorFullName = if (tier == "C") CaseGenerator.regulatorFullName(short) else "",
            regulatorDecision = decision,
            lawsuitOutcome = lawsuit,
            adjournments = adjournments,
            outcome = outcome,
            limits = CaseLimits.STANDARD,
            createdMillis = NOW
        )
    }

    /** One skeleton of every shape a case can have. */
    private fun allSkeletons(): List<CaseSkeleton> {
        val list = ArrayList<CaseSkeleton>()
        list.add(skeleton("A", thanks = true))
        list.add(skeleton("A", thanks = false))
        list.add(skeleton("B", branch = "REPLY"))
        list.add(skeleton("B", branch = "NO_REPLY"))
        list.add(skeleton("C", decision = "CLOSED"))
        list.add(skeleton("C", decision = "REFUND"))
        list.add(skeleton("C", decision = "PENALTY", category = CaseCategory.DATA_PRIVACY, price = 0L))
        for (lawsuit in listOf("DISMISSED", "SETTLED", "CLAIMANT_WINS")) {
            for (adj in 0..2) list.add(skeleton("C", decision = "UNRESOLVED", lawsuit = lawsuit, adjournments = adj))
        }
        return list
    }

    private fun describe(sk: CaseSkeleton): String =
        sk.tier + "/" + sk.tierBBranch + sk.regulatorDecision + "/" + sk.lawsuitOutcome + "/" + sk.adjournments

    /** A valid answer built from the template: the first allowed day for every event. */
    private fun validAnswer(sk: CaseSkeleton, damages: Long = 400_000L, costs: Long = 50_000L): AiCaseResult {
        val steps = CaseAi.steps(sk)
        val events = ArrayList<AiCaseEvent>()
        for (i in steps.indices) {
            val range = CaseAi.dayRange(steps, i)
            val day = if (i == 0 || range == null) 0 else range.first
            events.add(
                AiCaseEvent(
                    kind = steps[i].kind.name,
                    title = "Message number " + i,
                    body = "This is plain test message number " + i + " written for the self check of the app.",
                    dayOffset = day,
                    hour = 10,
                    minute = 15
                )
            )
        }
        val claim = CaseMoney.claim(sk.priceNaira, 2, damages, costs)
        val big = claim > sk.limits.magistrateMaxClaim
        return AiCaseResult(
            subject = "Complaint about my plan",
            events = events,
            regulatorRef = if (sk.tier == "C") sk.regulatorShort + "/CMP/2027/4821" else "",
            court = if (sk.hasCourtPath) CaseMoney.courtFor(claim, sk.limits) else "",
            suitNo = if (sk.hasCourtPath) (if (big) "LD/1234/2027" else "MIK/1234/2027") else "",
            refundBaseMultiple = if (sk.hasCourtPath) 2 else 0,
            damages = if (sk.hasCourtPath) damages else 0L,
            costs = if (sk.hasCourtPath) costs else 0L,
            penalty = if (sk.regulatorDecision == "PENALTY") 200_000L else 0L,
            settlementPercent = if (sk.lawsuitOutcome == "SETTLED") 40 else 0,
            awardedCosts = if (sk.lawsuitOutcome == "CLAIMANT_WINS") 80_000L else 0L,
            generalDamagesPercent = if (sk.lawsuitOutcome == "CLAIMANT_WINS") 30 else 0,
            dueReason = ""
        )
    }

    private fun toJson(r: AiCaseResult): JSONObject {
        val events = JSONArray()
        for (e in r.events) {
            events.put(
                JSONObject().put("kind", e.kind).put("title", e.title).put("body", e.body)
                    .put("dayOffset", e.dayOffset).put("hour", e.hour).put("minute", e.minute)
            )
        }
        return JSONObject().put("subject", r.subject).put("events", events)
            .put("regulatorRef", r.regulatorRef).put("court", r.court).put("suitNo", r.suitNo)
            .put("refundBaseMultiple", r.refundBaseMultiple).put("damages", r.damages).put("costs", r.costs)
            .put("penalty", r.penalty).put("settlementPercent", r.settlementPercent)
            .put("awardedCosts", r.awardedCosts).put("generalDamagesPercent", r.generalDamagesPercent)
            .put("dueReason", r.dueReason)
    }

    private fun increasing(c: ComplaintCase): Boolean {
        for (i in 1 until c.events.size) if (c.events[i].dueMillis < c.events[i - 1].dueMillis) return false
        return true
    }

    private fun runCheck(name: String, block: () -> Pair<Boolean, String>): CheckLine {
        return try {
            val result = block()
            CheckLine(name, result.first, result.second)
        } catch (e: Throwable) {
            CheckLine(name, false, "Error: " + e.javaClass.simpleName)
        }
    }

    fun run(): List<CheckLine> {
        val lines = ArrayList<CheckLine>()
        val ledger = Ledger()
        val all = allSkeletons()

        lines.add(
            runCheck("16. Case money maths and the skeleton file") {
                val claim = CaseMoney.claim(3_000L, 2, 400_000L, 50_000L)
                val settle = CaseMoney.settlementAmount(claim, 40)
                val award = CaseMoney.claimantAward(6_000L, 400_000L, 30, 80_000L, claim)
                val courts = CaseMoney.courtFor(500_000L, CaseLimits.STANDARD) == CaseMoney.MAGISTRATE &&
                    CaseMoney.courtFor(500_001L, CaseLimits.STANDARD) == CaseMoney.HIGH_COURT
                val rounding = CaseMoney.roundTo(182_400L, 5_000L) == 180_000L
                val roundTrip = all.all { CaseSkeleton.fromJson(JSONObject(it.toJson().toString())) == it }
                val broken = CaseSkeleton.fromJson(JSONObject().put("tier", "Z")) == null
                val ok = claim == 456_000L && settle == 180_000L && award == 206_000L && courts && rounding && roundTrip && broken
                Pair(ok, if (ok) "Claim 456,000, settlement 180,000, " + all.size + " skeletons round trip" else "A number is wrong")
            }
        )

        lines.add(
            runCheck("17. The built-in texts follow the skeleton") {
                var problems = 0
                for ((index, sk) in all.withIndex()) {
                    val steps = CaseAi.steps(sk)
                    val c = CaseGenerator.buildFromTemplates(ledger, sk, NOW, Random(index + 1))
                    val kinds = c.events.map { it.kind }
                    if (kinds != steps.map { it.kind }) problems++
                    if (!increasing(c)) problems++
                    if (c.writer != "App") problems++
                    if (c.outcome != sk.outcome) problems++
                    val due = CaseEngine.statusAt(c, NOW + 400 * DAY_MILLIS)
                    val expected = when {
                        sk.regulatorDecision == "PENALTY" || sk.lawsuitOutcome == "SETTLED" || sk.lawsuitOutcome == "CLAIMANT_WINS" -> CaseStatus.PAYMENT_DUE
                        sk.lawsuitOutcome == "DISMISSED" -> CaseStatus.CLOSED
                        else -> CaseStatus.RESOLVED
                    }
                    if (due != expected) problems++
                    val pays = sk.regulatorDecision == "PENALTY" || sk.lawsuitOutcome == "SETTLED" || sk.lawsuitOutcome == "CLAIMANT_WINS"
                    if ((c.amountDue > 0L) != pays) problems++
                }
                Pair(problems == 0, all.size.toString() + " shapes checked")
            }
        )

        lines.add(
            runCheck("18. Claude's answers are accepted when right") {
                var problems = 0
                for (sk in all) {
                    val answer = validAnswer(sk)
                    val parsed = CaseAi.parseResult(JSONObject(toJson(answer).toString()))
                    if (parsed == null) {
                        problems++
                        continue
                    }
                    val c = CaseGenerator.buildFromAi(ledger, sk, parsed, NOW)
                    if (c == null) {
                        problems++
                        continue
                    }
                    if (c.writer != "Claude" || !increasing(c) || c.outcome != sk.outcome) problems++
                    if (c.events.size != CaseAi.steps(sk).size) problems++
                    if (sk.lawsuitOutcome == "SETTLED" && c.amountDue != 180_000L) problems++
                    if (sk.lawsuitOutcome == "CLAIMANT_WINS" && c.amountDue != 206_000L) problems++
                    if (sk.regulatorDecision == "PENALTY" && c.amountDue != 200_000L) problems++
                    if (sk.hasCourtPath && c.court != CaseMoney.MAGISTRATE) problems++
                }
                // A big claim goes to the High Court.
                val court = skeleton("C", decision = "UNRESOLVED", lawsuit = "DISMISSED")
                val big = CaseGenerator.buildFromAi(ledger, court, validAnswer(court, damages = 900_000L), NOW)
                if (big == null || big.court != CaseMoney.HIGH_COURT || !big.suitNo.startsWith("LD/")) problems++
                Pair(problems == 0, all.size.toString() + " answers accepted")
            }
        )

        lines.add(
            runCheck("19. Claude's answers are refused when wrong") {
                val tierA = skeleton("A", thanks = true)
                val tierC = skeleton("C", decision = "UNRESOLVED", lawsuit = "SETTLED", adjournments = 1)
                val goodA = validAnswer(tierA)
                val goodC = validAnswer(tierC)

                fun withEvent(r: AiCaseResult, index: Int, change: (AiCaseEvent) -> AiCaseEvent): AiCaseResult =
                    r.copy(events = r.events.mapIndexed { i, e -> if (i == index) change(e) else e })

                val bad = listOf(
                    "kind" to CaseGenerator.buildFromAi(ledger, tierA, withEvent(goodA, 1) { it.copy(kind = "RESOLUTION") }, NOW),
                    "missing event" to CaseGenerator.buildFromAi(ledger, tierA, goodA.copy(events = goodA.events.dropLast(1)), NOW),
                    "link" to CaseGenerator.buildFromAi(ledger, tierA, withEvent(goodA, 1) { it.copy(body = it.body + " https://example.com") }, NOW),
                    "short body" to CaseGenerator.buildFromAi(ledger, tierA, withEvent(goodA, 1) { it.copy(body = "Too short") }, NOW),
                    "email" to CaseGenerator.buildFromAi(ledger, tierA, withEvent(goodA, 1) { it.copy(body = it.body + " write to a@b.com") }, NOW),
                    "long number" to CaseGenerator.buildFromAi(ledger, tierA, withEvent(goodA, 1) { it.copy(body = it.body + " 01234567890") }, NOW),
                    "day range" to CaseGenerator.buildFromAi(ledger, tierA, withEvent(goodA, 2) { it.copy(dayOffset = 9) }, NOW),
                    "days go back" to CaseGenerator.buildFromAi(ledger, tierA, withEvent(goodA, 3) { it.copy(dayOffset = 2) }, NOW),
                    "hour" to CaseGenerator.buildFromAi(ledger, tierA, withEvent(goodA, 2) { it.copy(hour = 22) }, NOW),
                    "ref on tier A" to CaseGenerator.buildFromAi(ledger, tierA, goodA.copy(regulatorRef = "FCCPC/CMP/2027/4821"), NOW),
                    "wrong regulator" to CaseGenerator.buildFromAi(ledger, tierC, goodC.copy(regulatorRef = "NDPC/CMP/2027/4821"), NOW),
                    "damages step" to CaseGenerator.buildFromAi(ledger, tierC, goodC.copy(damages = 125_000L), NOW),
                    "court" to CaseGenerator.buildFromAi(ledger, tierC, goodC.copy(court = CaseMoney.HIGH_COURT), NOW),
                    "suit number" to CaseGenerator.buildFromAi(ledger, tierC, goodC.copy(suitNo = "XX/12/2027"), NOW),
                    "percent" to CaseGenerator.buildFromAi(ledger, tierC, goodC.copy(settlementPercent = 70), NOW)
                )
                val accepted = bad.filter { it.second != null }.map { it.first }
                val extraKey = CaseAi.parseResult(toJson(goodA).put("surprise", 1)) == null
                val missingKey = CaseAi.parseResult(JSONObject(toJson(goodA).toString()).also { it.remove("costs") }) == null
                val ok = accepted.isEmpty() && extraKey && missingKey
                Pair(ok, if (ok) bad.size.toString() + " bad answers refused" else "Wrongly accepted: " + accepted.joinToString(", "))
            }
        )

        lines.add(
            runCheck("20. A case job is well formed") {
                var problems = 0
                var largest = 0
                for (sk in all) {
                    val job = CaseAi.buildJob(sk)
                    val ctx = job.getJSONObject("context")
                    val schema = job.getJSONObject("result_schema")
                    val steps = CaseAi.steps(sk)
                    val bytes = ctx.toString().toByteArray(Charsets.UTF_8).size
                    if (bytes > largest) largest = bytes
                    if (bytes > CaseAi.CONTEXT_LIMIT_BYTES) problems++
                    if (job.getString("kind") != "WRITE_CASE" || job.getInt("priority") != 90) problems++
                    if (!job.getString("dedupe_key").startsWith("case:") || job.getString("dedupe_key").length > 200) problems++
                    if (ctx.getJSONArray("template").length() != steps.size) problems++
                    val events = schema.getJSONObject("properties").getJSONObject("events")
                    if (events.getInt("minItems") != steps.size || events.getInt("maxItems") != steps.size) problems++
                    if (CaseSkeleton.fromJson(ctx.getJSONObject("skeleton")) != sk) problems++
                    if (CaseAi.caseId(job.getString("dedupe_key")) != CaseAi.caseId(CaseAi.dedupeKey(sk))) problems++
                }
                Pair(problems == 0 && largest > 0, all.size.toString() + " jobs, largest context " + largest + " bytes")
            }
        )

        lines.add(
            runCheck("21. The same seed gives the same case") {
                val users = (1..40).map { i ->
                    AppUser(
                        id = "u$i", name = "Test User $i", email = "t$i@example.com", plan = Plan.PREMIUM,
                        addedMillis = NOW - 20 * DAY_MILLIS, planExpiryMillis = NOW + DAY_MILLIS,
                        planStartMillis = NOW - 30 * DAY_MILLIS, simulated = true
                    )
                }
                val rolls = (1..20).map { RollRecord("2027-01-%02d".format(it), listOf(0, 0, 0, 0, 0, 0, 0), 0, 0, 0, 0, 0, 0, 0, 0, false) }
                val app = AppData(
                    id = "selfcheck-app", name = "Test app", premiumPrice = 3_000L, proPrice = 9_000L,
                    adsEnabled = true, googlePlayEnabled = true, users = users, rolls = rolls
                )
                val base = Ledger(apps = listOf(app))
                val facts = RollFacts(renewedUsers = users, lapsedUsers = users, upgradedUsers = users, newUserCount = 400, adsEarned = true)
                var compared = 0
                var problems = 0
                for (seed in 1..400) {
                    val whole = CaseGenerator.maybeCreate(base, app.id, facts, NOW, Random(seed))
                    val random = Random(seed)
                    val sk = CaseGenerator.decideSkeleton(base, app.id, facts, NOW, random)
                    if ((sk == null) != whole.cases.isEmpty()) {
                        problems++
                        continue
                    }
                    if (sk == null) continue
                    val made = CaseGenerator.buildFromTemplates(base, sk, NOW, random)
                    val same = whole.cases.first()
                    compared++
                    if (made.ref != same.ref || made.category != same.category || made.outcome != same.outcome ||
                        made.events.map { it.kind } != same.events.map { it.kind } ||
                        made.events.map { it.dueMillis } != same.events.map { it.dueMillis } ||
                        made.subject != same.subject
                    ) problems++
                }
                Pair(problems == 0 && compared > 0, compared.toString() + " cases compared")
            }
        )
        return lines
    }
}
