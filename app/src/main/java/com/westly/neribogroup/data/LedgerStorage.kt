package com.westly.neribogroup.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Saves and loads everything as one JSON file inside the app's private storage. */
object LedgerStorage {

    private const val FILE_NAME = "ledger.json"

    fun load(context: Context): Ledger {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return Ledger()
        return try {
            fromJson(file.readText())
        } catch (e: Exception) {
            // Keep the unreadable file instead of overwriting it.
            runCatching { file.renameTo(File(context.filesDir, "ledger.corrupt.json")) }
            Ledger()
        }
    }

    fun save(context: Context, ledger: Ledger) {
        runCatching {
            val file = File(context.filesDir, FILE_NAME)
            val temp = File(context.filesDir, "$FILE_NAME.tmp")
            temp.writeText(toJson(ledger))
            if (!temp.renameTo(file)) {
                file.writeText(temp.readText())
                temp.delete()
            }
        }
    }

    fun toJson(ledger: Ledger): String {
        val root = JSONObject()
        root.put("version", 4)
        root.put("companyName", ledger.company.name)
        root.put("companyExpenses", entriesToJson(ledger.company.expenses))

        val apps = JSONArray()
        for (app in ledger.apps) {
            val o = JSONObject()
            o.put("id", app.id)
            o.put("name", app.name)
            o.put("premiumPrice", app.premiumPrice)
            o.put("proPrice", app.proPrice)
            o.put("entries", entriesToJson(app.entries))
            o.put("users", usersToJson(app.users))
            o.put("premiumBenefits", app.premiumBenefits)
            o.put("proBenefits", app.proBenefits)
            o.put("freeBenefits", app.freeBenefits)
            o.put("activeUsers", app.activeUsers)
            o.put("activeUsersDayKey", app.activeUsersDayKey)
            o.put("activeHistory", historyToJson(app.activeHistory))
            o.put("adsEnabled", app.adsEnabled)
            o.put("lastRollDayKey", app.lastRollDayKey)
            o.put("playSubsBalance", app.playSubsBalance)
            o.put("playAdsBalance", app.playAdsBalance)
            o.put("rolls", rollsToJson(app.rolls))
            o.put("playTx", playTxToJson(app.playTx))
            val campaign = app.campaign
            if (campaign != null) {
                val co = JSONObject()
                co.put("size", campaign.sizeName)
                co.put("start", campaign.startDayKey)
                co.put("end", campaign.endDayKey)
                co.put("stopped", campaign.stoppedDayKey)
                o.put("campaign", co)
            }
            apps.put(o)
        }
        root.put("apps", apps)

        val staffArray = JSONArray()
        for (s in ledger.staff) {
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("role", s.role)
            o.put("email", s.email)
            o.put("phone", s.phone)
            o.put("bankName", s.bankName)
            o.put("accountNumber", s.accountNumber)
            o.put("monthlySalary", s.monthlySalary)

            val allowances = JSONArray()
            for (a in s.allowances) {
                val ao = JSONObject()
                ao.put("id", a.id)
                ao.put("name", a.name)
                ao.put("amount", a.amount)
                allowances.put(ao)
            }
            o.put("allowances", allowances)

            val payments = JSONArray()
            for (p in s.payments) {
                val po = JSONObject()
                po.put("id", p.id)
                po.put("date", p.dateMillis)
                po.put("amount", p.amount)
                po.put("item", p.itemKey)
                po.put("itemName", p.itemName)
                payments.put(po)
            }
            o.put("payments", payments)

            staffArray.put(o)
        }
        root.put("staff", staffArray)

        val renewalsArray = JSONArray()
        for (r in ledger.renewals) {
            val o = JSONObject()
            o.put("id", r.id)
            o.put("name", r.name)
            o.put("amount", r.amount)
            o.put("durationDays", r.durationDays)
            o.put("paid", r.paidMillis)
            o.put("expiry", r.expiryMillis)
            val days = JSONArray()
            for (d in r.reminderDays) days.put(d)
            o.put("reminderDays", days)
            renewalsArray.put(o)
        }
        root.put("renewals", renewalsArray)

        val withdrawalsArray = JSONArray()
        for (w in ledger.withdrawals) {
            val o = JSONObject()
            o.put("id", w.id)
            o.put("date", w.dateMillis)
            o.put("amount", w.amount)
            o.put("label", w.label)
            o.put("note", w.note)
            withdrawalsArray.put(o)
        }
        root.put("withdrawals", withdrawalsArray)

        return root.toString(2)
    }

    /** Throws if the text is not a valid backup. Older backups without staff still load. */
    fun fromJson(text: String): Ledger {
        val root = JSONObject(text)
        if (!root.has("apps") && !root.has("companyName")) {
            throw IllegalArgumentException("This is not a Neribo Group backup")
        }

        val company = Company(
            name = root.optString("companyName", DEFAULT_COMPANY_NAME).ifBlank { DEFAULT_COMPANY_NAME },
            expenses = jsonToEntries(root.optJSONArray("companyExpenses"))
        )

        val appsArray = root.optJSONArray("apps") ?: JSONArray()
        val apps = ArrayList<AppData>()
        for (i in 0 until appsArray.length()) {
            val o = appsArray.getJSONObject(i)
            apps.add(
                AppData(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    premiumPrice = o.optLong("premiumPrice"),
                    proPrice = o.optLong("proPrice"),
                    entries = jsonToEntries(o.optJSONArray("entries")),
                    users = jsonToUsers(o.optJSONArray("users")),
                    premiumBenefits = o.optString("premiumBenefits"),
                    proBenefits = o.optString("proBenefits"),
                    freeBenefits = o.optString("freeBenefits"),
                    activeUsers = o.optLong("activeUsers"),
                    activeUsersDayKey = o.optString("activeUsersDayKey"),
                    activeHistory = jsonToHistory(o.optJSONArray("activeHistory")),
                    adsEnabled = o.optBoolean("adsEnabled"),
                    lastRollDayKey = o.optString("lastRollDayKey"),
                    rolls = jsonToRolls(o.optJSONArray("rolls")),
                    playTx = jsonToPlayTx(o.optJSONArray("playTx")),
                    playSubsBalance = o.optLong("playSubsBalance"),
                    playAdsBalance = o.optLong("playAdsBalance"),
                    campaign = jsonToCampaign(o.optJSONObject("campaign"))
                )
            )
        }

        val staffArray = root.optJSONArray("staff") ?: JSONArray()
        val staff = ArrayList<Staff>()
        for (i in 0 until staffArray.length()) {
            val o = staffArray.getJSONObject(i)

            val allowances = ArrayList<Allowance>()
            val allowancesArray = o.optJSONArray("allowances") ?: JSONArray()
            for (j in 0 until allowancesArray.length()) {
                val ao = allowancesArray.getJSONObject(j)
                allowances.add(
                    Allowance(
                        id = ao.optString("id").ifBlank { UUID.randomUUID().toString() },
                        name = ao.optString("name"),
                        amount = ao.optLong("amount")
                    )
                )
            }

            val payments = ArrayList<Payment>()
            val paymentsArray = o.optJSONArray("payments") ?: JSONArray()
            for (j in 0 until paymentsArray.length()) {
                val po = paymentsArray.getJSONObject(j)
                payments.add(
                    Payment(
                        id = po.optString("id").ifBlank { UUID.randomUUID().toString() },
                        dateMillis = po.optLong("date"),
                        amount = po.optLong("amount"),
                        itemKey = po.optString("item"),
                        itemName = po.optString("itemName")
                    )
                )
            }

            staff.add(
                Staff(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    role = o.optString("role"),
                    email = o.optString("email"),
                    phone = o.optString("phone"),
                    bankName = o.optString("bankName"),
                    accountNumber = o.optString("accountNumber"),
                    monthlySalary = o.optLong("monthlySalary"),
                    allowances = allowances,
                    payments = payments
                )
            )
        }

        val renewalsArray = root.optJSONArray("renewals") ?: JSONArray()
        val renewals = ArrayList<Renewal>()
        for (i in 0 until renewalsArray.length()) {
            val o = renewalsArray.getJSONObject(i)
            val reminders = ArrayList<Int>()
            val daysArray = o.optJSONArray("reminderDays")
            if (daysArray != null) {
                for (j in 0 until daysArray.length()) reminders.add(daysArray.optInt(j))
            }
            renewals.add(
                Renewal(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = o.optString("name"),
                    amount = o.optLong("amount"),
                    durationDays = o.optInt("durationDays", 30),
                    paidMillis = o.optLong("paid"),
                    expiryMillis = o.optLong("expiry"),
                    reminderDays = reminders
                )
            )
        }

        val withdrawals = ArrayList<Withdrawal>()
        val withdrawalsArray = root.optJSONArray("withdrawals") ?: JSONArray()
        for (i in 0 until withdrawalsArray.length()) {
            val o = withdrawalsArray.optJSONObject(i) ?: continue
            withdrawals.add(
                Withdrawal(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    dateMillis = o.optLong("date"),
                    amount = o.optLong("amount"),
                    label = o.optString("label"),
                    note = o.optString("note")
                )
            )
        }

        return Ledger(
            company = company,
            apps = apps,
            staff = staff,
            renewals = renewals,
            withdrawals = withdrawals
        )
    }

    private fun usersToJson(users: List<AppUser>): JSONArray {
        val array = JSONArray()
        for (u in users) {
            val o = JSONObject()
            o.put("id", u.id)
            o.put("name", u.name)
            o.put("email", u.email)
            o.put("phone", u.phone)
            o.put("plan", u.plan.name)
            o.put("addedMillis", u.addedMillis)
            o.put("planExpiry", u.planExpiryMillis)
            o.put("planStart", u.planStartMillis)
            o.put("simulated", u.simulated)
            array.put(o)
        }
        return array
    }

    /** Skips any user without a name. An unknown plan becomes Free. */
    private fun jsonToUsers(array: JSONArray?): List<AppUser> {
        if (array == null) return emptyList()
        val list = ArrayList<AppUser>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val name = o.optString("name").trim()
            if (name.isEmpty()) continue
            val plan = runCatching { Plan.valueOf(o.optString("plan")) }.getOrDefault(Plan.FREE)
            list.add(
                AppUser(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    name = name,
                    email = o.optString("email"),
                    phone = o.optString("phone"),
                    plan = plan,
                    addedMillis = o.optLong("addedMillis"),
                    planExpiryMillis = o.optLong("planExpiry"),
                    planStartMillis = o.optLong("planStart"),
                    simulated = o.optBoolean("simulated")
                )
            )
        }
        return list
    }

    private fun rollsToJson(rolls: List<RollRecord>): JSONArray {
        val array = JSONArray()
        for (r in rolls) {
            val o = JSONObject()
            o.put("day", r.dayKey)
            val faces = JSONArray()
            for (f in r.faces) faces.put(f)
            o.put("faces", faces)
            o.put("newUsers", r.newUsers)
            o.put("active", r.active)
            o.put("premium", r.premium)
            o.put("pro", r.pro)
            o.put("renewed", r.renewed)
            o.put("lapsed", r.lapsed)
            o.put("subsNet", r.subsNet)
            o.put("adsNet", r.adsNet)
            o.put("campaign", r.campaign)
            array.put(o)
        }
        return array
    }

    private fun jsonToRolls(array: JSONArray?): List<RollRecord> {
        if (array == null) return emptyList()
        val list = ArrayList<RollRecord>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val day = o.optString("day")
            if (day.isBlank()) continue
            val faces = ArrayList<Int>()
            val facesArray = o.optJSONArray("faces")
            if (facesArray != null) {
                for (j in 0 until facesArray.length()) faces.add(facesArray.optInt(j, -1))
            }
            while (faces.size < DiceKind.values().size) faces.add(-1)
            list.add(
                RollRecord(
                    dayKey = day,
                    faces = faces,
                    newUsers = o.optLong("newUsers"),
                    active = o.optLong("active"),
                    premium = o.optLong("premium"),
                    pro = o.optLong("pro"),
                    renewed = o.optLong("renewed"),
                    lapsed = o.optLong("lapsed"),
                    subsNet = o.optLong("subsNet"),
                    adsNet = o.optLong("adsNet"),
                    campaign = o.optBoolean("campaign")
                )
            )
        }
        return list
    }

    private fun playTxToJson(list: List<PlayTx>): JSONArray {
        val array = JSONArray()
        for (t in list) {
            val o = JSONObject()
            o.put("id", t.id)
            o.put("kind", t.kind.name)
            o.put("label", t.label)
            o.put("gross", t.gross)
            o.put("fee", t.fee)
            o.put("net", t.net)
            o.put("date", t.dateMillis)
            array.put(o)
        }
        return array
    }

    private fun jsonToPlayTx(array: JSONArray?): List<PlayTx> {
        if (array == null) return emptyList()
        val list = ArrayList<PlayTx>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val kind = runCatching { PlayTxKind.valueOf(o.optString("kind")) }.getOrNull() ?: continue
            list.add(
                PlayTx(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    kind = kind,
                    label = o.optString("label"),
                    gross = o.optLong("gross"),
                    fee = o.optLong("fee"),
                    net = o.optLong("net"),
                    dateMillis = o.optLong("date")
                )
            )
        }
        return list
    }

    private fun jsonToCampaign(o: JSONObject?): Campaign? {
        if (o == null) return null
        val start = o.optString("start")
        val end = o.optString("end")
        if (start.isBlank() || end.isBlank()) return null
        return Campaign(
            sizeName = o.optString("size"),
            startDayKey = start,
            endDayKey = end,
            stoppedDayKey = o.optString("stopped")
        )
    }

    private fun historyToJson(history: List<DayCount>): JSONArray {
        val array = JSONArray()
        for (h in history) {
            val o = JSONObject()
            o.put("day", h.dayKey)
            o.put("count", h.count)
            array.put(o)
        }
        return array
    }

    private fun jsonToHistory(array: JSONArray?): List<DayCount> {
        if (array == null) return emptyList()
        val list = ArrayList<DayCount>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val day = o.optString("day")
            if (day.isBlank()) continue
            list.add(DayCount(dayKey = day, count = o.optLong("count")))
        }
        return list
    }

    private fun entriesToJson(entries: List<Entry>): JSONArray {
        val array = JSONArray()
        for (e in entries) {
            val o = JSONObject()
            o.put("id", e.id)
            o.put("title", e.title)
            o.put("amount", e.amount)
            o.put("isIncome", e.isIncome)
            o.put("date", e.dateMillis)
            o.put("auto", e.auto)
            array.put(o)
        }
        return array
    }

    private fun jsonToEntries(array: JSONArray?): List<Entry> {
        if (array == null) return emptyList()
        val list = ArrayList<Entry>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            list.add(
                Entry(
                    id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                    title = o.optString("title"),
                    amount = o.optLong("amount"),
                    isIncome = o.optBoolean("isIncome"),
                    dateMillis = o.optLong("date"),
                    auto = o.optBoolean("auto")
                )
            )
        }
        return list
    }
}
