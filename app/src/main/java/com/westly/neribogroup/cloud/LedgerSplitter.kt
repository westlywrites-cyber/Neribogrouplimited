package com.westly.neribogroup.cloud

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** SHA-256 of the UTF-8 bytes of [text], lowercase hex. */
fun sha256Hex(text: String): String = sha256Hex(text.toByteArray(Charsets.UTF_8))

fun sha256Hex(bytes: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
    val hex = "0123456789abcdef"
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
        val v = b.toInt() and 0xff
        sb.append(hex[v ushr 4])
        sb.append(hex[v and 0x0f])
    }
    return sb.toString()
}

/** One cloud document: its key, its exact text and the SHA-256 of that text. */
data class SplitDoc(val key: String, val text: String, val sha256: String)

/**
 * Pure functions that cut the ledger text (LedgerStorage.toJson) into cloud documents and join them again.
 * Values are copied exactly as written; only the keys are distributed.
 */
object LedgerSplitter {

    const val DOC_COMPANY = "company"
    const val DOC_STAFF = "staff"
    const val DOC_CASES = "cases"
    const val DOC_SOFTWARE = "software"
    const val APP_PREFIX = "app:"

    val KEY_REGEX = Regex("^[a-z0-9:_.-]{1,100}$")

    private val COMPANY_KEYS = listOf("version", "companyName", "companyExpenses", "renewals", "withdrawals")
    private val CASES_KEYS = listOf("cases", "nextCaseNumber", "casesClockMillis")
    private val KNOWN_ROOT_KEYS = COMPANY_KEYS + CASES_KEYS + listOf("staff", "software", "apps")

    /** Throws if the text is not a ledger. The same text always gives the same documents. */
    fun split(ledgerText: String): List<SplitDoc> {
        val root = JSONObject(ledgerText)
        val result = ArrayList<SplitDoc>()

        // company: its own keys, then any key this version does not know, then appOrder
        val company = JSONObject()
        for (k in COMPANY_KEYS) if (root.has(k)) company.put(k, root.get(k))
        val unknown = ArrayList<String>()
        val names = root.keys()
        while (names.hasNext()) {
            val k = names.next()
            if (k !in KNOWN_ROOT_KEYS && k != "appOrder") unknown.add(k)
        }
        for (k in unknown) company.put(k, root.get(k))

        val apps = root.optJSONArray("apps") ?: JSONArray()
        val order = JSONArray()
        val appDocs = ArrayList<SplitDoc>()
        val usedKeys = HashSet<String>()
        for (i in 0 until apps.length()) {
            val o = apps.optJSONObject(i)
                ?: throw IllegalArgumentException("An app entry is not an object")
            val id = o.optString("id", "")
            if (id.isEmpty()) throw IllegalArgumentException("An app has no id")
            order.put(id)
            var key = keyForApp(id)
            var n = 2
            while (!usedKeys.add(key)) {
                key = keyForApp(id).take(90) + "_" + n
                n++
            }
            appDocs.add(makeDoc(key, o.toString()))
        }
        company.put("appOrder", order)
        result.add(makeDoc(DOC_COMPANY, company.toString()))

        val staff = JSONObject()
        if (root.has("staff")) staff.put("staff", root.get("staff"))
        result.add(makeDoc(DOC_STAFF, staff.toString()))

        val cases = JSONObject()
        for (k in CASES_KEYS) if (root.has(k)) cases.put(k, root.get(k))
        result.add(makeDoc(DOC_CASES, cases.toString()))

        val software = JSONObject()
        if (root.has("software")) software.put("software", root.get("software"))
        result.add(makeDoc(DOC_SOFTWARE, software.toString()))

        result.addAll(appDocs)
        return result
    }

    /**
     * Rebuilds the ledger text from documents (key -> exact text). Throws IllegalArgumentException when a
     * needed document is missing or invalid; the caller then applies nothing.
     */
    fun join(docs: Map<String, String>): String {
        val company = parse(docs, DOC_COMPANY)
        val staff = parse(docs, DOC_STAFF)
        val cases = parse(docs, DOC_CASES)
        val software = parse(docs, DOC_SOFTWARE)
        if (!staff.has("staff")) throw IllegalArgumentException("The staff document is incomplete")
        if (!cases.has("cases")) throw IllegalArgumentException("The cases document is incomplete")
        if (!software.has("software")) throw IllegalArgumentException("The software document is incomplete")

        // app documents
        val found = ArrayList<Pair<String, JSONObject>>()
        for (key in docs.keys.sorted()) {
            if (!key.startsWith(APP_PREFIX)) continue
            val o = parse(docs, key)
            if (o.optBoolean("deleted", false) && !o.has("id")) continue // a removed app
            if (!o.has("id")) throw IllegalArgumentException("An app document has no id")
            found.add(Pair(key, o))
        }
        val appOrder = ArrayList<String>()
        val orderArray = company.optJSONArray("appOrder")
        if (orderArray != null) {
            for (i in 0 until orderArray.length()) appOrder.add(orderArray.optString(i, ""))
        }
        // every app named in appOrder must really be there (protects against a half-read cloud)
        for (id in appOrder) {
            if (found.none { it.second.optString("id") == id }) {
                throw IllegalArgumentException("An app document is missing")
            }
        }
        val position = HashMap<String, Int>()
        for ((index, id) in appOrder.withIndex()) if (!position.containsKey(id)) position[id] = index
        val sorted = found.sortedWith(
            compareBy<Pair<String, JSONObject>>(
                { position[it.second.optString("id")] ?: Int.MAX_VALUE },
                { it.first }
            )
        )
        val appsArray = JSONArray()
        for (p in sorted) appsArray.put(p.second)

        val root = JSONObject()
        for (k in listOf("version", "companyName", "companyExpenses")) {
            if (company.has(k)) root.put(k, company.get(k))
        }
        root.put("apps", appsArray)
        root.put("staff", staff.get("staff"))
        for (k in listOf("renewals", "withdrawals")) {
            if (company.has(k)) root.put(k, company.get(k))
        }
        for (k in CASES_KEYS) if (cases.has(k)) root.put(k, cases.get(k))
        root.put("software", software.get("software"))
        val names = company.keys()
        while (names.hasNext()) {
            val k = names.next()
            if (k !in COMPANY_KEYS && k != "appOrder") root.put(k, company.get(k))
        }
        return root.toString()
    }

    /** "app:" + the id made safe for the server's key rule (lowercase, only a-z 0-9 : _ . -). */
    fun keyForApp(id: String): String {
        val sb = StringBuilder(APP_PREFIX)
        for (ch in id.lowercase()) {
            val ok = (ch in 'a'..'z') || (ch in '0'..'9') || ch == ':' || ch == '_' || ch == '.' || ch == '-'
            sb.append(if (ok) ch else '_')
        }
        return sb.toString().take(95)
    }

    private fun makeDoc(key: String, text: String): SplitDoc = SplitDoc(key, text, sha256Hex(text))

    private fun parse(docs: Map<String, String>, key: String): JSONObject {
        val text = docs[key] ?: throw IllegalArgumentException("Document $key is missing")
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Document $key is not valid")
        }
    }
}
