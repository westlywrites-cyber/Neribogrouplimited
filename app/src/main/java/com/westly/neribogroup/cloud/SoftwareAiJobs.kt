package com.westly.neribogroup.cloud

import com.westly.neribogroup.data.AiProposal
import com.westly.neribogroup.data.ClientType
import com.westly.neribogroup.data.Ledger
import com.westly.neribogroup.data.OfferKind
import com.westly.neribogroup.data.ProposalSlot
import com.westly.neribogroup.data.SOFTWARE_APP_ID
import com.westly.neribogroup.data.SOFTWARE_APP_NAME
import com.westly.neribogroup.data.SOFTWARE_TYPES
import com.westly.neribogroup.data.SoftwareEngine
import com.westly.neribogroup.data.dayKey
import org.json.JSONArray
import org.json.JSONObject

/** One software job (a dice or a proposal) as read from the cloud. */
class SwRow(
    val id: String,
    val dedupeKey: String,
    /** "DICE_ROLL" (the two dice) or "WRITE_PROPOSAL". */
    val kind: String,
    val dayKey: String,
    val status: String,
    val result: JSONObject?,
    val context: JSONObject?
) {
    val isDice: Boolean get() = kind == SoftwareAiRules.DICE_JOB_KIND
    val isProposal: Boolean get() = kind == SoftwareAiRules.PROPOSAL_KIND

    /** SOFTWARE_CONTACTS or SOFTWARE_PROPOSALS for a dice row, else "". */
    val dice: String get() = if (isDice) dedupeKey.substringAfterLast(':') else ""

    /** The proposal number for a proposal row, else -1. */
    val proposalIndex: Int get() = if (isProposal) dedupeKey.substringAfterLast(':').toIntOrNull() ?: -1 else -1
}

/**
 * Phase 6: the jobs of Software Development Clients. The two dice are sent as DICE_ROLL jobs of the app
 * "software-clients", because the cloud hands out all DICE_ROLL jobs of one app together. The proposals come
 * later, one WRITE_PROPOSAL job each, after the faces are known.
 */
object SoftwareAiRules {

    const val DICE_JOB_KIND = "DICE_ROLL"
    const val PROPOSAL_KIND = "WRITE_PROPOSAL"
    const val DICE_CONTACTS = "SOFTWARE_CONTACTS"
    const val DICE_PROPOSALS = "SOFTWARE_PROPOSALS"
    const val PRIORITY = 110
    private const val CONTEXT_LIMIT_BYTES = 6000

    fun diceKey(day: String, dice: String): String = day + ":" + SOFTWARE_APP_ID + ":" + dice
    fun proposalKey(day: String, index: Int): String = "sw:$day:$index"

    private fun text(o: JSONObject, key: String): String = if (o.isNull(key)) "" else o.optString(key, "")

    /** Reads the software rows (the two dice and the proposals) of a list_jobs answer. */
    fun parseRows(json: JSONObject): List<SwRow> {
        val array = json.optJSONArray("jobs") ?: return emptyList()
        val rows = ArrayList<SwRow>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val kind = text(o, "kind")
            if (text(o, "app_id") != SOFTWARE_APP_ID) continue
            if (kind != DICE_JOB_KIND && kind != PROPOSAL_KIND) continue
            val id = text(o, "id")
            val key = text(o, "dedupe_key")
            if (id.isEmpty() || key.isEmpty()) continue
            rows.add(
                SwRow(
                    id = id, dedupeKey = key, kind = kind, dayKey = text(o, "day_key"),
                    status = text(o, "status"), result = o.optJSONObject("result"),
                    context = o.optJSONObject("context")
                )
            )
        }
        return rows
    }

    // ---- The two dice ----

    fun diceSchema(): JSONObject {
        val face = JSONObject().put("type", "integer").put("minimum", 0).put("maximum", 5)
        val comment = JSONObject().put("type", "string").put("maxLength", 200)
        return JSONObject()
            .put("type", "object")
            .put("properties", JSONObject().put("face", face).put("comment", comment))
            .put("required", JSONArray().put("face"))
            .put("additionalProperties", false)
    }

    private fun faceTable(contacts: Boolean): JSONArray {
        val table = JSONArray()
        for (f in 0..5) {
            val effect = if (contacts) {
                val n = SoftwareEngine.contactsForFace(f)
                if (n == 0) "no new contacts" else if (n == 1) "1 new contact" else "$n new contacts"
            } else {
                val share = SoftwareEngine.sharePercentForFace(f)
                if (share == 0) "no contact sends a proposal" else "$share% of the new contacts send a proposal"
            }
            table.put(JSONObject().put("face", f).put("effect", effect))
        }
        return table
    }

    private fun diceContext(ledger: Ledger, dice: String, now: Long): JSONObject {
        val sw = ledger.software
        val eff = SoftwareEngine.effectiveNow(ledger, now)
        val contacts = dice == DICE_CONTACTS
        val ctx = JSONObject()
        ctx.put("job", DICE_JOB_KIND)
        ctx.put("company", "Neribo Group")
        ctx.put("app", JSONObject().put("name", SOFTWARE_APP_NAME).put("id", SOFTWARE_APP_ID))
        ctx.put(
            "dice",
            JSONObject()
                .put("kind", dice)
                .put("label", if (contacts) "Contacts dice" else "Proposals dice")
                .put("faces", "0-5")
                .put(
                    "meaning",
                    if (contacts) "How many new people contact the software business today."
                    else "How many of those new contacts send a written proposal."
                )
        )
        ctx.put("day", dayKey(eff))
        ctx.put(
            "state",
            JSONObject()
                .put("waitingProposals", SoftwareEngine.waitingCount(ledger, eff))
                .put("maxWaiting", SoftwareEngine.MAX_WAITING)
                .put("clients", sw.clients.size)
        )
        ctx.put("faceTable", faceTable(contacts))
        ctx.put("note", "Choose a face from 0 to 5 honestly at random. 0 means nothing.")
        return ctx
    }

    /** The two dice jobs of today. */
    fun buildDiceJobs(ledger: Ledger, now: Long): List<JSONObject> {
        val day = dayKey(SoftwareEngine.effectiveNow(ledger, now))
        val jobs = ArrayList<JSONObject>()
        for ((i, dice) in listOf(DICE_CONTACTS, DICE_PROPOSALS).withIndex()) {
            jobs.add(
                JSONObject()
                    .put("dedupe_key", diceKey(day, dice))
                    .put("kind", DICE_JOB_KIND)
                    .put("app_id", SOFTWARE_APP_ID)
                    .put("day_key", day)
                    .put("priority", PRIORITY)
                    .put("sort_order", i)
                    .put("context", diceContext(ledger, dice, now))
                    .put("result_schema", diceSchema())
            )
        }
        return jobs
    }

    /** A face 0 to 5 from an answer, or -1 when the answer is not usable. */
    fun faceOf(result: JSONObject?): Int {
        if (result == null) return -1
        val number = result.opt("face") as? Number ?: return -1
        val d = number.toDouble()
        if (d != Math.floor(d) || d < 0.0 || d > 5.0) return -1
        return d.toInt()
    }

    // ---- The slots ----

    fun slotToJson(s: ProposalSlot): JSONObject = JSONObject()
        .put("index", s.index)
        .put("kind", s.kind.name)
        .put("typeCode", s.typeCode)
        .put("clientType", s.clientType.name)
        .put("country", s.country)
        .put("currency", s.currency)
        .put("minUnits", s.minUnits)
        .put("maxUnits", s.maxUnits)
        .put("stepUnits", s.stepUnits)
        .put("minWeeks", s.minWeeks)
        .put("maxWeeks", s.maxWeeks)
        .put("minRequirements", s.minRequirements)
        .put("maxRequirements", s.maxRequirements)

    fun slotFromJson(o: JSONObject?): ProposalSlot? {
        if (o == null) return null
        val kind = OfferKind.values().firstOrNull { it.name == o.optString("kind") } ?: return null
        val clientType = ClientType.values().firstOrNull { it.name == o.optString("clientType") } ?: return null
        val step = o.optLong("stepUnits")
        val min = o.optLong("minUnits")
        val max = o.optLong("maxUnits")
        if (step <= 0L || min <= 0L || max < min) return null
        val country = o.optString("country")
        val currency = o.optString("currency")
        if (country.isBlank() || currency.isBlank()) return null
        return ProposalSlot(
            index = o.optInt("index"),
            kind = kind,
            typeCode = o.optString("typeCode"),
            clientType = clientType,
            country = country,
            currency = currency,
            minUnits = min,
            maxUnits = max,
            stepUnits = step,
            minWeeks = o.optInt("minWeeks", 2),
            maxWeeks = o.optInt("maxWeeks", 12),
            minRequirements = o.optInt("minRequirements", 5),
            maxRequirements = o.optInt("maxRequirements", 7)
        )
    }

    // ---- The proposals ----

    fun proposalSchema(slot: ProposalSlot): JSONObject {
        fun str(min: Int, max: Int) = JSONObject().put("type", "string").put("minLength", min).put("maxLength", max)
        val props = JSONObject()
            .put("clientName", str(3, 80))
            .put("contactName", str(3, 60))
            .put(
                "email",
                JSONObject().put("type", "string").put("maxLength", 80).put("pattern", "^[a-z0-9._-]+@[a-z0-9.-]+$")
            )
            .put(
                "phone",
                JSONObject().put("type", "string").put("minLength", 8).put("maxLength", 18)
                    .put("pattern", "^[+]?[0-9]{8,15}$")
            )
            .put("country", str(3, 40))
            .put("title", str(8, 100))
            .put("summary", str(40, 400))
            .put(
                "requirements",
                JSONObject().put("type", "array").put("minItems", slot.minRequirements)
                    .put("maxItems", slot.maxRequirements).put("items", str(8, 120))
            )
            .put(
                "deliveryWeeks",
                JSONObject().put("type", "integer").put("minimum", slot.minWeeks).put("maximum", slot.maxWeeks)
            )
            .put(
                "feeUnits",
                JSONObject().put("type", "integer").put("minimum", slot.minUnits).put("maximum", slot.maxUnits)
                    .put("multipleOf", slot.stepUnits)
            )
        val required = JSONArray()
        for (k in listOf(
            "clientName", "contactName", "email", "phone", "country", "title", "summary",
            "requirements", "deliveryWeeks", "feeUnits"
        )) required.put(k)
        return JSONObject()
            .put("type", "object")
            .put("properties", props)
            .put("required", required)
            .put("additionalProperties", false)
    }

    private fun kindWords(kind: OfferKind): String = when (kind) {
        OfferKind.WEBSITE -> "a website"
        OfferKind.WEB_APP -> "a web app"
        OfferKind.ANDROID_APP -> "an Android app"
        OfferKind.CODE_PURCHASE -> "buying existing source code"
    }

    private fun proposalContext(ledger: Ledger, slot: ProposalSlot, faces: List<Int>, now: Long, names: Int, titles: Int): JSONObject {
        val sw = ledger.software
        val eff = SoftwareEngine.effectiveNow(ledger, now)
        val ctx = JSONObject()
        ctx.put("job", PROPOSAL_KIND)
        ctx.put("company", "Neribo Group")
        ctx.put("app", JSONObject().put("name", SOFTWARE_APP_NAME).put("id", SOFTWARE_APP_ID))
        ctx.put("day", dayKey(eff))
        ctx.put("faces", JSONArray().put(faces.getOrElse(0) { -1 }).put(faces.getOrElse(1) { -1 }))
        ctx.put(
            "slot",
            slotToJson(slot)
        )
        ctx.put(
            "slotInWords",
            "A " + slot.clientType.name.lowercase() + " client from " + slot.country + " (" + slot.currency +
                ") wants " + kindWords(slot.kind) + ". Fee between " + slot.minUnits + " and " + slot.maxUnits +
                " " + slot.currency + " in steps of " + slot.stepUnits + ", delivery " + slot.minWeeks + " to " +
                slot.maxWeeks + " weeks, " + slot.minRequirements + " to " + slot.maxRequirements +
                " requirement lines."
        )
        val rates = SoftwareEngine.completeRates(sw.rates)
        ctx.put(
            "rate",
            JSONObject()
                .put("currency", slot.currency)
                .put("unitsPerUsd", SoftwareEngine.perUsd(slot.currency, rates))
                .put("nairaPerUsd", SoftwareEngine.perUsd("NGN", rates))
                .put("nairaMin", SoftwareEngine.ngnEstimateFor(slot.minUnits, slot.currency, rates))
                .put("nairaMax", SoftwareEngine.ngnEstimateFor(slot.maxUnits, slot.currency, rates))
        )
        val clientNames = JSONArray()
        for (c in sw.clients.takeLast(names)) clientNames.put(c.name)
        for (o in sw.offers.takeLast(names)) clientNames.put(o.clientName)
        ctx.put("existingClientNames", clientNames)
        val recent = JSONArray()
        for (o in sw.offers.takeLast(titles)) recent.put(o.title)
        ctx.put("recentProposalTitles", recent)
        val type = SOFTWARE_TYPES.firstOrNull { it.code == slot.typeCode }
        if (type != null) {
            val sample = JSONObject().put("title", type.title)
            type.summaries.firstOrNull()?.let { sample.put("summary", it) }
            val reqs = JSONArray()
            for (r in type.pool.take(3)) reqs.put(r)
            sample.put("requirements", reqs)
            ctx.put("styleSample", sample)
        }
        ctx.put(
            "note",
            "The app fixes the client type, country, currency and kind. Write the client, the text, the fee and the weeks."
        )
        return ctx
    }

    /** The context of one proposal job, kept under 6 KB by dropping names when it is too long. */
    fun proposalContextFor(ledger: Ledger, slot: ProposalSlot, faces: List<Int>, now: Long): JSONObject {
        var names = 30
        var titles = 20
        while (true) {
            val ctx = proposalContext(ledger, slot, faces, now, names, titles)
            if ((names == 0 && titles == 0) || ctx.toString().toByteArray(Charsets.UTF_8).size <= CONTEXT_LIMIT_BYTES) {
                return ctx
            }
            names = if (names > 10) 10 else 0
            titles = if (titles > 5) 5 else 0
        }
    }

    /** The proposal jobs of a day, one per slot. */
    fun buildProposalJobs(
        ledger: Ledger,
        slots: List<ProposalSlot>,
        faces: List<Int>,
        day: String,
        now: Long
    ): List<JSONObject> {
        val jobs = ArrayList<JSONObject>()
        for (slot in slots) {
            jobs.add(
                JSONObject()
                    .put("dedupe_key", proposalKey(day, slot.index))
                    .put("kind", PROPOSAL_KIND)
                    .put("app_id", SOFTWARE_APP_ID)
                    .put("day_key", day)
                    .put("priority", PRIORITY)
                    .put("sort_order", 10 + slot.index)
                    .put("context", proposalContextFor(ledger, slot, faces, now))
                    .put("result_schema", proposalSchema(slot))
            )
        }
        return jobs
    }

    /** Reads Claude's answer into a proposal. The full check happens when the offer is made. */
    fun parseProposal(result: JSONObject?): AiProposal? {
        if (result == null) return null
        fun s(key: String): String? = if (result.isNull(key)) null else result.opt(key) as? String
        val clientName = s("clientName") ?: return null
        val contactName = s("contactName") ?: return null
        val email = s("email") ?: return null
        val phone = s("phone") ?: return null
        val title = s("title") ?: return null
        val summary = s("summary") ?: return null
        val country = s("country") ?: ""
        val list = result.opt("requirements") as? JSONArray ?: return null
        val reqs = ArrayList<String>()
        for (i in 0 until list.length()) reqs.add(list.opt(i) as? String ?: return null)
        val weeks = result.opt("deliveryWeeks") as? Number ?: return null
        val fee = result.opt("feeUnits") as? Number ?: return null
        if (weeks.toDouble() != Math.floor(weeks.toDouble()) || fee.toDouble() != Math.floor(fee.toDouble())) return null
        return AiProposal(
            clientName = clientName, contactName = contactName, email = email, phone = phone,
            country = country, title = title, summary = summary, requirements = reqs,
            deliveryWeeks = weeks.toInt(), feeUnits = fee.toLong()
        )
    }
}
