package com.westly.neribogroup.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID

/** The id and name of the built-in "Client Hosting" app. */
const val HOSTING_APP_ID = "client-hosting"
const val HOSTING_APP_NAME = "Client Hosting"

/** True for the one built-in Client Hosting app. It has its own screen and can never be deleted. */
val AppData.isHosting: Boolean get() = id == HOSTING_APP_ID

/**
 * Makes sure the Client Hosting app exists. It is added at the END of the apps list when missing.
 * When more than one app has its id, only the first is kept. Returns the same ledger when nothing changes.
 */
fun Ledger.withHostingApp(): Ledger {
    val matches = apps.count { it.id == HOSTING_APP_ID }
    if (matches == 0) {
        return copy(apps = apps + AppData(id = HOSTING_APP_ID, name = HOSTING_APP_NAME))
    }
    if (matches > 1) {
        var seen = false
        val cleaned = apps.filter { app ->
            if (app.id != HOSTING_APP_ID) {
                true
            } else if (!seen) {
                seen = true
                true
            } else {
                false
            }
        }
        return copy(apps = cleaned)
    }
    return this
}

/** The two currencies a hosting customer can pay in. */
val HOSTING_CURRENCIES: List<String> = listOf("NGN", "USD")

/** One renewal that was paid by a customer. */
data class HostingPayment(
    val id: String,
    val dateMillis: Long,
    /** "NGN" or "USD". */
    val currency: String,
    /** The price in whole units of [currency]. */
    val units: Long,
    /** Naira per 1 USD used for the conversion (1.0 for a naira payment). */
    val ratePerUsd: Double,
    /** What was added to the company's income, in whole naira. */
    val ngnCredited: Long
)

/**
 * One customer of Client Hosting. The renewal works exactly like a line of the Renewals page: it lasts
 * [durationDays], it warns [reminderDays] before it runs out, and Renew starts the next period.
 */
data class HostingCustomer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val contactName: String = "",
    val email: String = "",
    val phone: String = "",
    /** What was built or what is hosted for this customer. */
    val notes: String = "",
    /** The storage given to this customer, in MB. */
    val storageMb: Long,
    /** "NGN" or "USD". */
    val currency: String,
    /** The price of one renewal, in whole units of [currency]. */
    val priceUnits: Long,
    val addedMillis: Long,
    val durationDays: Int = 30,
    val paidMillis: Long,
    val expiryMillis: Long,
    val reminderDays: List<Int> = listOf(7, 2),
    /** Newest first. The last 60 are kept. */
    val payments: List<HostingPayment> = emptyList(),
    /** How many renewals were ever paid (the list above keeps only the newest). */
    val paidCount: Int = 0
) {
    /** Whole calendar days from today to the renewal day. Negative means overdue. */
    fun daysLeft(now: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(expiryMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, end)
    }

    /** 0 = fine, 1 = inside a reminder stage, 2 = inside the last stage or overdue. Same rule as Renewals. */
    fun alertLevel(now: Long): Int {
        val left = daysLeft(now)
        if (left < 0L) return 2
        val stages = reminderDays.sorted()
        if (stages.isEmpty()) return if (left == 0L) 2 else 0
        if (left <= stages.first()) return 2
        if (left <= stages.last()) return 1
        return 0
    }

    /** Pays for another period. If it has not run out yet, the new period starts after the current one. */
    fun renewedAt(now: Long): HostingCustomer {
        val base = maxOf(now, expiryMillis)
        return copy(paidMillis = now, expiryMillis = base + durationDays * DAY_MILLIS)
    }
}

data class HostingData(
    val customers: List<HostingCustomer> = emptyList(),
    /** The highest "now" ever seen by the Client Hosting screen. Protects against a clock set back. */
    val clockMillis: Long = 0L
)

// ---------------------------------------------------------------------------
// Engine
// ---------------------------------------------------------------------------

object HostingEngine {

    const val KEEP_PAYMENTS = 60
    const val MAX_CUSTOMERS = 500

    /** Naira per 1 USD from the software rates (always inside the allowed range). */
    fun ngnPerUsd(ledger: Ledger): Double {
        val rate = ledger.software.rates["NGN"]?.perUsd ?: DEFAULT_RATES["NGN"]?.perUsd ?: 1500.0
        return rate.coerceIn(NGN_RATE_MIN, NGN_RATE_MAX)
    }

    /** The naira worth of [units] of [currency] at [ngnPerUsd]. */
    fun ngnFor(units: Long, currency: String, ngnPerUsd: Double): Long =
        if (currency == "USD") Math.round(units * ngnPerUsd) else units

    /** What all customers pay in one period, in naira at today's rate. */
    fun monthlyNgn(ledger: Ledger): Long {
        val rate = ngnPerUsd(ledger)
        return ledger.hosting.customers.sumOf { ngnFor(it.priceUnits, it.currency, rate) }
    }

    fun storageGivenMb(ledger: Ledger): Long = ledger.hosting.customers.sumOf { it.storageMb }

    /** Customers that are inside a reminder stage or overdue, soonest first. */
    fun needingAttention(ledger: Ledger, now: Long): List<HostingCustomer> =
        ledger.hosting.customers.filter { it.alertLevel(now) > 0 }.sortedBy { it.daysLeft(now) }

    fun overdueCount(ledger: Ledger, now: Long): Int =
        ledger.hosting.customers.count { it.daysLeft(now) < 0L }

    /** The customer's renewal as it is shown on the card. */
    fun statusText(left: Long): String = when {
        left < 0L -> "Overdue by " + (-left) + (if (-left == 1L) " day" else " days")
        left == 0L -> "Renews today"
        left == 1L -> "1 day left"
        else -> "$left days left"
    }

    private fun effectiveNow(ledger: Ledger, now: Long): Long = maxOf(now, ledger.hosting.clockMillis)

    /** Remembers the highest time seen. */
    fun withClock(ledger: Ledger, now: Long): Ledger =
        if (now <= ledger.hosting.clockMillis) ledger
        else ledger.copy(hosting = ledger.hosting.copy(clockMillis = now))

    /**
     * Adds a customer. When [paidNow] is true the first period is already paid, so it is recorded like a Renew
     * (and added to the company income) straight away.
     */
    fun addCustomer(ledger: Ledger, customer: HostingCustomer, paidNow: Boolean, now: Long): Ledger {
        if (ledger.hosting.customers.size >= MAX_CUSTOMERS) return ledger
        if (ledger.hosting.customers.any { it.id == customer.id }) return ledger
        val eff = effectiveNow(ledger, now)
        val fresh = customer.copy(
            addedMillis = eff,
            paidMillis = eff,
            expiryMillis = eff + customer.durationDays * DAY_MILLIS,
            payments = emptyList(),
            paidCount = 0
        )
        val base = ledger.copy(
            hosting = ledger.hosting.copy(
                customers = ledger.hosting.customers + fresh,
                clockMillis = maxOf(ledger.hosting.clockMillis, eff)
            )
        )
        if (!paidNow) return base
        return recordPayment(base, fresh.id, eff, moveDate = false) ?: base
    }

    /** Changes the details of a customer. Dates, payments and history are never touched here. */
    fun updateCustomer(ledger: Ledger, edited: HostingCustomer): Ledger {
        val old = ledger.hosting.customers.firstOrNull { it.id == edited.id } ?: return ledger
        val merged = old.copy(
            name = edited.name,
            contactName = edited.contactName,
            email = edited.email,
            phone = edited.phone,
            notes = edited.notes,
            storageMb = edited.storageMb,
            currency = edited.currency,
            priceUnits = edited.priceUnits,
            durationDays = edited.durationDays,
            reminderDays = edited.reminderDays
        )
        return ledger.copy(
            hosting = ledger.hosting.copy(
                customers = ledger.hosting.customers.map { if (it.id == edited.id) merged else it }
            )
        )
    }

    fun deleteCustomer(ledger: Ledger, customerId: String): Ledger {
        if (ledger.hosting.customers.none { it.id == customerId }) return ledger
        return ledger.copy(
            hosting = ledger.hosting.copy(
                customers = ledger.hosting.customers.filter { it.id != customerId }
            )
        )
    }

    /**
     * Renew: starts the next period and adds the price to the company's income. A $ price is changed to naira at
     * the software rate of this moment and the rate is written in the income line.
     */
    fun renew(ledger: Ledger, customerId: String, now: Long): Ledger {
        val eff = effectiveNow(ledger, now)
        return recordPayment(ledger, customerId, eff, moveDate = true) ?: ledger
    }

    private fun recordPayment(ledger: Ledger, customerId: String, eff: Long, moveDate: Boolean): Ledger? {
        val customer = ledger.hosting.customers.firstOrNull { it.id == customerId } ?: return null
        val app = ledger.apps.firstOrNull { it.id == HOSTING_APP_ID } ?: return null
        if (customer.priceUnits < 0L) return null

        val rate = if (customer.currency == "USD") ngnPerUsd(ledger) else 1.0
        val ngn = ngnFor(customer.priceUnits, customer.currency, rate)
        val number = customer.paidCount + 1
        val paymentId = "host-" + customer.id + "-" + number
        if (app.entries.any { it.id == paymentId }) return null

        val priceText = if (customer.currency == "USD") {
            "$" + customer.priceUnits + " at ₦" + Math.round(rate) + "/$"
        } else {
            "₦" + customer.priceUnits
        }
        val entry = Entry(
            id = paymentId,
            title = "Hosting renewal: " + customer.name + " (" + priceText + ")",
            amount = ngn,
            isIncome = true,
            dateMillis = eff,
            auto = true
        )
        val payment = HostingPayment(
            id = paymentId,
            dateMillis = eff,
            currency = customer.currency,
            units = customer.priceUnits,
            ratePerUsd = rate,
            ngnCredited = ngn
        )
        val moved = if (moveDate) customer.renewedAt(eff) else customer
        val updated = moved.copy(
            payments = (listOf(payment) + customer.payments).take(KEEP_PAYMENTS),
            paidCount = number
        )
        val newApps = ledger.apps.map {
            if (it.id == HOSTING_APP_ID) {
                if (ngn > 0L) it.copy(entries = it.entries + entry) else it
            } else {
                it
            }
        }
        return ledger.copy(
            apps = newApps,
            hosting = ledger.hosting.copy(
                customers = ledger.hosting.customers.map { if (it.id == customerId) updated else it },
                clockMillis = maxOf(ledger.hosting.clockMillis, eff)
            )
        )
    }

    /**
     * Reads pasted customer details. The first line is the name; an e-mail address and a phone number are
     * found anywhere in the text; the rest of the lines become the notes.
     */
    fun parsePasted(text: String): PastedCustomer {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val emailRegex = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
        val phoneRegex = Regex("\\+?\\d[\\d ()-]{6,}\\d")
        val email = emailRegex.find(text)?.value ?: ""
        val phone = phoneRegex.find(text)?.value?.trim() ?: ""
        val rest = lines.drop(1).filter { line ->
            (email.isEmpty() || !line.contains(email)) && (phone.isEmpty() || !line.contains(phone))
        }
        val name = lines.firstOrNull()
            ?.replace(emailRegex, "")
            ?.replace(phoneRegex, "")
            ?.trim(' ', ',', '-', ':')
            ?: ""
        return PastedCustomer(name = name.take(60), email = email.take(80), phone = phone.take(25), notes = rest.joinToString("\n").take(300))
    }
}

data class PastedCustomer(val name: String, val email: String, val phone: String, val notes: String)

// ---------------------------------------------------------------------------
// JSON
// ---------------------------------------------------------------------------

object HostingJson {

    fun toJson(data: HostingData): JSONObject {
        val root = JSONObject()
        val array = JSONArray()
        for (c in data.customers) array.put(customerToJson(c))
        root.put("customers", array)
        root.put("clockMillis", data.clockMillis)
        return root
    }

    fun fromJson(root: JSONObject?): HostingData {
        if (root == null) return HostingData()
        val list = ArrayList<HostingCustomer>()
        val seen = HashSet<String>()
        val array = root.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val c = customerFromJson(o) ?: continue
            if (seen.add(c.id)) list.add(c)
        }
        return HostingData(customers = list, clockMillis = maxOf(0L, root.optLong("clockMillis")))
    }

    private fun customerToJson(c: HostingCustomer): JSONObject {
        val o = JSONObject()
        o.put("id", c.id)
        o.put("name", c.name)
        o.put("contact", c.contactName)
        o.put("email", c.email)
        o.put("phone", c.phone)
        o.put("notes", c.notes)
        o.put("storageMb", c.storageMb)
        o.put("currency", c.currency)
        o.put("price", c.priceUnits)
        o.put("added", c.addedMillis)
        o.put("days", c.durationDays)
        o.put("paid", c.paidMillis)
        o.put("expiry", c.expiryMillis)
        val reminders = JSONArray()
        for (d in c.reminderDays) reminders.put(d)
        o.put("reminders", reminders)
        o.put("paidCount", c.paidCount)
        val payments = JSONArray()
        for (p in c.payments) {
            val j = JSONObject()
            j.put("id", p.id)
            j.put("date", p.dateMillis)
            j.put("currency", p.currency)
            j.put("units", p.units)
            j.put("rate", p.ratePerUsd)
            j.put("ngn", p.ngnCredited)
            payments.put(j)
        }
        o.put("payments", payments)
        return o
    }

    private fun customerFromJson(o: JSONObject): HostingCustomer? {
        val id = o.optString("id", "")
        val name = o.optString("name", "")
        if (id.isEmpty() || name.isEmpty()) return null
        val reminders = ArrayList<Int>()
        val r = o.optJSONArray("reminders")
        if (r != null) for (i in 0 until r.length()) reminders.add(r.optInt(i))
        val payments = ArrayList<HostingPayment>()
        val p = o.optJSONArray("payments")
        if (p != null) {
            for (i in 0 until p.length()) {
                val j = p.optJSONObject(i) ?: continue
                payments.add(
                    HostingPayment(
                        id = j.optString("id", ""),
                        dateMillis = j.optLong("date"),
                        currency = if (j.optString("currency") == "USD") "USD" else "NGN",
                        units = maxOf(0L, j.optLong("units")),
                        ratePerUsd = j.optDouble("rate", 1.0),
                        ngnCredited = maxOf(0L, j.optLong("ngn"))
                    )
                )
            }
        }
        val days = o.optInt("days", 30)
        return HostingCustomer(
            id = id,
            name = name,
            contactName = o.optString("contact", ""),
            email = o.optString("email", ""),
            phone = o.optString("phone", ""),
            notes = o.optString("notes", ""),
            storageMb = maxOf(0L, o.optLong("storageMb")),
            currency = if (o.optString("currency") == "USD") "USD" else "NGN",
            priceUnits = maxOf(0L, o.optLong("price")),
            addedMillis = o.optLong("added"),
            durationDays = if (days in 1..3650) days else 30,
            paidMillis = o.optLong("paid"),
            expiryMillis = o.optLong("expiry"),
            reminderDays = reminders,
            payments = payments,
            paidCount = maxOf(0, o.optInt("paidCount"))
        )
    }
}
