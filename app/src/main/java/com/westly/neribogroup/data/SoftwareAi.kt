package com.westly.neribogroup.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Phase 6: what the app decides for ONE proposal before Claude writes it. Claude never changes these.
 * (The existing generator never brings back an old client, so a slot never names a repeat client.)
 */
data class ProposalSlot(
    val index: Int,
    val kind: OfferKind,
    /** The project type (a [SoftwareType] code); used by the built-in text when Claude does not answer. */
    val typeCode: String,
    val clientType: ClientType,
    val country: String,
    val currency: String,
    val minUnits: Long,
    val maxUnits: Long,
    val stepUnits: Long,
    val minWeeks: Int,
    val maxWeeks: Int,
    val minRequirements: Int = 5,
    val maxRequirements: Int = 7
)

/** What Claude wrote for one proposal. It is checked again before it is used. */
data class AiProposal(
    val clientName: String,
    val contactName: String,
    val email: String,
    val phone: String,
    val country: String,
    val title: String,
    val summary: String,
    val requirements: List<String>,
    val deliveryWeeks: Int,
    val feeUnits: Long
)

/**
 * The answers of Claude for one software roll. [faces] = contacts dice, proposals dice (-1 = not given, the
 * random face is used). [slots] and [proposals] are by proposal number; a missing or null entry means the
 * built-in generator writes that proposal.
 */
data class SoftwarePreset(
    val faces: List<Int> = emptyList(),
    val slots: List<ProposalSlot?> = emptyList(),
    val proposals: List<AiProposal?> = emptyList(),
    /** The email domains the owner allows. Null = not checked here (the cloud already checks them). */
    val allowedDomains: List<String>? = null
)

// =================================================================================================
// Part C: Software Development Clients are rolled ONLY by Claude, with ONE job per day.
// =================================================================================================

/** One proposal written by Claude and already checked and tidied by [SoftwareAi.validate]. */
data class ClaudeOffer(
    val projectCode: String,
    val kind: OfferKind,
    val clientType: ClientType,
    val clientName: String,
    /** For an INDIVIDUAL this is the same as [clientName] (set by the app). */
    val contactName: String,
    val country: String,
    /** Derived by the app from [country]. */
    val currency: String,
    val title: String,
    val summary: String,
    val requirements: List<String>,
    /** Already clamped into the allowed band. */
    val deliveryWeeks: Int,
    /** Whole units of [currency], already rounded to a whole step. */
    val feeUnits: Long,
    val ngnEstimate: Long
)

/** The check of one SOFTWARE_ROLL answer. [offers] are the first [wanted] proposals, ready to be built. */
class SoftwareCheck(
    val ok: Boolean,
    val reason: String,
    val contactFace: Int = 0,
    val offerFace: Int = 0,
    val wanted: Int = 0,
    val offers: List<ClaudeOffer> = emptyList(),
    /** The extra contacts the followers brought today (already inside [wanted]). */
    val reachBonus: Int = 0
)

object SoftwareAi {

    const val KIND = "SOFTWARE_ROLL"

    /**
     * The cloud claims jobs by priority (smallest first), then sort_order, then creation time, with no day
     * filter. Dice and case jobs use 100 and the old software jobs 110, so 50 is always claimed first.
     */
    const val PRIORITY = 50
    const val SORT_ORDER = 0
    const val MAX_ATTEMPTS = 3
    /** The most proposals one day can ask for: the best dice (3 contacts) plus the biggest follower bonus. */
    const val MAX_OFFERS = SoftwareEngine.MAX_CONTACTS

    /**
     * How many proposals the backend rulebook already asked Claude to write before followers added contacts.
     * Claude must still write at least this many (or `wanted` when that is smaller); the built-in generator
     * writes any further proposal, so the app also works while the backend rulebook still says 3.
     */
    const val ASKED_OFFERS = 3

    /** The writing rules. The same text is in the backend rulebook. */
    const val INSTRUCTIONS =
        "1. Roll both dice honestly (0-5 each), then write exactly `wanted` proposals, where contacts = " +
            "contactsByFace[contactFace] + reachBonus (reachBonus is in the job context and is already final: " +
            "the extra contacts the company's social followers bring today; never roll or change it) and " +
            "wanted = min(contacts, (contacts*share+50)/100) and never more than inboxRoom and never more than 6. " +
            "wanted = 0 means an empty offers array. " +
            "2. Fit is mandatory. Pick a projectCode from projectTypes. The client must be the kind of " +
            "organisation or person that would really order that project, and the title, summary and " +
            "requirements must describe that same project for that same client. A church website comes from a " +
            "church, ministry or NGO, never a school or a tailor; a school website from a school; a hotel " +
            "website from a hotel or guesthouse; a clinic booking system from a clinic; a restaurant website " +
            "from a restaurant or caterer (an individual chef is allowed); a portfolio website from an " +
            "individual. Follow allowCompany and allowIndividual of the chosen type. " +
            "3. Names must be realistic for the country, different from recentClientNames and from each other. " +
            "Foreign clients use names natural to that country. No celebrities, no real brands, no real people. " +
            "4. Do not write any email address, phone number or email-like text anywhere; the app makes " +
            "contacts itself. Text must not contain the at sign or a web address. " +
            "5. fee is a whole number in units of the proposal's currency, a multiple of 50 (NGN: a multiple of " +
            "10000), so that units x ratesPerUsd-based naira value lies inside the project type's minNgn-maxNgn " +
            "(kind CODE_PURCHASE: 1000000-50000000 naira). " +
            "6. deliveryWeeks: code purchase 1-2; naira value up to 700000: 2-3; up to 2000000: 3-6; above: 6-12. " +
            "7. summary: 1-3 sentences, specific to the client. requirements: 5-7 short, concrete, " +
            "project-specific lines. Do not write payment, hosting, content or maintenance lines. " +
            "8. About 70% Nigerian clients (NGN) and 30% foreign (the currency of that country)."

    fun dedupeKey(day: String, attempt: Int): String = day + ":" + SOFTWARE_APP_ID + ":" + KIND + ":" + attempt

    /** The attempt number (1..3) of a SOFTWARE_ROLL dedupe key, or null. */
    fun attemptOfDedupeKey(key: String): Int? = key.substringAfterLast(':').toIntOrNull()

    // ---- The job ----

    private fun typeJson(t: SoftwareType): JSONObject = JSONObject()
        .put("code", t.code).put("title", t.title).put("kind", t.kind.name)
        .put("minNgn", t.minNgn).put("maxNgn", t.maxNgn)
        .put("allowCompany", t.allowCompany).put("allowIndividual", t.allowIndividual)

    /** What Claude sees. Built from the live catalogue and rates. */
    fun context(ledger: Ledger, now: Long): JSONObject {
        val sw = ledger.software
        val eff = SoftwareEngine.effectiveNow(ledger, now)
        val ctx = JSONObject()
        ctx.put("job", KIND)
        ctx.put("company", "Neribo Group")
        ctx.put("app", JSONObject().put("name", SOFTWARE_APP_NAME).put("id", SOFTWARE_APP_ID))
        ctx.put("day", dayKey(eff))
        ctx.put(
            "dice",
            JSONObject()
                .put("contactDie", JSONObject().put("faces", "0-5")
                    .put("contactsByFace", JSONArray(listOf(0, 1, 2, 3, 4, 5).map { SoftwareEngine.contactsForFace(it) })))
                .put("shareDie", JSONObject().put("faces", "0-5")
                    .put("sharePercentByFace", JSONArray(listOf(0, 1, 2, 3, 4, 5).map { SoftwareEngine.sharePercentForFace(it) })))
                .put(
                    "formula",
                    "contacts = contactsByFace[contactFace] + reach.reachBonus; " +
                        "wanted = min(contacts, (contacts*share + 50)/100); then at most inboxRoom"
                )
        )
        ctx.put(
            "reach",
            JSONObject()
                .put("reachBonus", SoftwareEngine.reachBonus(ledger, now))
                .put("totalFollowers", SoftwareEngine.totalFollowers(ledger))
                .put("followersPerContact", SoftwareEngine.FOLLOWERS_PER_CONTACT)
                .put("maxReachBonus", SoftwareEngine.MAX_REACH_BONUS)
        )
        ctx.put("inboxRoom", SoftwareEngine.roomFor(ledger, now))
        val types = JSONArray()
        for (t in SOFTWARE_TYPES) types.put(typeJson(t))
        ctx.put("projectTypes", types)
        ctx.put(
            "codePurchase",
            JSONObject().put("minNgn", CODE_MIN_NGN).put("maxNgn", CODE_MAX_NGN)
                .put("note", "kind CODE_PURCHASE: any projectCode; the client buys the finished source code")
        )
        val countries = JSONArray()
        countries.put(JSONObject().put("name", NIGERIA).put("currency", "NGN"))
        for (c in FOREIGN_COUNTRIES) countries.put(JSONObject().put("name", c.name).put("currency", c.currency))
        ctx.put("countries", countries)
        val rates = JSONObject()
        for (code in SOFTWARE_CURRENCIES) rates.put(code, SoftwareEngine.perUsd(code, sw.rates))
        ctx.put("ratesPerUsd", rates)
        val names = LinkedHashSet<String>()
        for (o in sw.offers.sortedByDescending { it.createdMillis }) names.add(o.clientName)
        for (c in sw.clients) names.add(c.name)
        val recent = JSONArray()
        for (n in names.take(40)) recent.put(n)
        ctx.put("recentClientNames", recent)
        ctx.put("instructions", INSTRUCTIONS)
        return ctx
    }

    /** The schema of the answer, built from the live catalogue so it never drifts. */
    fun resultSchema(): JSONObject {
        fun enumOf(values: List<String>): JSONArray {
            val a = JSONArray()
            for (v in values) a.put(v)
            return a
        }
        fun str(min: Int, max: Int) = JSONObject().put("type", "string").put("minLength", min).put("maxLength", max)
        fun int(min: Long, max: Long) = JSONObject().put("type", "integer").put("minimum", min).put("maximum", max)

        val offerProps = JSONObject()
            .put("projectCode", JSONObject().put("type", "string").put("enum", enumOf(SOFTWARE_TYPES.map { it.code })))
            .put("kind", JSONObject().put("type", "string").put("enum", enumOf(OfferKind.values().map { it.name })))
            .put("clientType", JSONObject().put("type", "string").put("enum", enumOf(ClientType.values().map { it.name })))
            .put("clientName", str(2, 80))
            .put("contactName", str(3, 60))
            .put("country", JSONObject().put("type", "string")
                .put("enum", enumOf(listOf(NIGERIA) + FOREIGN_COUNTRIES.map { it.name })))
            .put("title", str(8, 90))
            .put("summary", str(40, 400))
            .put("requirements", JSONObject().put("type", "array").put("minItems", 5).put("maxItems", 7)
                .put("items", str(8, 140)))
            .put("deliveryWeeks", int(1, 12))
            .put("fee", int(50, 2_000_000_000L))
        val offer = JSONObject()
            .put("type", "object")
            .put("properties", offerProps)
            .put("required", enumOf(listOf(
                "projectCode", "kind", "clientType", "clientName", "contactName", "country",
                "title", "summary", "requirements", "deliveryWeeks", "fee"
            )))
            .put("additionalProperties", false)
        return JSONObject()
            .put("type", "object")
            .put(
                "properties",
                JSONObject()
                    .put("contactFace", int(0, 5))
                    .put("offerFace", int(0, 5))
                    .put("comment", JSONObject().put("type", "string").put("maxLength", 200))
                    .put("offers", JSONObject().put("type", "array").put("maxItems", MAX_OFFERS).put("items", offer))
            )
            .put("required", enumOf(listOf("contactFace", "offerFace", "offers")))
            .put("additionalProperties", false)
    }

    /** The job for [attempt] (1..3) of today. */
    fun buildJob(ledger: Ledger, now: Long, attempt: Int): JSONObject {
        val day = dayKey(SoftwareEngine.effectiveNow(ledger, now))
        return JSONObject()
            .put("dedupe_key", dedupeKey(day, attempt))
            .put("kind", KIND)
            .put("app_id", SOFTWARE_APP_ID)
            .put("day_key", day)
            .put("priority", PRIORITY)
            .put("sort_order", SORT_ORDER)
            .put("context", context(ledger, now))
            .put("result_schema", resultSchema())
    }

    // ---- Checking an answer (the server schema is not trusted alone) ----

    private const val CODE_MIN_NGN = 1_000_000L
    private const val CODE_MAX_NGN = 50_000_000L

    private class BadOffer(message: String) : Exception(message)

    /**
     * Sector words. A sector's words are allowed only when the chosen project type belongs to that sector.
     * Whole-word, lower-case match on the client name, title and summary. Conservative on purpose: tune
     * the lists here. A type that is not listed in [TYPE_SECTORS] allows no sector word at all.
     */
    private val SECTOR_WORDS: Map<String, List<String>> = mapOf(
        "church" to listOf("church", "chapel", "ministry", "ministries", "cathedral", "parish", "assembly", "tabernacle"),
        "school" to listOf("school", "schools", "academy", "college", "nursery"),
        "health" to listOf("clinic", "hospital", "pharmacy", "diagnostic"),
        "restaurant" to listOf("restaurant", "kitchen", "eatery", "catering", "cafe"),
        "hotel" to listOf("hotel", "lodge", "guesthouse", "suites", "resort"),
        "realestate" to listOf("realty", "properties", "estate agents"),
        "logistics" to listOf("logistics", "haulage", "courier", "freight", "dispatch"),
        "fintech" to listOf("microfinance", "fintech", "payments", "bank"),
        "fashion" to listOf("tailor", "tailoring", "fashion", "couture", "boutique")
    )

    private val TYPE_SECTORS: Map<String, Set<String>> = mapOf(
        "W3" to setOf("school"), "A3" to setOf("school"),
        "W4" to setOf("church"),
        "W5" to setOf("restaurant"),
        "W6" to setOf("realestate"),
        "W7" to setOf("fashion"),
        "W8" to setOf("hotel"),
        "A1" to setOf("health"),
        "A4" to setOf("logistics"), "D1" to setOf("logistics"),
        "A6" to setOf("fintech")
    )

    /** The sector that a word of [text] belongs to when it does not fit the type, or null when all fit. */
    private fun sectorClash(typeCode: String, text: String): String? {
        val words = text.lowercase().replace(Regex("[^a-z]+"), " ").trim()
        val padded = " $words "
        val allowed = TYPE_SECTORS[typeCode] ?: emptySet()
        for ((sector, list) in SECTOR_WORDS) {
            if (sector in allowed) continue
            for (w in list) if (padded.contains(" $w ")) return w
        }
        return null
    }

    private val URL_LIKE = Regex("[a-z0-9-]+\\.(com|net|org|ng|io|co|uk|biz|info|app|dev)\\b", RegexOption.IGNORE_CASE)

    private fun badText(t: String): Boolean {
        if (t.any { it.isISOControl() }) return true
        val low = t.lowercase()
        if (low.contains('@') || low.contains("http") || low.contains("www.") || low.contains("```") || low.contains("**")) return true
        return URL_LIKE.containsMatchIn(t)
    }

    private fun nameOk(t: String, allowDigits: Boolean): Boolean {
        if (!t.all { it.isLetter() || it == ' ' || it == '\'' || it == '-' || it == '.' || it == '&' || it == ',' || (allowDigits && it.isDigit()) }) return false
        return !Regex("[0-9]{4,}").containsMatchIn(t)
    }

    private fun clean(t: String): String = t.trim().replace(Regex("\\s+"), " ")

    private fun stringOf(o: JSONObject, key: String): String {
        val v = o.opt(key)
        if (v !is String) throw BadOffer("$key is not text")
        return clean(v)
    }

    private fun wholeNumber(v: Any?, name: String): Long {
        val n = v as? Number ?: throw BadOffer("$name is not a number")
        val d = n.toDouble()
        if (d != Math.floor(d) || Math.abs(d) > 4_000_000_000.0) throw BadOffer("$name is not a whole number")
        return n.toLong()
    }

    private fun faceOf(v: Any?, name: String): Int {
        val n = wholeNumber(v, name)
        if (n !in 0L..5L) throw BadOffer("$name is not 0 to 5")
        return n.toInt()
    }

    /**
     * Checks Claude's answer against the live [ledger]. Never trusts the server alone. A proposal count
     * above what the dice allow is cut to the first `wanted`; fewer is rejected. A fee that is only off
     * the step is rounded; one outside the range is rejected. Delivery weeks are clamped into their band.
     */
    fun validate(ledger: Ledger, result: JSONObject?, now: Long): SoftwareCheck {
        if (result == null) return SoftwareCheck(false, "no answer")
        try {
            val contactFace = faceOf(result.opt("contactFace"), "contactFace")
            val offerFace = faceOf(result.opt("offerFace"), "offerFace")
            val bonus = SoftwareEngine.reachBonus(ledger, now)
            val wanted = minOf(
                SoftwareEngine.wantedFor(contactFace, offerFace, bonus),
                SoftwareEngine.roomFor(ledger, now),
                MAX_OFFERS
            )
            val array = result.optJSONArray("offers") ?: throw BadOffer("offers is missing")
            // Claude must write what the rulebook asked for; the app writes any proposal beyond that itself.
            if (array.length() < minOf(wanted, ASKED_OFFERS)) throw BadOffer("too few proposals")
            val take = minOf(wanted, array.length())
            val sw = ledger.software
            val usedNames = HashSet<String>()
            for (o in sw.offers) usedNames.add(o.clientName.trim().lowercase())
            for (c in sw.clients) usedNames.add(c.name.trim().lowercase())
            val offers = ArrayList<ClaudeOffer>()
            for (i in 0 until take) {
                val o = array.optJSONObject(i) ?: throw BadOffer("proposal " + (i + 1) + " is not an object")
                offers.add(checkOffer(o, sw.rates, usedNames))
            }
            return SoftwareCheck(true, "", contactFace, offerFace, wanted, offers, bonus)
        } catch (e: BadOffer) {
            return SoftwareCheck(false, (e.message ?: "not valid").take(150))
        }
    }

    private fun checkOffer(o: JSONObject, rates: Map<String, RateInfo>, usedNames: MutableSet<String>): ClaudeOffer {
        val code = stringOf(o, "projectCode")
        val type = SOFTWARE_TYPES.firstOrNull { it.code == code } ?: throw BadOffer("unknown projectCode $code")
        val kind = OfferKind.values().firstOrNull { it.name == stringOf(o, "kind") } ?: throw BadOffer("unknown kind")
        if (kind != type.kind && kind != OfferKind.CODE_PURCHASE) throw BadOffer("kind does not fit $code")
        val clientType = ClientType.values().firstOrNull { it.name == stringOf(o, "clientType") }
            ?: throw BadOffer("unknown clientType")
        if (clientType == ClientType.COMPANY && !type.allowCompany) throw BadOffer("$code does not take a company")
        if (clientType == ClientType.INDIVIDUAL && !type.allowIndividual) throw BadOffer("$code does not take an individual")

        val clientName = stringOf(o, "clientName")
        val contactName = if (clientType == ClientType.INDIVIDUAL) clientName else stringOf(o, "contactName")
        val minName = if (clientType == ClientType.INDIVIDUAL) 3 else 2
        if (clientName.length !in minName..(if (clientType == ClientType.INDIVIDUAL) 60 else 80)) throw BadOffer("clientName length")
        if (contactName.length !in 3..60) throw BadOffer("contactName length")
        if (!nameOk(clientName, clientType == ClientType.COMPANY) || !nameOk(contactName, false)) throw BadOffer("name characters")

        val country = stringOf(o, "country")
        val foreign = FOREIGN_COUNTRIES.firstOrNull { it.name == country }
        if (country != NIGERIA && foreign == null) throw BadOffer("unknown country")
        val currency = foreign?.currency ?: "NGN"

        val title = stringOf(o, "title")
        val summary = stringOf(o, "summary")
        if (title.length !in 8..90) throw BadOffer("title length")
        if (summary.length !in 40..400) throw BadOffer("summary length")
        val reqArray = o.optJSONArray("requirements") ?: throw BadOffer("requirements missing")
        if (reqArray.length() !in 5..7) throw BadOffer("requirements count")
        val reqs = ArrayList<String>()
        for (j in 0 until reqArray.length()) {
            val v = reqArray.opt(j) as? String ?: throw BadOffer("requirement is not text")
            val line = clean(v)
            if (line.length !in 8..140) throw BadOffer("requirement length")
            reqs.add(line)
        }
        if (reqs.map { it.lowercase() }.toSet().size != reqs.size) throw BadOffer("repeated requirement")
        for (t in listOf(clientName, contactName, title, summary) + reqs) {
            if (badText(t)) throw BadOffer("text has an at sign, web address or markup")
        }

        // Sector fit: client name, title and summary may only use words of this project's own sector.
        val clash = sectorClash(code, "$clientName $title $summary")
        if (clash != null) throw BadOffer("'$clash' does not fit project $code")

        val lowerName = clientName.lowercase()
        if (!usedNames.add(lowerName)) throw BadOffer("client name already used")

        // Fee: round to the nearest step, then it must lie inside the naira range.
        val raw = wholeNumber(o.opt("fee"), "fee")
        if (raw < 50L) throw BadOffer("fee too small")
        val step = if (currency == "NGN") 10_000L else 50L
        val fee = maxOf(step, ((raw + step / 2L) / step) * step)
        val ngn = SoftwareEngine.ngnEstimateFor(fee, currency, rates)
        val isCode = kind == OfferKind.CODE_PURCHASE
        val low = if (isCode) CODE_MIN_NGN else type.minNgn
        val high = if (isCode) CODE_MAX_NGN else type.maxNgn
        if (ngn < low || ngn > high) throw BadOffer("fee is outside $low-$high naira")

        val weeksRaw = wholeNumber(o.opt("deliveryWeeks"), "deliveryWeeks")
        if (weeksRaw !in 1L..12L) throw BadOffer("deliveryWeeks")
        val band = when {
            isCode -> Pair(1, 2)
            ngn <= 700_000L -> Pair(2, 3)
            ngn <= 2_000_000L -> Pair(3, 6)
            else -> Pair(6, 12)
        }
        val weeks = weeksRaw.toInt().coerceIn(band.first, band.second)

        return ClaudeOffer(
            projectCode = code, kind = kind, clientType = clientType, clientName = clientName,
            contactName = contactName, country = country, currency = currency, title = title,
            summary = summary, requirements = reqs, deliveryWeeks = weeks, feeUnits = fee, ngnEstimate = ngn
        )
    }

    // ---- Applying ----

    /**
     * Makes today's software roll from Claude's answer, exactly like SoftwareEngine.roll does for its own
     * dice. Returns null (nothing changes) when today was already rolled or the answer is not valid on the
     * live ledger, so the same day can never create proposals twice.
     */
    fun applyClaudeRoll(ledger: Ledger, result: JSONObject?, now: Long, random: Random): Ledger? {
        if (!SoftwareEngine.canRoll(ledger, now)) return null
        val check = validate(ledger, result, now)
        if (!check.ok) return null
        val sw = ledger.software
        val eff = SoftwareEngine.effectiveNow(ledger, now)
        val today = dayKey(eff)

        var offers = sw.offers
        var number = sw.nextOfferNumber
        for (claude in check.offers) {
            val offer = SoftwareGenerator.buildFromClaude(
                claude = claude,
                existingOffers = offers,
                existingClients = sw.clients,
                number = number,
                effectiveNow = eff,
                random = random
            )
            offers = offers + offer
            number++
        }
        // Claude wrote fewer than the dice and followers allow (the old rulebook asks for at most 3):
        // the built-in generator writes the rest.
        var created = check.offers.size
        while (created < check.wanted) {
            val extra = SoftwareGenerator.createOffer(
                existingOffers = offers,
                existingClients = sw.clients,
                rates = sw.rates,
                number = number,
                effectiveNow = eff,
                random = random
            )
            offers = offers + extra
            number++
            created++
        }
        val contacts = SoftwareEngine.contactsForFace(check.contactFace) + check.reachBonus
        val diceWanted = SoftwareEngine.wantedFor(check.contactFace, check.offerFace, check.reachBonus)
        val skipped = diceWanted - created
        val note = if (skipped > 0) {
            "Inbox full: $skipped proposal" + (if (skipped == 1) "" else "s") + " could not be added."
        } else {
            ""
        }
        val record = SoftwareRoll(
            dayKey = today,
            atMillis = eff,
            contactFace = check.contactFace,
            offerFace = check.offerFace,
            contacts = contacts,
            offers = created,
            note = note,
            byClaude = true,
            reachBonus = check.reachBonus
        )
        return ledger.copy(
            software = sw.copy(
                offers = SoftwareEngine.pruneOffers(offers, sw.projects, eff),
                nextOfferNumber = number,
                lastRollDayKey = today,
                rolls = (listOf(record) + sw.rolls).take(SoftwareEngine.KEEP_ROLLS),
                clockMillis = maxOf(sw.clockMillis, eff)
            )
        )
    }
}
