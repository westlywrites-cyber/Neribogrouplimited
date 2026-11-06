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
        root.put("version", 3)
        root.put("companyName", ledger.company.name)
        root.put("companyExpenses", entriesToJson(ledger.company.expenses))

        val apps = JSONArray()
        for (app in ledger.apps) {
            val o = JSONObject()
            o.put("id", app.id)
            o.put("name", app.name)
            o.put("totalUsers", app.totalUsers)
            o.put("premiumUsers", app.premiumUsers)
            o.put("proUsers", app.proUsers)
            o.put("dailyNewUsers", app.dailyNewUsers)
            o.put("premiumPrice", app.premiumPrice)
            o.put("proPrice", app.proPrice)
            o.put("premiumToday", app.premiumToday)
            o.put("proToday", app.proToday)
            o.put("todayKey", app.todayKey)
            o.put("newUsersDayKey", app.newUsersDayKey)
            o.put("entries", entriesToJson(app.entries))
            o.put("users", usersToJson(app.users))
            o.put("premiumBenefits", app.premiumBenefits)
            o.put("proBenefits", app.proBenefits)
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
                    totalUsers = o.optLong("totalUsers"),
                    premiumUsers = o.optLong("premiumUsers"),
                    proUsers = o.optLong("proUsers"),
                    dailyNewUsers = o.optLong("dailyNewUsers"),
                    premiumPrice = o.optLong("premiumPrice"),
                    proPrice = o.optLong("proPrice"),
                    premiumToday = o.optLong("premiumToday"),
                    proToday = o.optLong("proToday"),
                    todayKey = o.optString("todayKey"),
                    newUsersDayKey = o.optString("newUsersDayKey"),
                    entries = jsonToEntries(o.optJSONArray("entries")),
                    users = jsonToUsers(o.optJSONArray("users")),
                    premiumBenefits = o.optString("premiumBenefits"),
                    proBenefits = o.optString("proBenefits")
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

        return Ledger(company = company, apps = apps, staff = staff, renewals = renewals)
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
                    addedMillis = o.optLong("addedMillis")
                )
            )
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
