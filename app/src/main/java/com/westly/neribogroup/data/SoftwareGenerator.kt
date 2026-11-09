package com.westly.neribogroup.data

import com.westly.neribogroup.ui.formatMoney
import java.util.UUID
import kotlin.random.Random

/**
 * Writes one new proposal. Everything about it (client, fee, requirements, dates) is decided here,
 * once, and stored whole, so rolling again or restoring a backup can never change it.
 * All names, companies, emails and phone numbers are made up.
 */
object SoftwareGenerator {

    private const val BUILD_MIN_NGN = 300_000L
    private const val BUILD_MAX_NGN = 5_000_000L
    private const val CODE_MIN_NGN = 1_000_000L
    private const val CODE_MAX_NGN = 50_000_000L

    private class Taken(
        val names: MutableSet<String>,
        val emails: MutableSet<String>,
        val phones: MutableSet<String>
    )

    /** The old entry point. Same random draws, in the same order, as before Phase 6. */
    fun createOffer(
        existingOffers: List<SoftwareOffer>,
        existingClients: List<SoftwareClient>,
        rates: Map<String, RateInfo>,
        number: Int,
        effectiveNow: Long,
        random: Random
    ): SoftwareOffer {
        val slot = planSlot(0, rates, random)
        return createOfferFromSlot(existingOffers, existingClients, rates, number, effectiveNow, random, slot)
    }

    /**
     * Steps 1 to 4 of the old createOffer (kind, project type, location, client type), with exactly the same
     * random draws. The price and weeks limits need no random numbers.
     */
    fun planSlot(index: Int, rates: Map<String, RateInfo>, random: Random): ProposalSlot {
        // 1. Kind
        val kindRoll = random.nextInt(100)
        val kind = when {
            kindRoll < 45 -> OfferKind.WEBSITE
            kindRoll < 70 -> OfferKind.WEB_APP
            kindRoll < 95 -> OfferKind.ANDROID_APP
            else -> OfferKind.CODE_PURCHASE
        }

        // 2. Project type
        val type = if (kind == OfferKind.CODE_PURCHASE) {
            SOFTWARE_TYPES.random(random)
        } else {
            SOFTWARE_TYPES.filter { it.kind == kind }.random(random)
        }

        // 3. Location
        val foreign: SoftwareCountry? =
            if (random.nextInt(100) < 70) null else FOREIGN_COUNTRIES.random(random)
        val countryName = foreign?.name ?: NIGERIA
        val currency = foreign?.currency ?: "NGN"

        // 4. Client type
        val clientType = when {
            type.allowCompany && type.allowIndividual ->
                if (random.nextInt(100) < 60) ClientType.COMPANY else ClientType.INDIVIDUAL
            type.allowCompany -> ClientType.COMPANY
            else -> ClientType.INDIVIDUAL
        }

        val isCode = kind == OfferKind.CODE_PURCHASE
        val band = feeBand(isCode, currency, rates)
        return ProposalSlot(
            index = index,
            kind = kind,
            typeCode = type.code,
            clientType = clientType,
            country = countryName,
            currency = currency,
            minUnits = band.first,
            maxUnits = band.second,
            stepUnits = if (currency == "NGN") AI_NGN_STEP else AI_FOREIGN_STEP,
            minWeeks = if (isCode) 1 else 2,
            maxWeeks = if (isCode) 2 else 12
        )
    }

    private const val AI_NGN_STEP = 5_000L
    private const val AI_FOREIGN_STEP = 50L

    /** The lowest and highest fee in [currency] units that Claude may choose. */
    fun feeBand(isCode: Boolean, currency: String, rates: Map<String, RateInfo>): Pair<Long, Long> {
        val low = if (isCode) CODE_MIN_NGN else BUILD_MIN_NGN
        val high = if (isCode) CODE_MAX_NGN else BUILD_MAX_NGN
        if (currency == "NGN") return Pair(low, high)
        val step = AI_FOREIGN_STEP
        var min = maxOf(step, Math.ceil(SoftwareEngine.unitsFromNgn(low, currency, rates) / step).toLong() * step)
        var guard = 0
        while (SoftwareEngine.ngnEstimateFor(min, currency, rates) < low && guard < 5000) {
            min += step
            guard++
        }
        while (min > step && SoftwareEngine.ngnEstimateFor(min - step, currency, rates) >= low && guard < 10000) {
            min -= step
            guard++
        }
        var max = Math.floor(SoftwareEngine.unitsFromNgn(high, currency, rates) / step).toLong() * step
        while (max > min && SoftwareEngine.ngnEstimateFor(max, currency, rates) > high && guard < 20000) {
            max -= step
            guard++
        }
        if (max < min) max = min
        return Pair(min, max)
    }

    /** Steps 5 to the end of the old createOffer, for a slot that was already decided. */
    fun createOfferFromSlot(
        existingOffers: List<SoftwareOffer>,
        existingClients: List<SoftwareClient>,
        rates: Map<String, RateInfo>,
        number: Int,
        effectiveNow: Long,
        random: Random,
        slot: ProposalSlot
    ): SoftwareOffer {
        val taken = Taken(HashSet(), HashSet(), HashSet())
        for (o in existingOffers) {
            taken.names.add(o.clientName.trim().lowercase())
            taken.emails.add(o.email.trim().lowercase())
            taken.phones.add(digitsOf(o.phone))
        }
        for (c in existingClients) {
            taken.names.add(c.name.trim().lowercase())
            taken.emails.add(c.email.trim().lowercase())
            taken.phones.add(digitsOf(c.phone))
        }
        val kind = slot.kind
        val type = SOFTWARE_TYPES.firstOrNull { it.code == slot.typeCode }
            ?: SOFTWARE_TYPES.first { it.kind == kind || kind == OfferKind.CODE_PURCHASE }
        val foreign: SoftwareCountry? = FOREIGN_COUNTRIES.firstOrNull { it.name == slot.country }
        val countryName = foreign?.name ?: NIGERIA
        val currency = foreign?.currency ?: "NGN"
        val clientType = slot.clientType

        // 5. Names and contact details
        val clientName: String
        val contactName: String
        val email: String
        if (clientType == ClientType.COMPANY) {
            clientName = uniqueCompanyName(foreign, taken, random)
            contactName = personName(foreign, random, 0)
            email = "info@" + slugOf(clientName) + "." + (foreign?.emailTld ?: "com.ng")
        } else {
            clientName = uniquePersonName(foreign, taken, random)
            contactName = clientName
            email = uniquePersonEmail(clientName, taken, random)
        }
        taken.names.add(clientName.trim().lowercase())
        taken.emails.add(email.lowercase())
        val phone = uniquePhone(foreign, taken, random)

        // 6. Fee
        val isCode = kind == OfferKind.CODE_PURCHASE
        val target = nairaTarget(type, isCode, random)
        val feeUnits: Long
        val ngnEstimate: Long
        if (foreign == null) {
            feeUnits = target
            ngnEstimate = target
        } else {
            val low = if (isCode) CODE_MIN_NGN else BUILD_MIN_NGN
            val high = if (isCode) CODE_MAX_NGN else BUILD_MAX_NGN
            var units = Math.round(SoftwareEngine.unitsFromNgn(target, currency, rates) / 50.0) * 50L
            if (units < 50L) units = 50L
            var guard = 0
            while (SoftwareEngine.ngnEstimateFor(units, currency, rates) < low && guard < 5000) {
                units += 50L
                guard++
            }
            while (SoftwareEngine.ngnEstimateFor(units, currency, rates) > high && units > 50L && guard < 10000) {
                units -= 50L
                guard++
            }
            feeUnits = units
            ngnEstimate = SoftwareEngine.ngnEstimateFor(units, currency, rates)
        }

        // 7. Requirements and delivery time
        val pickedCount = random.nextInt(5, 8)
        val requirements = ArrayList<String>(type.pool.shuffled(random).take(pickedCount))
        if (isCode) {
            requirements.add("Payment: 50% when you accept, 50% on handover.")
            requirements.add("Full handoff. No maintenance after delivery.")
        } else {
            requirements.add(HOSTING_LINES.random(random))
            requirements.add(CONTENT_LINES.random(random))
            requirements.add("Payment: 50% when you accept, 50% on delivery.")
            requirements.add(
                "Monthly maintenance after delivery: " + formatMoney(feeUnits / 10, currency) +
                    " every 30 days."
            )
        }
        val deliveryWeeks = when {
            isCode -> random.nextInt(1, 3)
            ngnEstimate <= 700_000L -> random.nextInt(2, 4)
            ngnEstimate <= 2_000_000L -> random.nextInt(3, 7)
            else -> random.nextInt(6, 13)
        }

        // Title and summary
        val title = if (isCode) "Source code purchase: " + type.title else type.title
        val summary = if (isCode) {
            CODE_SUMMARIES.random(random)
                .replace("{client}", clientName)
                .replace("{type}", type.title.lowercase())
        } else {
            type.summaries.random(random)
                .replace("{client}", clientName)
                .replace("{country}", countryName)
        }

        // Dates: a random time today between 08:00 and now
        val dayStart = dayStartMillis(effectiveNow)
        val eight = dayStart + 8L * 3_600_000L
        val created = if (effectiveNow <= eight) {
            effectiveNow
        } else {
            minOf(effectiveNow, eight + random.nextLong(effectiveNow - eight + 1L))
        }

        return SoftwareOffer(
            id = UUID.randomUUID().toString(),
            ref = "SW-OFR-" + SoftwareEngine.yearOf(created) + "-" + number.toString().padStart(4, '0'),
            createdMillis = created,
            expiresMillis = created + SoftwareEngine.OFFER_VALID_DAYS * DAY_MILLIS,
            status = OfferStatus.PENDING,
            decidedMillis = 0L,
            clientType = clientType,
            clientName = clientName,
            contactName = contactName,
            email = email,
            phone = phone,
            country = countryName,
            currency = currency,
            kind = kind,
            title = title,
            summary = summary,
            requirements = requirements,
            deliveryWeeks = deliveryWeeks,
            feeUnits = feeUnits,
            ngnEstimate = ngnEstimate,
            writer = "App"
        )
    }

    /** A random time today between 08:00 and now. Draws one random number only when needed. */
    private fun createdTimeToday(effectiveNow: Long, random: Random): Long {
        val dayStart = dayStartMillis(effectiveNow)
        val eight = dayStart + 8L * 3_600_000L
        return if (effectiveNow <= eight) {
            effectiveNow
        } else {
            minOf(effectiveNow, eight + random.nextLong(effectiveNow - eight + 1L))
        }
    }

    private val EMAIL_RE = Regex("^[a-z0-9._-]+@[a-z0-9.-]+$")
    private val PHONE_RE = Regex("^[+]?[0-9]{8,15}$")
    private val BAD_WORDS = listOf("http", "www.", "lorem", "ipsum", "tbd", "xxx", "```", "**")

    private fun cleanText(t: String): String = t.trim().replace(Regex("\\s+"), " ")

    private fun hasBadChars(t: String): Boolean {
        if (t.any { it.isISOControl() }) return true
        val low = t.lowercase()
        return BAD_WORDS.any { low.contains(it) }
    }

    private fun nameOk(t: String, allowDigits: Boolean): Boolean {
        if (!t.all { it.isLetter() || it == ' ' || it == '\'' || it == '-' || it == '.' || it == '&' || it == ',' || (allowDigits && it.isDigit()) }) return false
        return !Regex("[0-9]{4,}").containsMatchIn(t)
    }

    /** True when [email] ends with one of the [allowed] domains (null = not checked). */
    private fun domainAllowed(email: String, allowed: List<String>?): Boolean {
        if (allowed == null) return true
        val domain = email.substringAfter('@').lowercase()
        return allowed.any { d -> domain == d.lowercase() || domain.endsWith("." + d.lowercase()) }
    }

    /**
     * Makes the proposal from what Claude wrote, or null when anything is wrong (the caller then uses the
     * built-in text for the same slot). Kind, client type, currency and country always come from [slot].
     */
    fun createOfferFromAi(
        slot: ProposalSlot,
        ai: AiProposal,
        existingOffers: List<SoftwareOffer>,
        existingClients: List<SoftwareClient>,
        rates: Map<String, RateInfo>,
        number: Int,
        effectiveNow: Long,
        random: Random,
        allowedDomains: List<String>? = null
    ): SoftwareOffer? {
        val clientName = cleanText(ai.clientName)
        val contactName = if (slot.clientType == ClientType.INDIVIDUAL) clientName else cleanText(ai.contactName)
        val email = ai.email.trim().lowercase()
        val phone = ai.phone.filter { it.isDigit() || it == '+' }
        val title = cleanText(ai.title)
        val summary = cleanText(ai.summary)
        val reqs = ai.requirements.map { cleanText(it) }

        if (clientName.length !in 3..80 || contactName.length !in 3..60) return null
        if (!nameOk(clientName, slot.clientType == ClientType.COMPANY) || !nameOk(contactName, false)) return null
        if (email.length > 80 || !EMAIL_RE.matches(email) || !email.substringAfter('@').contains('.')) return null
        if (!domainAllowed(email, allowedDomains)) return null
        if (!PHONE_RE.matches(phone)) return null
        if (title.length !in 8..100 || summary.length !in 40..400) return null
        if (listOf(title, summary, clientName, contactName).any { hasBadChars(it) }) return null
        if (reqs.size !in slot.minRequirements..slot.maxRequirements) return null
        if (reqs.any { it.length !in 8..120 || hasBadChars(it) }) return null
        if (reqs.map { it.lowercase() }.toSet().size != reqs.size) return null
        if (ai.deliveryWeeks !in slot.minWeeks..slot.maxWeeks) return null

        // Fee: inside the slot band and a whole step. If the rates moved, keep it inside the naira band.
        val isCode = slot.kind == OfferKind.CODE_PURCHASE
        var fee = ai.feeUnits
        if (fee <= 0L || fee % slot.stepUnits != 0L) return null
        if (fee < slot.minUnits || fee > slot.maxUnits) return null
        val band = feeBand(isCode, slot.currency, rates)
        fee = fee.coerceIn(band.first, band.second)
        if (fee % slot.stepUnits != 0L) {
            fee = (fee / slot.stepUnits) * slot.stepUnits
            if (fee < band.first) fee += slot.stepUnits
        }

        // Nobody else may already use this name, email or phone.
        val names = HashSet<String>()
        val emails = HashSet<String>()
        val phones = HashSet<String>()
        for (o in existingOffers) {
            names.add(o.clientName.trim().lowercase())
            emails.add(o.email.trim().lowercase())
            phones.add(digitsOf(o.phone))
        }
        for (c in existingClients) {
            names.add(c.name.trim().lowercase())
            emails.add(c.email.trim().lowercase())
            phones.add(digitsOf(c.phone))
        }
        if (clientName.lowercase() in names || email in emails || digitsOf(phone) in phones) return null
        if (existingOffers.any { it.title.trim().equals(title, ignoreCase = true) && it.clientName.equals(clientName, ignoreCase = true) }) return null

        val requirements = ArrayList<String>(reqs)
        if (isCode) {
            requirements.add("Payment: 50% when you accept, 50% on handover.")
            requirements.add("Full handoff. No maintenance after delivery.")
        } else {
            requirements.add("Payment: 50% when you accept, 50% on delivery.")
            requirements.add(
                "Monthly maintenance after delivery: " + formatMoney(fee / 10, slot.currency) +
                    " every 30 days."
            )
        }
        val created = createdTimeToday(effectiveNow, random)
        return SoftwareOffer(
            id = UUID.randomUUID().toString(),
            ref = "SW-OFR-" + SoftwareEngine.yearOf(created) + "-" + number.toString().padStart(4, '0'),
            createdMillis = created,
            expiresMillis = created + SoftwareEngine.OFFER_VALID_DAYS * DAY_MILLIS,
            status = OfferStatus.PENDING,
            decidedMillis = 0L,
            clientType = slot.clientType,
            clientName = clientName,
            contactName = contactName,
            email = email,
            phone = phone,
            country = slot.country,
            currency = slot.currency,
            kind = slot.kind,
            title = title,
            summary = summary,
            requirements = requirements,
            deliveryWeeks = ai.deliveryWeeks,
            feeUnits = fee,
            ngnEstimate = SoftwareEngine.ngnEstimateFor(fee, slot.currency, rates),
            writer = "Claude"
        )
    }

    /**
     * Part C: makes one proposal from what Claude wrote and the app has already checked ([claude] comes
     * from SoftwareAi.validate). The app-owned fields (id, ref, times, status, e-mail, phone, the payment,
     * hosting, content and maintenance lines) are made here exactly as createOfferFromSlot makes them.
     */
    fun buildFromClaude(
        claude: ClaudeOffer,
        existingOffers: List<SoftwareOffer>,
        existingClients: List<SoftwareClient>,
        number: Int,
        effectiveNow: Long,
        random: Random
    ): SoftwareOffer {
        val taken = Taken(HashSet(), HashSet(), HashSet())
        for (o in existingOffers) {
            taken.names.add(o.clientName.trim().lowercase())
            taken.emails.add(o.email.trim().lowercase())
            taken.phones.add(digitsOf(o.phone))
        }
        for (c in existingClients) {
            taken.names.add(c.name.trim().lowercase())
            taken.emails.add(c.email.trim().lowercase())
            taken.phones.add(digitsOf(c.phone))
        }
        val foreign: SoftwareCountry? = FOREIGN_COUNTRIES.firstOrNull { it.name == claude.country }
        val email = if (claude.clientType == ClientType.COMPANY) {
            "info@" + slugOf(claude.clientName) + "." + (foreign?.emailTld ?: "com.ng")
        } else {
            uniquePersonEmail(claude.clientName, taken, random)
        }
        val phone = uniquePhone(foreign, taken, random)

        val isCode = claude.kind == OfferKind.CODE_PURCHASE
        val requirements = ArrayList<String>(claude.requirements)
        if (isCode) {
            requirements.add("Payment: 50% when you accept, 50% on handover.")
            requirements.add("Full handoff. No maintenance after delivery.")
        } else {
            requirements.add(HOSTING_LINES.random(random))
            requirements.add(CONTENT_LINES.random(random))
            requirements.add("Payment: 50% when you accept, 50% on delivery.")
            requirements.add(
                "Monthly maintenance after delivery: " + formatMoney(claude.feeUnits / 10, claude.currency) +
                    " every 30 days."
            )
        }
        val prefix = "Source code purchase: "
        val title = if (isCode && !claude.title.startsWith(prefix, ignoreCase = true)) prefix + claude.title else claude.title
        val created = createdTimeToday(effectiveNow, random)
        return SoftwareOffer(
            id = UUID.randomUUID().toString(),
            ref = "SW-OFR-" + SoftwareEngine.yearOf(created) + "-" + number.toString().padStart(4, '0'),
            createdMillis = created,
            expiresMillis = created + SoftwareEngine.OFFER_VALID_DAYS * DAY_MILLIS,
            status = OfferStatus.PENDING,
            decidedMillis = 0L,
            clientType = claude.clientType,
            clientName = claude.clientName,
            contactName = claude.contactName,
            email = email,
            phone = phone,
            country = claude.country,
            currency = claude.currency,
            kind = claude.kind,
            title = title,
            summary = claude.summary,
            requirements = requirements,
            deliveryWeeks = claude.deliveryWeeks,
            feeUnits = claude.feeUnits,
            ngnEstimate = claude.ngnEstimate,
            writer = "Claude"
        )
    }

    // ---- Fee ----

    private fun uniform(low: Long, high: Long, random: Random): Long =
        low + random.nextLong(high - low + 1L)

    private fun roundTo(value: Long, step: Long): Long =
        Math.round(value / step.toDouble()) * step

    /** A random naira fee inside the project type's range (or the code range), already rounded. */
    private fun nairaTarget(type: SoftwareType, isCode: Boolean, random: Random): Long {
        if (!isCode) {
            val raw = roundTo(uniform(type.minNgn, type.maxNgn, random), 10_000L)
            return raw.coerceIn(type.minNgn, type.maxNgn)
        }
        val roll = random.nextInt(100)
        val raw = when {
            roll < 60 -> uniform(1_000_000L, 3_000_000L, random)
            roll < 85 -> uniform(3_000_000L, 8_000_000L, random)
            roll < 95 -> uniform(8_000_000L, 20_000_000L, random)
            else -> uniform(20_000_000L, 50_000_000L, random)
        }
        return roundTo(raw, 50_000L).coerceIn(CODE_MIN_NGN, CODE_MAX_NGN)
    }

    // ---- Names ----

    private fun digitsOf(text: String): String = text.filter { it.isDigit() }

    private fun slugOf(name: String): String =
        name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

    private fun firstNameOf(foreign: SoftwareCountry?, random: Random): String =
        if (foreign == null) NIGERIAN_FIRST_NAMES.random(random) else foreign.firstNames.random(random)

    private fun surnameOf(foreign: SoftwareCountry?, random: Random): String =
        if (foreign == null) NIGERIAN_SURNAMES.random(random) else foreign.surnames.random(random)

    /** A person's name. [attempt] above 40 adds a middle initial so there are always new names. */
    private fun personName(foreign: SoftwareCountry?, random: Random, attempt: Int): String {
        val first = firstNameOf(foreign, random)
        val last = surnameOf(foreign, random)
        if (attempt > 40) {
            val initial = ('A' + random.nextInt(26))
            return "$first $initial. $last"
        }
        return "$first $last"
    }

    private fun uniquePersonName(foreign: SoftwareCountry?, taken: Taken, random: Random): String {
        var attempt = 0
        while (true) {
            val name = personName(foreign, random, attempt)
            if (name.lowercase() !in taken.names) return name
            attempt++
            if (attempt > 2000) return name + " " + random.nextInt(10, 99)
        }
    }

    private fun uniqueCompanyName(foreign: SoftwareCountry?, taken: Taken, random: Random): String {
        var attempt = 0
        while (true) {
            val name = if (foreign == null) {
                NIGERIAN_COMPANY_PREFIXES.random(random) + " " +
                    NIGERIAN_COMPANY_SECTORS.random(random) + " " +
                    NIGERIAN_COMPANY_SUFFIXES.random(random)
            } else {
                ENGLISH_COMPANY_PREFIXES.random(random) + " " +
                    ENGLISH_COMPANY_SECTORS.random(random) + " " +
                    foreign.legalSuffixes.random(random)
            }
            if (name.lowercase() !in taken.names && "info@" + slugOf(name) !in taken.emails) return name
            attempt++
            if (attempt > 2000) return name + " " + random.nextInt(10, 99)
        }
    }

    /** firstname.lastname@gmail.com style, lower-case, with digits added when it is already used. */
    private fun uniquePersonEmail(fullName: String, taken: Taken, random: Random): String {
        val base = fullName.trim().lowercase()
            .replace(" ", ".")
            .filter { it in 'a'..'z' || it in '0'..'9' || it == '.' }
            .replace("..", ".")
        var candidate = "$base@gmail.com"
        var attempt = 0
        while (candidate.lowercase() in taken.emails) {
            attempt++
            candidate = base + random.nextInt(2, 100 + attempt * 10) + "@gmail.com"
        }
        return candidate
    }

    private fun uniquePhone(foreign: SoftwareCountry?, taken: Taken, random: Random): String {
        while (true) {
            val phone = if (foreign == null) {
                NIGERIAN_PHONE_PREFIXES.random(random) + randomDigits(8, random)
            } else {
                foreign.phonePrefix + randomDigits(foreign.phoneDigits, random)
            }
            val digits = digitsOf(phone)
            if (digits !in taken.phones) {
                taken.phones.add(digits)
                return phone
            }
        }
    }

    private fun randomDigits(count: Int, random: Random): String {
        val builder = StringBuilder()
        for (i in 0 until count) builder.append(random.nextInt(10))
        return builder.toString()
    }
}
