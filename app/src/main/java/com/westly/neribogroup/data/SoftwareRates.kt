package com.westly.neribogroup.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * The live exchange rates ("the converter"). Uses only HttpURLConnection and org.json.
 * [fetchLive] blocks, so call it from a background thread. It never throws: any problem gives null.
 */
object SoftwareRates {

    private const val RATES_URL = "https://open.er-api.com/v6/latest/USD"
    private const val TIMEOUT_MS = 10_000

    /** Units of each supported currency per 1 USD, or null when nothing usable came back. */
    fun fetchLive(): Map<String, Double>? {
        var answer: Map<String, Double>? = null
        try {
            val url = URL(RATES_URL)
            if (url.protocol == "https") {
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "GET"
                    connection.connectTimeout = TIMEOUT_MS
                    connection.readTimeout = TIMEOUT_MS
                    connection.setRequestProperty("Accept", "application/json")
                    if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                        val text = connection.inputStream.bufferedReader(Charsets.UTF_8)
                            .use { it.readText() }
                        answer = parseResponse(text)
                    }
                } finally {
                    connection.disconnect()
                }
            }
        } catch (e: Throwable) {
            answer = null
        }
        return answer
    }

    /**
     * Accepts the answer only when result is "success", the naira rate is present and between
     * 300 and 10 000, and at least one rate is usable. Otherwise the whole answer is ignored.
     */
    fun parseResponse(text: String): Map<String, Double>? {
        var answer: Map<String, Double>? = null
        try {
            val root = JSONObject(text)
            val rates = root.optJSONObject("rates")
            if (root.optString("result") == "success" && rates != null) {
                val result = LinkedHashMap<String, Double>()
                for (code in SOFTWARE_CURRENCIES) {
                    if (code == "USD") continue
                    if (!rates.has(code)) continue
                    val value = rates.optDouble(code, 0.0)
                    if (value > 0.0 && !value.isNaN() && !value.isInfinite()) {
                        result[code] = value
                    }
                }
                val ngn = result["NGN"]
                if (ngn != null && ngn >= NGN_RATE_MIN && ngn <= NGN_RATE_MAX) {
                    answer = result
                }
            }
        } catch (e: Exception) {
            answer = null
        }
        return answer
    }
}
