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
        root.put("version", 1)
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
            o.put("entries", entriesToJson(app.entries))
            apps.put(o)
        }
        root.put("apps", apps)
        return root.toString(2)
    }

    /** Throws if the text is not a valid backup. */
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
                    entries = jsonToEntries(o.optJSONArray("entries"))
                )
            )
        }
        return Ledger(company = company, apps = apps)
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
                    dateMillis = o.optLong("date")
                )
            )
        }
        return list
    }
}
