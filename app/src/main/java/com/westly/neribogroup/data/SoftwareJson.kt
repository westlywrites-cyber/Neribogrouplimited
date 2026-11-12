package com.westly.neribogroup.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Reads and writes the root "software" object of ledger.json.
 * Every field is read with a default, so older files and older backups load without error.
 */
object SoftwareJson {

    fun toJson(data: SoftwareData): JSONObject {
        val root = JSONObject()
        root.put("usdBalanceCents", data.usdBalanceCents)
        root.put("ratesFetchedMillis", data.ratesFetchedMillis)
        root.put("lastRollDayKey", data.lastRollDayKey)
        root.put("nextOfferNumber", data.nextOfferNumber)
        root.put("nextProjectNumber", data.nextProjectNumber)
        root.put("clockMillis", data.clockMillis)

        val offers = JSONArray()
        for (o in data.offers) offers.put(offerToJson(o))
        root.put("offers", offers)

        val projects = JSONArray()
        for (p in data.projects) projects.put(projectToJson(p))
        root.put("projects", projects)

        val clients = JSONArray()
        for (c in data.clients) clients.put(clientToJson(c))
        root.put("clients", clients)

        val usdTx = JSONArray()
        for (t in data.usdTx) usdTx.put(usdTxToJson(t))
        root.put("usdTx", usdTx)

        val rates = JSONObject()
        for ((code, info) in data.rates) {
            val ro = JSONObject()
            ro.put("perUsd", info.perUsd)
            ro.put("updatedMillis", info.updatedMillis)
            ro.put("source", info.source.name)
            rates.put(code, ro)
        }
        root.put("rates", rates)

        val rolls = JSONArray()
        for (r in data.rolls) {
            val ro = JSONObject()
            ro.put("dayKey", r.dayKey)
            ro.put("at", r.atMillis)
            ro.put("contactFace", r.contactFace)
            ro.put("offerFace", r.offerFace)
            ro.put("contacts", r.contacts)
            ro.put("offers", r.offers)
            ro.put("note", r.note)
            ro.put("byClaude", r.byClaude)
            ro.put("reachBonus", r.reachBonus)
            rolls.put(ro)
        }
        root.put("rolls", rolls)
        return root
    }

    /** A missing or unreadable object gives empty software data with the default rates. */
    fun fromJson(root: JSONObject?): SoftwareData {
        if (root == null) return SoftwareData()

        val offers = ArrayList<SoftwareOffer>()
        val offersArray = root.optJSONArray("offers")
        if (offersArray != null) {
            for (i in 0 until offersArray.length()) {
                val o = offersArray.optJSONObject(i) ?: continue
                val parsed = runCatching { jsonToOffer(o) }.getOrNull() ?: continue
                offers.add(parsed)
            }
        }

        val projects = ArrayList<SoftwareProject>()
        val projectsArray = root.optJSONArray("projects")
        if (projectsArray != null) {
            for (i in 0 until projectsArray.length()) {
                val o = projectsArray.optJSONObject(i) ?: continue
                val parsed = runCatching { jsonToProject(o) }.getOrNull() ?: continue
                projects.add(parsed)
            }
        }

        val clients = ArrayList<SoftwareClient>()
        val clientsArray = root.optJSONArray("clients")
        if (clientsArray != null) {
            for (i in 0 until clientsArray.length()) {
                val o = clientsArray.optJSONObject(i) ?: continue
                val parsed = runCatching { jsonToClient(o) }.getOrNull() ?: continue
                clients.add(parsed)
            }
        }

        val usdTx = ArrayList<UsdTx>()
        val usdArray = root.optJSONArray("usdTx")
        if (usdArray != null) {
            for (i in 0 until usdArray.length()) {
                val o = usdArray.optJSONObject(i) ?: continue
                val parsed = runCatching { jsonToUsdTx(o) }.getOrNull() ?: continue
                usdTx.add(parsed)
            }
        }

        val rolls = ArrayList<SoftwareRoll>()
        val rollsArray = root.optJSONArray("rolls")
        if (rollsArray != null) {
            for (i in 0 until rollsArray.length()) {
                val o = rollsArray.optJSONObject(i) ?: continue
                val day = o.optString("dayKey")
                if (day.isBlank()) continue
                rolls.add(
                    SoftwareRoll(
                        dayKey = day,
                        atMillis = o.optLong("at"),
                        contactFace = o.optInt("contactFace").coerceIn(0, 5),
                        offerFace = o.optInt("offerFace").coerceIn(0, 5),
                        contacts = o.optInt("contacts"),
                        offers = o.optInt("offers"),
                        note = o.optString("note"),
                        byClaude = o.optBoolean("byClaude"),
                        reachBonus = maxOf(0, o.optInt("reachBonus"))
                    )
                )
            }
        }

        // Rates: start from the defaults, then take every valid saved rate.
        val savedRates = LinkedHashMap<String, RateInfo>()
        val ratesObject = root.optJSONObject("rates")
        if (ratesObject != null) {
            for (code in SOFTWARE_CURRENCIES) {
                val ro = ratesObject.optJSONObject(code) ?: continue
                val perUsd = ro.optDouble("perUsd", 0.0)
                val source = runCatching { RateSource.valueOf(ro.optString("source")) }
                    .getOrDefault(RateSource.DEFAULT)
                savedRates[code] = RateInfo(perUsd, ro.optLong("updatedMillis"), source)
            }
        }

        // Duplicate ids would break exactly-once rules, so the first of each id is kept.
        val uniqueOffers = offers.distinctBy { it.id }
        val uniqueProjects = projects.distinctBy { it.id }

        return SoftwareData(
            offers = uniqueOffers,
            projects = uniqueProjects,
            clients = clients.distinctBy { it.id },
            usdBalanceCents = maxOf(0L, root.optLong("usdBalanceCents")),
            usdTx = usdTx.take(SoftwareEngine.KEEP_USD_TX),
            rates = SoftwareEngine.completeRates(savedRates),
            ratesFetchedMillis = root.optLong("ratesFetchedMillis"),
            lastRollDayKey = root.optString("lastRollDayKey"),
            rolls = rolls.take(SoftwareEngine.KEEP_ROLLS),
            nextOfferNumber = maxOf(root.optInt("nextOfferNumber", 1), 1),
            nextProjectNumber = maxOf(root.optInt("nextProjectNumber", 1), 1),
            clockMillis = root.optLong("clockMillis")
        )
    }

    // ---- Offers ----

    private fun stringsToJson(list: List<String>): JSONArray {
        val array = JSONArray()
        for (s in list) array.put(s)
        return array
    }

    private fun jsonToStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val list = ArrayList<String>()
        for (i in 0 until array.length()) list.add(array.optString(i))
        return list
    }

    private fun offerToJson(o: SoftwareOffer): JSONObject {
        val j = JSONObject()
        j.put("id", o.id)
        j.put("ref", o.ref)
        j.put("created", o.createdMillis)
        j.put("expires", o.expiresMillis)
        // EXPIRED is derived, never stored.
        j.put("status", if (o.status == OfferStatus.EXPIRED) OfferStatus.PENDING.name else o.status.name)
        j.put("decided", o.decidedMillis)
        j.put("clientType", o.clientType.name)
        j.put("clientName", o.clientName)
        j.put("contactName", o.contactName)
        j.put("email", o.email)
        j.put("phone", o.phone)
        j.put("country", o.country)
        j.put("currency", o.currency)
        j.put("kind", o.kind.name)
        j.put("title", o.title)
        j.put("summary", o.summary)
        j.put("requirements", stringsToJson(o.requirements))
        j.put("deliveryWeeks", o.deliveryWeeks)
        j.put("fee", o.feeUnits)
        j.put("ngnEstimate", o.ngnEstimate)
        j.put("writer", o.writer)
        return j
    }

    private fun jsonToOffer(j: JSONObject): SoftwareOffer? {
        val kind = runCatching { OfferKind.valueOf(j.optString("kind")) }.getOrNull() ?: return null
        val id = j.optString("id")
        if (id.isBlank()) return null
        val status = when (j.optString("status")) {
            "ACCEPTED" -> OfferStatus.ACCEPTED
            "DECLINED" -> OfferStatus.DECLINED
            else -> OfferStatus.PENDING
        }
        val clientType = runCatching { ClientType.valueOf(j.optString("clientType")) }
            .getOrDefault(ClientType.COMPANY)
        return SoftwareOffer(
            id = id,
            ref = j.optString("ref"),
            createdMillis = j.optLong("created"),
            expiresMillis = j.optLong("expires"),
            status = status,
            decidedMillis = j.optLong("decided"),
            clientType = clientType,
            clientName = j.optString("clientName"),
            contactName = j.optString("contactName"),
            email = j.optString("email"),
            phone = j.optString("phone"),
            country = j.optString("country"),
            currency = j.optString("currency", "NGN").ifBlank { "NGN" },
            kind = kind,
            title = j.optString("title"),
            summary = j.optString("summary"),
            requirements = jsonToStrings(j.optJSONArray("requirements")),
            deliveryWeeks = j.optInt("deliveryWeeks"),
            feeUnits = j.optLong("fee"),
            ngnEstimate = j.optLong("ngnEstimate"),
            writer = j.optString("writer")
        )
    }

    // ---- Projects ----

    private fun paymentToJson(p: ProjectPayment): JSONObject {
        val j = JSONObject()
        j.put("id", p.id)
        j.put("purpose", p.purpose.name)
        j.put("period", p.periodIndex)
        j.put("date", p.dateMillis)
        j.put("currency", p.currency)
        j.put("units", p.units)
        j.put("ratePerUsd", p.ratePerUsd)
        j.put("usdCents", p.usdCents)
        j.put("ngnAtReceipt", p.ngnAtReceipt)
        return j
    }

    private fun jsonToPayment(j: JSONObject): ProjectPayment? {
        val purpose = runCatching { PaymentPurpose.valueOf(j.optString("purpose")) }.getOrNull() ?: return null
        val id = j.optString("id")
        if (id.isBlank()) return null
        return ProjectPayment(
            id = id,
            purpose = purpose,
            periodIndex = j.optInt("period"),
            dateMillis = j.optLong("date"),
            currency = j.optString("currency", "NGN").ifBlank { "NGN" },
            units = j.optLong("units"),
            ratePerUsd = j.optDouble("ratePerUsd", 1.0),
            usdCents = j.optLong("usdCents"),
            ngnAtReceipt = j.optLong("ngnAtReceipt")
        )
    }

    private fun projectToJson(p: SoftwareProject): JSONObject {
        val j = JSONObject()
        j.put("id", p.id)
        j.put("offerId", p.offerId)
        j.put("clientId", p.clientId)
        j.put("ref", p.ref)
        j.put("clientName", p.clientName)
        j.put("title", p.title)
        j.put("summary", p.summary)
        j.put("requirements", stringsToJson(p.requirements))
        j.put("kind", p.kind.name)
        j.put("currency", p.currency)
        j.put("fee", p.feeUnits)
        j.put("stage", p.stage.name)
        j.put("created", p.createdMillis)
        j.put("delivered", p.deliveredMillis)
        val payments = JSONArray()
        for (pay in p.payments) payments.put(paymentToJson(pay))
        j.put("payments", payments)
        j.put("maintenanceActive", p.maintenanceActive)
        j.put("maintenanceUnits", p.maintenanceUnits)
        j.put("maintenanceStopped", p.maintenanceStoppedMillis)
        return j
    }

    private fun jsonToProject(j: JSONObject): SoftwareProject? {
        val kind = runCatching { OfferKind.valueOf(j.optString("kind")) }.getOrNull() ?: return null
        val id = j.optString("id")
        if (id.isBlank()) return null
        val stage = runCatching { ProjectStage.valueOf(j.optString("stage")) }
            .getOrDefault(ProjectStage.ACCEPTED)

        val payments = ArrayList<ProjectPayment>()
        val paymentsArray = j.optJSONArray("payments")
        if (paymentsArray != null) {
            for (i in 0 until paymentsArray.length()) {
                val po = paymentsArray.optJSONObject(i) ?: continue
                val parsed = runCatching { jsonToPayment(po) }.getOrNull() ?: continue
                payments.add(parsed)
            }
        }
        return SoftwareProject(
            id = id,
            offerId = j.optString("offerId"),
            clientId = j.optString("clientId"),
            ref = j.optString("ref"),
            clientName = j.optString("clientName"),
            title = j.optString("title"),
            summary = j.optString("summary"),
            requirements = jsonToStrings(j.optJSONArray("requirements")),
            kind = kind,
            currency = j.optString("currency", "NGN").ifBlank { "NGN" },
            feeUnits = j.optLong("fee"),
            stage = stage,
            createdMillis = j.optLong("created"),
            deliveredMillis = j.optLong("delivered"),
            payments = payments.distinctBy { it.id },
            maintenanceActive = j.optBoolean("maintenanceActive"),
            maintenanceUnits = j.optLong("maintenanceUnits"),
            maintenanceStoppedMillis = j.optLong("maintenanceStopped")
        )
    }

    // ---- Clients and the $ account ----

    private fun clientToJson(c: SoftwareClient): JSONObject {
        val j = JSONObject()
        j.put("id", c.id)
        j.put("type", c.type.name)
        j.put("name", c.name)
        j.put("contactName", c.contactName)
        j.put("email", c.email)
        j.put("phone", c.phone)
        j.put("country", c.country)
        j.put("currency", c.currency)
        j.put("since", c.sinceMillis)
        return j
    }

    private fun jsonToClient(j: JSONObject): SoftwareClient? {
        val id = j.optString("id")
        if (id.isBlank()) return null
        val type = runCatching { ClientType.valueOf(j.optString("type")) }.getOrDefault(ClientType.COMPANY)
        return SoftwareClient(
            id = id,
            type = type,
            name = j.optString("name"),
            contactName = j.optString("contactName"),
            email = j.optString("email"),
            phone = j.optString("phone"),
            country = j.optString("country"),
            currency = j.optString("currency", "NGN").ifBlank { "NGN" },
            sinceMillis = j.optLong("since")
        )
    }

    private fun usdTxToJson(t: UsdTx): JSONObject {
        val j = JSONObject()
        j.put("id", t.id)
        j.put("kind", t.kind.name)
        j.put("date", t.dateMillis)
        j.put("usdCents", t.usdCents)
        j.put("label", t.label)
        j.put("currency", t.currency)
        j.put("units", t.units)
        j.put("ratePerUsd", t.ratePerUsd)
        j.put("ngnAmount", t.ngnAmount)
        j.put("ngnPerUsd", t.ngnPerUsd)
        return j
    }

    private fun jsonToUsdTx(j: JSONObject): UsdTx? {
        val kind = runCatching { UsdTxKind.valueOf(j.optString("kind")) }.getOrNull() ?: return null
        return UsdTx(
            id = j.optString("id").ifBlank { UUID.randomUUID().toString() },
            kind = kind,
            dateMillis = j.optLong("date"),
            usdCents = j.optLong("usdCents"),
            label = j.optString("label"),
            currency = j.optString("currency", "USD").ifBlank { "USD" },
            units = j.optLong("units"),
            ratePerUsd = j.optDouble("ratePerUsd", 1.0),
            ngnAmount = j.optLong("ngnAmount"),
            ngnPerUsd = j.optDouble("ngnPerUsd", 0.0)
        )
    }
}
