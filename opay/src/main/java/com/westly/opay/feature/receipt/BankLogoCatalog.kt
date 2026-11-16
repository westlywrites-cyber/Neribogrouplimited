package com.westly.opay.feature.receipt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object BankLogoCatalog {
    private const val CATALOG_URL = "https://cdn.jsdelivr.net/gh/supermx1/nigerian-banks-api@main/data.json"
    private const val CACHE_FILE = "bank_logo_catalog.json"
    private const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    private const val TIMEOUT_MILLIS = 5000
    private const val HTTP_OK = 200

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val lock = Mutex()

    @Volatile
    private var memory: List<LogoBank>? = null

    suspend fun get(context: Context): List<LogoBank> {
        memory?.let { return it }
        val cacheDir = context.applicationContext.cacheDir
        return withContext(Dispatchers.IO) {
            lock.withLock {
                memory ?: load(File(cacheDir, CACHE_FILE)).also { if (it.isNotEmpty()) memory = it }
            }
        }
    }

    private fun load(file: File): List<LogoBank> {
        val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MILLIS
        if (fresh) {
            val cached = parse(readFile(file))
            if (cached.isNotEmpty()) return cached
        }
        val body = fetch()
        val fetched = parse(body)
        if (body != null && fetched.isNotEmpty()) {
            runCatching { file.writeText(body) }
            return fetched
        }
        return if (file.isFile) parse(readFile(file)) else emptyList()
    }

    private fun readFile(file: File): String? =
        try {
            file.readText()
        } catch (e: java.io.IOException) {
            null
        }

    private fun fetch(): String? =
        try {
            val connection = URL(CATALOG_URL).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                if (connection.responseCode == HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    null
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            null
        }

    private fun parse(body: String?): List<LogoBank> {
        if (body.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<LogoBank>>(body)
        } catch (e: Exception) {
            emptyList()
        }
    }
}
