package com.westly.neribogroup.feature.cloud

import com.westly.neribogroup.cloud.AiJobRules
import com.westly.neribogroup.cloud.SoftwareAiRules
import com.westly.neribogroup.data.AiProposal
import com.westly.neribogroup.data.AppData
import com.westly.neribogroup.data.AppUser
import com.westly.neribogroup.data.ClientType
import com.westly.neribogroup.data.DEFAULT_RATES
import com.westly.neribogroup.data.DAY_MILLIS
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.LedgerStorage
import com.westly.neribogroup.data.OfferKind
import com.westly.neribogroup.data.Plan
import com.westly.neribogroup.data.PlanEvent
import com.westly.neribogroup.data.PlanEventKind
import com.westly.neribogroup.data.PlanHistory
import com.westly.neribogroup.data.ProposalSlot
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.SOFTWARE_CURRENCIES
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.SoftwareGenerator
import com.westly.neribogroup.data.SoftwareOffer
import com.westly.neribogroup.data.SoftwarePreset
import com.westly.neribogroup.data.withSoftwareApp
import org.json.JSONObject
import kotlin.random.Random

/**
 * Phase 6 self-check items (no network, no real data is changed): the software proposals written by Claude and
 * the plan history. They use a made-up ledger and are shown after the Phase 5 items.
 */
object SoftwareSelfCheck {

    private const val NOW = 1_800_000_000_000L

    private fun item(name: String, block: () -> String?): CheckLine = try {
        val problem = block()
        if (problem == null) CheckLine(name, true, "OK") else CheckLine(name, false, problem)
    } catch (e: Throwable) {
        CheckLine(name, false, "Crashed: " + e.javaClass.simpleName)
    }

    private fun ledger(): Ledger = Ledger().withSoftwareApp()

    /** The ids of offers are random. Compare offers by everything else. */
    private fun plain(l: Ledger): Ledger {
        val sw = l.software
        return l.copy(software = sw.copy(offers = sw.offers.map { it.copy(id = it.ref) }))
    }

    private fun slot(index: Int = 0, kind: OfferKind = OfferKind.WEBSITE): ProposalSlot = ProposalSlot(
        index = index, kind = kind, typeCode = "", clientType = ClientType.COMPANY, country = "Nigeria",
        currency = "NGN", minUnits = 300_000L, maxUnits = 5_000_000L, stepUnits = 5_000L,
        minWeeks = 2, maxWeeks = 12
    )

    private fun good(): AiProposal = AiProposal(
        clientName = "Adaeze Fabrics Limited", contactName = "Adaeze Okafor", email = "info@adaeze-fabrics.example.com",
        phone = "+2348031234567", country = "Nigeria", title = "Online store for a fabric shop",
        summary = "Adaeze Fabrics wants a fast online shop with a product catalogue and card payments.",
        requirements = listOf(
            "A product catalogue with search", "Card and bank transfer payments", "Order emails to customers",
            "An admin page to add products", "Works well on a phone"
        ),
        deliveryWeeks = 4, feeUnits = 450_000L
    )

    private fun make(slot: ProposalSlot, ai: AiProposal, l: Ledger = ledger(), domains: List<String>? = null): SoftwareOffer? =
        SoftwareGenerator.createOfferFromAi(
            slot, ai, l.software.offers, l.software.clients, l.software.rates, 1, NOW, Random(5), domains
        )

    fun run(): List<CheckLine> {
        val lines = ArrayList<CheckLine>()

        lines.add(item("P6 roll with a preset of the same faces = old roll") {
            val r = Random(11)
            val cf = r.nextInt(0, 6)
            val of = r.nextInt(0, 6)
            val a = SoftwareEngine.roll(ledger(), NOW, Random(11), null)
            val b = SoftwareEngine.roll(ledger(), NOW, Random(11), SoftwarePreset(faces = listOf(cf, of)))
            if (plain(a) == plain(b)) null else "The two rolls are not the same"
        })

        lines.add(item("P6 planSlot + createOfferFromSlot = old createOffer") {
            val l = ledger()
            val rates = l.software.rates
            var bad: String? = null
            for (seed in 1..40) {
                val a = SoftwareGenerator.createOffer(emptyList(), emptyList(), rates, 1, NOW, Random(seed))
                val r = Random(seed)
                val sl = SoftwareGenerator.planSlot(0, rates, r)
                val b = SoftwareGenerator.createOfferFromSlot(emptyList(), emptyList(), rates, 1, NOW, r, sl)
                if (a.copy(id = "x") != b.copy(id = "x")) {
                    bad = "Different offers for seed $seed"
                    break
                }
            }
            bad
        })

        lines.add(item("P6 fee bands stay inside the naira limits") {
            val rates = SoftwareEngine.completeRates(DEFAULT_RATES)
            var bad: String? = null
            for (code in SOFTWARE_CURRENCIES) {
                for (isCode in listOf(false, true)) {
                    val low = if (isCode) 1_000_000L else 300_000L
                    val high = if (isCode) 50_000_000L else 5_000_000L
                    val band = SoftwareGenerator.feeBand(isCode, code, rates)
                    val step = if (code == "NGN") 5_000L else 50L
                    val lo = SoftwareEngine.ngnEstimateFor(band.first, code, rates)
                    val hi = SoftwareEngine.ngnEstimateFor(band.second, code, rates)
                    if (band.first > band.second || lo < low || hi > high ||
                        band.first % step != 0L || band.second % step != 0L
                    ) {
                        bad = "Band wrong for $code" + (if (isCode) " (code)" else "")
                        break
                    }
                }
                if (bad != null) break
            }
            bad
        })

        lines.add(item("P6 a good Claude proposal is accepted") {
            val o = make(slot(), good())
            when {
                o == null -> "A good proposal was refused"
                o.writer != "Claude" -> "Writer is not Claude"
                o.requirements.size != 7 -> "Payment and maintenance lines are missing"
                o.feeUnits != 450_000L || o.country != "Nigeria" || o.currency != "NGN" -> "Slot values not used"
                else -> null
            }
        })

        lines.add(item("P6 a bad Claude proposal is refused") {
            val s = slot()
            val g = good()
            val tries = listOf(
                "fee not a whole step" to g.copy(feeUnits = 450_001L),
                "fee too low" to g.copy(feeUnits = 100_000L),
                "weeks too many" to g.copy(deliveryWeeks = 40),
                "too few requirements" to g.copy(requirements = g.requirements.take(2)),
                "link in text" to g.copy(summary = g.summary + " See www.example.com for details."),
                "bad email" to g.copy(email = "not an email"),
                "bad phone" to g.copy(phone = "123"),
                "placeholder text" to g.copy(title = "Lorem ipsum website")
            )
            var bad: String? = null
            for ((what, ai) in tries) {
                if (make(s, ai) != null) {
                    bad = "Accepted: $what"
                    break
                }
            }
            if (bad == null && make(s, g, domains = listOf("other.org")) != null) bad = "Accepted a domain that is not allowed"
            bad
        })

        lines.add(item("P6 a name that is already used is refused") {
            val first = make(slot(), good()) ?: return@item "First proposal refused"
            val l = ledger().let { it.copy(software = it.software.copy(offers = listOf(first))) }
            if (make(slot(), good(), l) == null) null else "A repeated client was accepted"
        })

        lines.add(item("P6 roll: Claude proposal used, bad one written by the app") {
            val s0 = slot(0)
            val s1 = slot(1)
            val r = SoftwareEngine.roll(
                ledger(), NOW, Random(3),
                SoftwarePreset(
                    faces = listOf(5, 5), slots = listOf(s0, s1),
                    proposals = listOf(good(), good().copy(feeUnits = 7L))
                )
            )
            val offers = r.software.offers
            when {
                offers.size != 3 -> "Expected 3 proposals, got " + offers.size
                offers.count { it.writer == "Claude" } != 1 -> "Expected one proposal from Claude"
                offers.count { it.writer == "App" } != 2 -> "Expected two from the app"
                r.software.lastRollDayKey.isEmpty() -> "Roll day not saved"
                else -> null
            }
        })

        lines.add(item("P6 one roll per day with a preset") {
            val once = SoftwareEngine.roll(ledger(), NOW, Random(3), SoftwarePreset(faces = listOf(5, 5)))
            val twice = SoftwareEngine.roll(once, NOW, Random(4), SoftwarePreset(faces = listOf(5, 5)))
            if (twice === once) null else "A second roll changed the ledger"
        })

        lines.add(item("P6 slot survives a job context") {
            val s = slot(2, OfferKind.CODE_PURCHASE).copy(minUnits = 1_000_000L, maxUnits = 50_000_000L, minWeeks = 1, maxWeeks = 2)
            val back = SoftwareAiRules.slotFromJson(JSONObject(SoftwareAiRules.slotToJson(s).toString()))
            if (back == s) null else "The slot changed on the way"
        })

        lines.add(item("P6 software jobs: keys, kinds, schema, size") {
            val l = ledger()
            val dice = SoftwareAiRules.buildDiceJobs(l, NOW)
            if (dice.size != 2) return@item "Expected 2 dice jobs"
            val day = com.westly.neribogroup.data.dayKey(SoftwareEngine.effectiveNow(l, NOW))
            val k0 = dice[0].getString("dedupe_key")
            if (k0 != "$day:$SOFTWARE_APP_ID:SOFTWARE_CONTACTS") return@item "Wrong dice key: $k0"
            if (dice.any { it.getString("kind") != "DICE_ROLL" || it.getString("app_id") != SOFTWARE_APP_ID }) {
                return@item "Dice jobs must be DICE_ROLL jobs of the software app"
            }
            val slots = listOf(slot(0), slot(1, OfferKind.CODE_PURCHASE))
            val jobs = SoftwareAiRules.buildProposalJobs(l, slots, listOf(4, 4), day, NOW)
            if (jobs.size != 2) return@item "Expected 2 proposal jobs"
            if (jobs[1].getString("dedupe_key") != "sw:$day:1" || jobs[1].getInt("sort_order") != 11) return@item "Wrong proposal key or order"
            if (jobs.any { it.getString("kind") != "WRITE_PROPOSAL" || it.getInt("priority") != 110 }) return@item "Wrong proposal kind or priority"
            if (jobs.any { it.getJSONObject("context").toString().toByteArray(Charsets.UTF_8).size > 6000 }) return@item "A context is over 6 KB"
            val step = jobs[0].getJSONObject("result_schema").getJSONObject("properties").getJSONObject("feeUnits").getLong("multipleOf")
            if (step != 5_000L) return@item "Wrong fee step in the schema"
            val faces = jobs[0].getJSONObject("context").getJSONArray("faces")
            if (faces.getInt(0) != 4) return@item "Faces missing from the job"
            null
        })

        lines.add(item("P6 the software dice never enter the Play dice rows") {
            val json = JSONObject(
                "{\"jobs\":[{\"id\":\"a\",\"kind\":\"DICE_ROLL\",\"app_id\":\"software-clients\",\"dedupe_key\":\"2026-11-09:software-clients:SOFTWARE_CONTACTS\",\"day_key\":\"2026-11-09\",\"status\":\"pending\"}," +
                    "{\"id\":\"b\",\"kind\":\"DICE_ROLL\",\"app_id\":\"app1\",\"dedupe_key\":\"2026-11-09:app1:NEW\",\"day_key\":\"2026-11-09\",\"status\":\"pending\"}]}"
            )
            val play = AiJobRules.parseRows(json)
            val sw = SoftwareAiRules.parseRows(json)
            if (play.size == 1 && play[0].id == "b" && sw.size == 1 && sw[0].dice == "SOFTWARE_CONTACTS") null
            else "Rows were sorted into the wrong group"
        })

        lines.add(item("P6 Claude answer is read into a proposal") {
            val answer = JSONObject(
                "{\"clientName\":\"Adaeze Fabrics Limited\",\"contactName\":\"Adaeze Okafor\",\"email\":\"info@adaeze.example.com\"," +
                    "\"phone\":\"+2348031234567\",\"country\":\"Nigeria\",\"title\":\"Online store for fabrics\"," +
                    "\"summary\":\"A shop with a catalogue and card payments for a fabric business.\"," +
                    "\"requirements\":[\"One\",\"Two\"],\"deliveryWeeks\":4,\"feeUnits\":450000}"
            )
            val p = SoftwareAiRules.parseProposal(answer)
            val broken = SoftwareAiRules.parseProposal(JSONObject("{\"clientName\":\"x\"}"))
            if (p != null && p.feeUnits == 450_000L && p.deliveryWeeks == 4 && broken == null) null else "Reading failed"
        })

        // ---- Plan history ----

        lines.add(item("P6 plan history: merge, no duplicates, newest 2000") {
            val e1 = PlanHistory.event("u1", PlanEventKind.STARTED, Plan.PREMIUM, 1000L, 5000L)
            val merged = PlanHistory.merge(listOf(e1), listOf(e1, PlanHistory.event("u1", PlanEventKind.RENEWED, Plan.PREMIUM, 2000L, 9000L)))
            if (merged.size != 2) return@item "Duplicate not removed"
            val many = (1..2100).map { PlanHistory.event("u", PlanEventKind.RENEWED, Plan.PRO, it.toLong(), 0L) }
            val cut = PlanHistory.merge(emptyList(), many)
            if (cut.size != PlanHistory.MAX_EVENTS || cut.first().atMillis != 101L) return@item "Not cut to the newest 2000"
            null
        })

        lines.add(item("P6 plan history: your own users are recorded") {
            val user = AppUser(name = "Ada", email = "ada@example.com", plan = Plan.PREMIUM, addedMillis = NOW)
            var app = AppData(name = "T", premiumPrice = 1000L).withUserAdded(user)
            val id = app.users.first().id
            if (app.planHistory.none { it.kind == PlanEventKind.STARTED }) return@item "Start not recorded"
            app = app.withUserRenewed(id, NOW + DAY_MILLIS)
            if (app.planHistory.none { it.kind == PlanEventKind.RENEWED }) return@item "Renewal not recorded"
            app = app.withPlanLapsed(id, NOW + 40 * DAY_MILLIS)
            if (app.planHistory.none { it.kind == PlanEventKind.EXPIRED }) return@item "Ending not recorded"
            if (PlanHistory.forUser(app.planHistory, id).first().kind != PlanEventKind.EXPIRED) return@item "Newest is not first"
            null
        })

        lines.add(item("P6 plan history: saving an edit keeps the dice history") {
            val ev = PlanHistory.event("sim", PlanEventKind.STARTED, Plan.PRO, 500L, 900L)
            val current = AppData(name = "T", googlePlayEnabled = true, planHistory = listOf(ev))
            val draft = current.copy(planHistory = emptyList())
            val saved = draft.keepingDiceData(current)
            if (saved.planHistory.any { it.id == ev.id }) null else "The dice history was lost"
        })

        lines.add(item("P6 plan history: saved and read back") {
            val ev = PlanEvent("id1", "u1", PlanEventKind.MOVED_TO_FREE, Plan.PREMIUM, 777L, 888L)
            val l = Ledger(apps = listOf(AppData(name = "T", planHistory = listOf(ev))))
            val back = LedgerStorage.fromJson(LedgerStorage.toJson(l))
            if (back.apps.firstOrNull()?.planHistory == listOf(ev)) null else "History changed after saving"
        })

        return lines
    }
}
