package com.westly.neribogroup.data

import com.westly.neribogroup.ui.formatRate
import com.westly.neribogroup.ui.formatUsd
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.random.Random

/**
 * Every rule of the Software Development Clients app. All functions are pure on [Ledger]:
 * they return the SAME instance when nothing changes, so the caller can skip saving.
 *
 * "now" is always run through [effectiveNow], the highest time ever seen, so setting the phone
 * clock back cannot re-open a roll day, un-expire a proposal or hide maintenance that is due.
 */
object SoftwareEngine {

    const val OFFER_VALID_DAYS = 7L
    const val MAINTENANCE_DAYS = 30L
    const val MAX_WAITING = 10
    const val KEEP_PAST_OFFERS = 100
    const val KEEP_ROLLS = 30
    const val KEEP_USD_TX = 200
    private const val MAINTENANCE_MILLIS = MAINTENANCE_DAYS * DAY_MILLIS

    /** Die 1: how many clients contacted you, by face 0..5. */
    private val CONTACTS_BY_FACE = intArrayOf(0, 0, 1, 1, 2, 3)

    /** Die 2: what share of them send a real proposal, in percent, by face 0..5. */
    private val SHARE_BY_FACE = intArrayOf(0, 25, 50, 50, 75, 100)

    // ---- Time ----

    fun effectiveNow(ledger: Ledger, now: Long): Long = maxOf(now, ledger.software.clockMillis)

    /** Raises the saved clock to [now] when it is higher. Returns the same ledger when nothing changes. */
    fun withClockAtLeast(ledger: Ledger, now: Long): Ledger =
        if (now > ledger.software.clockMillis) {
            ledger.copy(software = ledger.software.copy(clockMillis = now))
        } else {
            ledger
        }

    fun yearOf(millis: Long): Int =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).year

    // ---- Derived values ----

    fun offerStatusAt(offer: SoftwareOffer, effectiveNow: Long): OfferStatus {
        if (offer.status == OfferStatus.ACCEPTED || offer.status == OfferStatus.DECLINED) return offer.status
        return if (effectiveNow >= offer.expiresMillis) OfferStatus.EXPIRED else OfferStatus.PENDING
    }

    fun waitingCount(ledger: Ledger, effectiveNow: Long): Int =
        ledger.software.offers.count { offerStatusAt(it, effectiveNow) == OfferStatus.PENDING }

    /** Whole calendar days left before a proposal runs out. Never below 0. */
    fun offerDaysLeft(offer: SoftwareOffer, effectiveNow: Long): Long =
        maxOf(0L, calendarDaysLeft(effectiveNow, offer.expiresMillis))

    fun maintenancePaidCount(project: SoftwareProject): Int =
        project.payments.count { it.purpose == PaymentPurpose.MAINTENANCE }

    fun maintenancePeriodsDue(project: SoftwareProject, effectiveNow: Long): Int {
        if (!project.maintenanceActive || project.deliveredMillis <= 0L) return 0
        val elapsed = effectiveNow - project.deliveredMillis
        if (elapsed <= 0L) return 0
        return (elapsed / MAINTENANCE_MILLIS).toInt()
    }

    fun maintenanceOwed(project: SoftwareProject, effectiveNow: Long): Int =
        maxOf(0, maintenancePeriodsDue(project, effectiveNow) - maintenancePaidCount(project))

    /** When the next unpaid maintenance month is (or was) due. */
    fun maintenanceNextDueMillis(project: SoftwareProject): Long =
        project.deliveredMillis + (maintenancePaidCount(project) + 1) * MAINTENANCE_MILLIS

    fun maintenanceOwedTotal(ledger: Ledger, effectiveNow: Long): Int =
        ledger.software.projects.sumOf { maintenanceOwed(it, effectiveNow) }

    /** Everything received for a project, in the project's currency. */
    fun projectReceivedUnits(project: SoftwareProject): Long = project.payments.sumOf { it.units }

    /** Everything received for a project in naira at the time of each payment (foreign ones are estimates). */
    fun projectReceivedNgn(project: SoftwareProject): Long = project.payments.sumOf { it.ngnAtReceipt }

    fun projectExpenses(ledger: Ledger, projectId: String): List<Entry> =
        ledger.company.expenses
            .filter { it.projectId == projectId }
            .sortedByDescending { it.dateMillis }

    // ---- Money helpers (rates are units of the currency per 1 USD) ----

    fun perUsd(currency: String, rates: Map<String, RateInfo>): Double {
        if (currency == "USD") return 1.0
        val fallback = DEFAULT_RATES[currency]?.perUsd ?: 1.0
        val saved = rates[currency]?.perUsd ?: return fallback
        return if (saved > 0.0 && !saved.isNaN() && !saved.isInfinite()) saved else fallback
    }

    fun usdCentsFor(units: Long, currency: String, rates: Map<String, RateInfo>): Long {
        if (currency == "USD") return units * 100L
        return Math.round(units * 100.0 / perUsd(currency, rates))
    }

    fun ngnEstimateFor(units: Long, currency: String, rates: Map<String, RateInfo>): Long =
        Math.round(units / perUsd(currency, rates) * perUsd("NGN", rates))

    fun unitsFromNgn(ngn: Long, currency: String, rates: Map<String, RateInfo>): Double =
        ngn / perUsd("NGN", rates) * perUsd(currency, rates)

    /** True when [value] is an allowed rate for [code]. USD can never be changed. */
    fun isValidRate(code: String, value: Double): Boolean {
        if (code == "USD" || code !in SOFTWARE_CURRENCIES) return false
        if (value.isNaN() || value.isInfinite() || value <= 0.0) return false
        if (code == "NGN" && (value < NGN_RATE_MIN || value > NGN_RATE_MAX)) return false
        return true
    }

    /** Every supported currency has a rate: missing ones take the default. USD is always 1.0. */
    fun completeRates(rates: Map<String, RateInfo>): Map<String, RateInfo> {
        val result = LinkedHashMap<String, RateInfo>()
        for (code in SOFTWARE_CURRENCIES) {
            val saved: RateInfo? = rates[code]
            val fallback: RateInfo = DEFAULT_RATES.getValue(code)
            if (code == "USD" || saved == null || !isValidRate(code, saved.perUsd)) {
                result[code] = fallback
            } else {
                result[code] = saved
            }
        }
        return result
    }

    // ---- The dice ----

    fun contactsForFace(face: Int): Int = CONTACTS_BY_FACE[face.coerceIn(0, 5)]

    fun sharePercentForFace(face: Int): Int = SHARE_BY_FACE[face.coerceIn(0, 5)]

    /** offers = floor(contacts x share + 0.5), never more than contacts. */
    fun offersFor(contacts: Int, sharePercent: Int): Int =
        minOf(contacts, (contacts * sharePercent + 50) / 100)

    /** One roll per calendar day, by the highest time ever seen. */
    fun canRoll(ledger: Ledger, now: Long): Boolean =
        dayKey(effectiveNow(ledger, now)) > ledger.software.lastRollDayKey

    fun roll(ledger: Ledger, now: Long, random: Random): Ledger {
        val sw = ledger.software
        val eff = effectiveNow(ledger, now)
        val today = dayKey(eff)
        if (today <= sw.lastRollDayKey) return ledger

        val contactFace = random.nextInt(0, 6)
        val offerFace = random.nextInt(0, 6)
        val contacts = contactsForFace(contactFace)
        val wanted = offersFor(contacts, sharePercentForFace(offerFace))

        var waiting = waitingCount(ledger, eff)
        var offers = sw.offers
        var number = sw.nextOfferNumber
        var created = 0
        var skipped = 0
        for (i in 0 until wanted) {
            if (waiting >= MAX_WAITING) {
                skipped++
            } else {
                val offer = SoftwareGenerator.createOffer(
                    existingOffers = offers,
                    existingClients = sw.clients,
                    rates = sw.rates,
                    number = number,
                    effectiveNow = eff,
                    random = random
                )
                offers = offers + offer
                number++
                waiting++
                created++
            }
        }

        val note = if (skipped > 0) {
            "Inbox full: $skipped proposal" + (if (skipped == 1) "" else "s") + " could not be added."
        } else {
            ""
        }
        val record = SoftwareRoll(
            dayKey = today,
            atMillis = eff,
            contactFace = contactFace,
            offerFace = offerFace,
            contacts = contacts,
            offers = created,
            note = note
        )
        return ledger.copy(
            software = sw.copy(
                offers = pruneOffers(offers, sw.projects, eff),
                nextOfferNumber = number,
                lastRollDayKey = today,
                rolls = (listOf(record) + sw.rolls).take(KEEP_ROLLS),
                clockMillis = maxOf(sw.clockMillis, eff)
            )
        )
    }

    /** Keeps at most 100 closed proposals. A proposal that has a project is never dropped. */
    private fun pruneOffers(
        offers: List<SoftwareOffer>,
        projects: List<SoftwareProject>,
        effectiveNow: Long
    ): List<SoftwareOffer> {
        val closed = offers.filter { offerStatusAt(it, effectiveNow) != OfferStatus.PENDING }
        if (closed.size <= KEEP_PAST_OFFERS) return offers
        val removable = closed
            .filter { offer -> projects.none { it.offerId == offer.id } }
            .sortedBy { it.createdMillis }
        val drop = removable.take(closed.size - KEEP_PAST_OFFERS).map { it.id }.toSet()
        return offers.filter { it.id !in drop }
    }

    // ---- Accept and decline ----

    fun acceptOffer(ledger: Ledger, offerId: String, now: Long): Ledger {
        val sw = ledger.software
        val eff = effectiveNow(ledger, now)
        val offer = sw.offers.firstOrNull { it.id == offerId } ?: return ledger
        if (offer.status != OfferStatus.PENDING) return ledger
        if (eff >= offer.expiresMillis) return ledger
        if (sw.projects.any { it.offerId == offerId }) return ledger
        if (ledger.apps.none { it.id == SOFTWARE_APP_ID }) return ledger

        val projectId = "prj-$offerId"
        val clientId = "cl-$offerId"
        if (sw.projects.any { it.id == projectId }) return ledger

        val client = SoftwareClient(
            id = clientId,
            type = offer.clientType,
            name = offer.clientName,
            contactName = offer.contactName,
            email = offer.email,
            phone = offer.phone,
            country = offer.country,
            currency = offer.currency,
            sinceMillis = eff
        )
        val project = SoftwareProject(
            id = projectId,
            offerId = offerId,
            clientId = clientId,
            ref = "SW-" + yearOf(eff) + "-" + sw.nextProjectNumber.toString().padStart(4, '0'),
            clientName = offer.clientName,
            title = offer.title,
            summary = offer.summary,
            requirements = offer.requirements,
            kind = offer.kind,
            currency = offer.currency,
            feeUnits = offer.feeUnits,
            stage = ProjectStage.ACCEPTED,
            createdMillis = eff,
            deliveredMillis = 0L,
            payments = emptyList(),
            maintenanceActive = false,
            maintenanceUnits = 0L,
            maintenanceStoppedMillis = 0L
        )
        val clients = if (sw.clients.any { it.id == clientId }) sw.clients else sw.clients + client
        val base = ledger.copy(
            software = sw.copy(
                offers = sw.offers.map {
                    if (it.id == offerId) it.copy(status = OfferStatus.ACCEPTED, decidedMillis = eff) else it
                },
                projects = sw.projects + project,
                clients = clients,
                nextProjectNumber = sw.nextProjectNumber + 1,
                clockMillis = maxOf(sw.clockMillis, eff)
            )
        )
        return applyPayment(base, projectId, PaymentPurpose.UPFRONT, 0, eff) ?: ledger
    }

    fun declineOffer(ledger: Ledger, offerId: String, now: Long): Ledger {
        val sw = ledger.software
        val eff = effectiveNow(ledger, now)
        val offer = sw.offers.firstOrNull { it.id == offerId } ?: return ledger
        if (offer.status != OfferStatus.PENDING) return ledger
        if (eff >= offer.expiresMillis) return ledger
        return ledger.copy(
            software = sw.copy(
                offers = sw.offers.map {
                    if (it.id == offerId) it.copy(status = OfferStatus.DECLINED, decidedMillis = eff) else it
                },
                clockMillis = maxOf(sw.clockMillis, eff)
            )
        )
    }

    // ---- Payments ----

    fun paymentId(projectId: String, purpose: PaymentPurpose, periodIndex: Int): String =
        when (purpose) {
            PaymentPurpose.UPFRONT -> "pay-$projectId-up"
            PaymentPurpose.FINAL -> "pay-$projectId-fin"
            PaymentPurpose.MAINTENANCE -> "pay-$projectId-m$periodIndex"
        }

    fun purposeLabel(purpose: PaymentPurpose, periodIndex: Int): String =
        when (purpose) {
            PaymentPurpose.UPFRONT -> "Upfront 50%"
            PaymentPurpose.FINAL -> "Final 50%"
            PaymentPurpose.MAINTENANCE -> "Maintenance month $periodIndex"
        }

    /**
     * Records one payment of a project, exactly once. A naira payment becomes income of the software
     * app straight away. A foreign payment goes into the $ account and is NOT company income.
     * Returns null (nothing recorded) when the payment already exists or cannot be recorded.
     */
    private fun applyPayment(
        ledger: Ledger,
        projectId: String,
        purpose: PaymentPurpose,
        periodIndex: Int,
        effectiveNow: Long
    ): Ledger? {
        val sw = ledger.software
        val project = sw.projects.firstOrNull { it.id == projectId } ?: return null
        val app = ledger.apps.firstOrNull { it.id == SOFTWARE_APP_ID } ?: return null
        val id = paymentId(projectId, purpose, periodIndex)
        if (project.payments.any { it.id == id }) return null
        if (app.entries.any { it.id == "sw-$id" }) return null

        val units = when (purpose) {
            PaymentPurpose.UPFRONT -> project.feeUnits / 2
            PaymentPurpose.FINAL -> project.feeUnits - project.feeUnits / 2
            PaymentPurpose.MAINTENANCE -> project.maintenanceUnits
        }
        if (units <= 0L) return null

        val currency = project.currency
        val label = purposeLabel(purpose, periodIndex) + " — " + project.clientName +
            " (" + project.ref + ")"

        var newApps = ledger.apps
        var usdBalance = sw.usdBalanceCents
        var usdTx = sw.usdTx
        val payment: ProjectPayment
        if (currency == "NGN") {
            payment = ProjectPayment(
                id = id,
                purpose = purpose,
                periodIndex = periodIndex,
                dateMillis = effectiveNow,
                currency = "NGN",
                units = units,
                ratePerUsd = perUsd("NGN", sw.rates),
                usdCents = 0L,
                ngnAtReceipt = units
            )
            val entry = Entry(
                id = "sw-$id",
                title = label,
                amount = units,
                isIncome = true,
                dateMillis = effectiveNow,
                auto = true
            )
            newApps = newApps.map {
                if (it.id == SOFTWARE_APP_ID) it.copy(entries = it.entries + entry) else it
            }
        } else {
            val rate = perUsd(currency, sw.rates)
            val cents = usdCentsFor(units, currency, sw.rates)
            payment = ProjectPayment(
                id = id,
                purpose = purpose,
                periodIndex = periodIndex,
                dateMillis = effectiveNow,
                currency = currency,
                units = units,
                ratePerUsd = rate,
                usdCents = cents,
                ngnAtReceipt = ngnEstimateFor(units, currency, sw.rates)
            )
            usdBalance += cents
            val tx = UsdTx(
                id = UUID.randomUUID().toString(),
                kind = UsdTxKind.PAYMENT_IN,
                dateMillis = effectiveNow,
                usdCents = cents,
                label = label,
                currency = currency,
                units = units,
                ratePerUsd = rate,
                ngnAmount = 0L,
                ngnPerUsd = 0.0
            )
            usdTx = (listOf(tx) + usdTx).take(KEEP_USD_TX)
        }

        val updatedProject = project.copy(payments = project.payments + payment)
        return ledger.copy(
            apps = newApps,
            software = sw.copy(
                projects = sw.projects.map { if (it.id == projectId) updatedProject else it },
                usdBalanceCents = usdBalance,
                usdTx = usdTx,
                clockMillis = maxOf(sw.clockMillis, effectiveNow)
            )
        )
    }

    // ---- Stages and delivery ----

    fun setStage(ledger: Ledger, projectId: String, stage: ProjectStage, now: Long): Ledger {
        val sw = ledger.software
        val project = sw.projects.firstOrNull { it.id == projectId } ?: return ledger
        if (project.stage == ProjectStage.DELIVERED) return ledger
        val eff = effectiveNow(ledger, now)

        if (stage == ProjectStage.DELIVERED) {
            val isCode = project.kind == OfferKind.CODE_PURCHASE
            val delivered = project.copy(
                stage = ProjectStage.DELIVERED,
                deliveredMillis = eff,
                maintenanceActive = !isCode,
                maintenanceUnits = if (isCode) 0L else project.feeUnits / 10
            )
            val base = ledger.copy(
                software = sw.copy(
                    projects = sw.projects.map { if (it.id == projectId) delivered else it },
                    clockMillis = maxOf(sw.clockMillis, eff)
                )
            )
            return applyPayment(base, projectId, PaymentPurpose.FINAL, 0, eff) ?: ledger
        }

        if (project.stage == stage) return ledger
        return ledger.copy(
            software = sw.copy(
                projects = sw.projects.map { if (it.id == projectId) it.copy(stage = stage) else it },
                clockMillis = maxOf(sw.clockMillis, eff)
            )
        )
    }

    // ---- Maintenance ----

    /** Records the next unpaid month. One call is one month. */
    fun recordMaintenance(ledger: Ledger, projectId: String, now: Long): Ledger {
        val sw = ledger.software
        val project = sw.projects.firstOrNull { it.id == projectId } ?: return ledger
        val eff = effectiveNow(ledger, now)
        if (!project.maintenanceActive) return ledger
        if (maintenanceOwed(project, eff) <= 0) return ledger
        val period = maintenancePaidCount(project) + 1
        return applyPayment(
            ledger.copy(software = sw.copy(clockMillis = maxOf(sw.clockMillis, eff))),
            projectId,
            PaymentPurpose.MAINTENANCE,
            period,
            eff
        ) ?: ledger
    }

    fun stopMaintenance(ledger: Ledger, projectId: String, now: Long): Ledger {
        val sw = ledger.software
        val project = sw.projects.firstOrNull { it.id == projectId } ?: return ledger
        if (!project.maintenanceActive) return ledger
        val eff = effectiveNow(ledger, now)
        return ledger.copy(
            software = sw.copy(
                projects = sw.projects.map {
                    if (it.id == projectId) {
                        it.copy(maintenanceActive = false, maintenanceStoppedMillis = eff)
                    } else {
                        it
                    }
                },
                clockMillis = maxOf(sw.clockMillis, eff)
            )
        )
    }

    // ---- Project expenses (they are company expenses, counted once) ----

    fun addProjectExpense(
        ledger: Ledger,
        projectId: String,
        title: String,
        amount: Long,
        now: Long
    ): Ledger {
        val sw = ledger.software
        val project = sw.projects.firstOrNull { it.id == projectId } ?: return ledger
        val clean = title.trim()
        if (clean.isEmpty() || amount <= 0L) return ledger
        val eff = effectiveNow(ledger, now)
        val entry = Entry(
            title = clean + " — " + project.ref,
            amount = amount,
            isIncome = false,
            dateMillis = eff,
            auto = false,
            appId = SOFTWARE_APP_ID,
            projectId = projectId
        )
        return ledger.copy(
            company = ledger.company.copy(expenses = ledger.company.expenses + entry),
            software = sw.copy(clockMillis = maxOf(sw.clockMillis, eff))
        )
    }

    // ---- The $ account ----

    /**
     * Turns dollars into naira income. This is the only way $ becomes company income.
     * [requestId] is created once when the dialog opens, so a repeated tap cannot convert twice.
     */
    fun convertUsd(
        ledger: Ledger,
        usdCents: Long,
        ngnPerUsd: Double,
        requestId: String,
        now: Long
    ): Ledger {
        val sw = ledger.software
        if (requestId.isBlank()) return ledger
        if (usdCents < 1L || usdCents > sw.usdBalanceCents) return ledger
        if (ngnPerUsd.isNaN() || ngnPerUsd < NGN_RATE_MIN || ngnPerUsd > NGN_RATE_MAX) return ledger
        val ngn = Math.round(usdCents / 100.0 * ngnPerUsd)
        if (ngn < 1L) return ledger
        if (sw.usdTx.any { it.id == requestId }) return ledger
        val app = ledger.apps.firstOrNull { it.id == SOFTWARE_APP_ID } ?: return ledger
        val entryId = "sw-conv-$requestId"
        if (app.entries.any { it.id == entryId }) return ledger

        val eff = effectiveNow(ledger, now)
        val entry = Entry(
            id = entryId,
            title = "Converted " + formatUsd(usdCents) + " to naira at ₦" + formatRate(ngnPerUsd) + " per $1",
            amount = ngn,
            isIncome = true,
            dateMillis = eff,
            auto = true
        )
        val tx = UsdTx(
            id = requestId,
            kind = UsdTxKind.CONVERSION_OUT,
            dateMillis = eff,
            usdCents = usdCents,
            label = "Converted to naira",
            currency = "USD",
            units = usdCents / 100L,
            ratePerUsd = 1.0,
            ngnAmount = ngn,
            ngnPerUsd = ngnPerUsd
        )
        return ledger.copy(
            apps = ledger.apps.map {
                if (it.id == SOFTWARE_APP_ID) it.copy(entries = it.entries + entry) else it
            },
            software = sw.copy(
                usdBalanceCents = sw.usdBalanceCents - usdCents,
                usdTx = (listOf(tx) + sw.usdTx).take(KEEP_USD_TX),
                clockMillis = maxOf(sw.clockMillis, eff)
            )
        )
    }

    fun usdReceivedCents(sw: SoftwareData): Long =
        sw.usdTx.filter { it.kind == UsdTxKind.PAYMENT_IN }.sumOf { it.usdCents }

    fun usdConvertedCents(sw: SoftwareData): Long =
        sw.usdTx.filter { it.kind == UsdTxKind.CONVERSION_OUT }.sumOf { it.usdCents }

    // ---- Rates ----

    /**
     * Applies a live refresh. A rate the owner typed (MANUAL) is never overwritten.
     * Returns the same ledger when [live] has nothing usable.
     */
    fun applyLiveRates(ledger: Ledger, live: Map<String, Double>, now: Long): Ledger {
        val sw = ledger.software
        val updated = LinkedHashMap(completeRates(sw.rates))
        var changed = false
        for (code in SOFTWARE_CURRENCIES) {
            if (code == "USD") continue
            val value = live[code] ?: continue
            if (!isValidRate(code, value)) continue
            if (updated[code]?.source == RateSource.MANUAL) continue
            updated[code] = RateInfo(value, now, RateSource.LIVE)
            changed = true
        }
        if (!changed) return ledger
        return ledger.copy(software = sw.copy(rates = updated, ratesFetchedMillis = now))
    }

    /** The owner typed a rate. NGN must be 300 to 10 000, others above 0. USD cannot change. */
    fun saveManualRate(ledger: Ledger, code: String, value: Double, now: Long): Ledger {
        if (!isValidRate(code, value)) return ledger
        val sw = ledger.software
        val updated = LinkedHashMap(completeRates(sw.rates))
        updated[code] = RateInfo(value, now, RateSource.MANUAL)
        return ledger.copy(software = sw.copy(rates = updated))
    }

    /** Puts one currency back to its default rate. A live refresh normally follows straight away. */
    fun resetRateToDefault(ledger: Ledger, code: String): Ledger {
        if (code == "USD" || code !in SOFTWARE_CURRENCIES) return ledger
        val sw = ledger.software
        val updated = LinkedHashMap(completeRates(sw.rates))
        updated[code] = DEFAULT_RATES.getValue(code)
        return ledger.copy(software = sw.copy(rates = updated))
    }
}
