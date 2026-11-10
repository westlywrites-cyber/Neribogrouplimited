package com.westly.neribogroup.cloud

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** The answer of signIn / refresh. */
sealed class AuthResult {
    object Ok : AuthResult()
    object InvalidCredentials : AuthResult()
    object Offline : AuthResult()
    data class ServerError(val code: Int) : AuthResult()
}

/** The answer of one RPC call. */
sealed class ApiResult {
    data class Ok(val json: JSONObject) : ApiResult()
    object Offline : ApiResult()
    object AuthExpired : ApiResult()
    /** [snippet] is only the server's short error message, never a request body or a token. */
    data class Http(val code: Int, val snippet: String) : ApiResult()
    /** HTTP 200 whose JSON says status "error". */
    data class AppError(val code: String, val message: String) : ApiResult()
}

/** The answer of one media Storage call (upload, download, delete). */
sealed class MediaResult {
    class Ok(val bytes: ByteArray) : MediaResult()
    object NotFound : MediaResult()
    /** The object already exists (an upload without upsert). */
    object Duplicate : MediaResult()
    object Offline : MediaResult()
    object AuthExpired : MediaResult()
    /** [snippet] is only the server's short error message. */
    data class Http(val code: Int, val snippet: String) : MediaResult()
}

/** A tiny HTTP client for Supabase Auth and RPC functions. Blocking: call it from a background thread. */
class SupabaseApi(private val config: CloudConfig) {

    private companion object {
        const val STORAGE_OBJECT = "/storage/v1/object/neribo-media"
        const val STORAGE_AUTH_OBJECT = "/storage/v1/object/authenticated/neribo-media"
    }

    private class RawResponse(val code: Int, val body: String)

    private fun request(
        url: String,
        headers: Map<String, String>,
        body: String,
        readTimeoutMs: Int
    ): RawResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = readTimeoutMs
            conn.doInput = true
            conn.doOutput = true
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            val bytes = body.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            return RawResponse(code, text)
        } finally {
            conn.disconnect()
        }
    }

    /** Reads access_token / refresh_token / expires_in and saves the session. */
    private fun storeTokens(body: String): Boolean {
        val json = JSONObject(body)
        val access = json.optString("access_token", "")
        val refresh = json.optString("refresh_token", "")
        if (access.isEmpty() || refresh.isEmpty()) return false
        val seconds = json.optLong("expires_in", 3600L)
        config.saveSession(access, refresh, System.currentTimeMillis() + seconds * 1000L)
        return true
    }

    fun signIn(email: String, password: String): AuthResult {
        val url = config.projectUrl
        val key = config.anonKey
        if (url.isEmpty() || key.isEmpty()) return AuthResult.ServerError(0)
        return try {
            val body = JSONObject().put("email", email).put("password", password).toString()
            val r = request("$url/auth/v1/token?grant_type=password", mapOf("apikey" to key), body, 30_000)
            when {
                r.code == 200 -> if (storeTokens(r.body)) AuthResult.Ok else AuthResult.ServerError(200)
                r.code == 400 || r.code == 401 -> AuthResult.InvalidCredentials
                else -> AuthResult.ServerError(r.code)
            }
        } catch (e: IOException) {
            AuthResult.Offline
        } catch (e: Exception) {
            AuthResult.ServerError(-1)
        }
    }

    /** If the refresh is refused the session is over: tokens are cleared, local data is untouched. */
    @Synchronized
    fun refresh(): AuthResult {
        val url = config.projectUrl
        val key = config.anonKey
        val token = config.refreshToken
        if (url.isEmpty() || key.isEmpty() || token.isEmpty()) {
            config.clearSession()
            return AuthResult.InvalidCredentials
        }
        return try {
            val body = JSONObject().put("refresh_token", token).toString()
            val r = request("$url/auth/v1/token?grant_type=refresh_token", mapOf("apikey" to key), body, 30_000)
            when {
                r.code == 200 -> {
                    if (storeTokens(r.body)) {
                        AuthResult.Ok
                    } else {
                        AuthResult.ServerError(200)
                    }
                }
                r.code == 400 || r.code == 401 -> {
                    config.clearSession()
                    AuthResult.InvalidCredentials
                }
                else -> AuthResult.ServerError(r.code)
            }
        } catch (e: IOException) {
            AuthResult.Offline
        } catch (e: Exception) {
            AuthResult.ServerError(-1)
        }
    }

    /** Best effort on the server, then the tokens are cleared locally. Never deletes local data. */
    fun signOut() {
        val url = config.projectUrl
        val key = config.anonKey
        val token = config.accessToken
        if (url.isNotEmpty() && key.isNotEmpty() && token.isNotEmpty()) {
            try {
                request(
                    "$url/auth/v1/logout",
                    mapOf("apikey" to key, "Authorization" to "Bearer $token"),
                    "{}",
                    15_000
                )
            } catch (e: Exception) {
                // Best effort only.
            }
        }
        config.clearSession()
    }

    /** Calls POST {url}/rest/v1/rpc/{name} with named parameters. Refreshes the session when needed. */
    fun rpc(name: String, body: JSONObject, longTimeout: Boolean = false): ApiResult {
        val url = config.projectUrl
        val key = config.anonKey
        if (url.isEmpty() || key.isEmpty() || !config.signedIn) return ApiResult.AuthExpired
        val readTimeout = if (longTimeout) 60_000 else 30_000
        return try {
            if (config.expiresAtMillis - System.currentTimeMillis() < 60_000L) {
                when (val r = refresh()) {
                    AuthResult.Ok -> {}
                    AuthResult.Offline -> return ApiResult.Offline
                    AuthResult.InvalidCredentials -> return ApiResult.AuthExpired
                    is AuthResult.ServerError -> return ApiResult.Http(r.code, "")
                }
            }
            val text = body.toString()
            var raw = call(url, key, name, text, readTimeout)
            if (raw.code == 401) {
                when (val r = refresh()) {
                    AuthResult.Ok -> {}
                    AuthResult.Offline -> return ApiResult.Offline
                    AuthResult.InvalidCredentials -> return ApiResult.AuthExpired
                    is AuthResult.ServerError -> return ApiResult.Http(r.code, "")
                }
                raw = call(url, key, name, text, readTimeout)
                if (raw.code == 401) return ApiResult.AuthExpired
            }
            toApiResult(raw)
        } catch (e: IOException) {
            ApiResult.Offline
        } catch (e: Exception) {
            ApiResult.Http(-1, "unexpected error")
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Media Storage (bucket neribo-media, private, 2 MB, image/jpeg only)
    // ---------------------------------------------------------------------------------------------

    /** Uploads one JPEG. With [upsert] false an existing object is refused (staff photos); true replaces it. */
    fun uploadMedia(path: String, bytes: ByteArray, upsert: Boolean): MediaResult =
        storage("POST", "$STORAGE_OBJECT/$path", bytes, upsert, 90_000)

    /** Downloads one object (needs the signed-in user's own path). */
    fun downloadMedia(path: String): MediaResult =
        storage("GET", "$STORAGE_AUTH_OBJECT/$path", null, false, 90_000)

    /** Deletes one object. A missing object is reported as [MediaResult.NotFound]. */
    fun deleteMedia(path: String): MediaResult =
        storage("DELETE", "$STORAGE_OBJECT/$path", null, false, 30_000)

    private class RawBytes(val code: Int, val bytes: ByteArray)

    private fun storage(method: String, endpoint: String, body: ByteArray?, upsert: Boolean, readTimeoutMs: Int): MediaResult {
        val url = config.projectUrl
        val key = config.anonKey
        if (url.isEmpty() || key.isEmpty() || !config.signedIn) return MediaResult.AuthExpired
        return try {
            if (config.expiresAtMillis - System.currentTimeMillis() < 60_000L) {
                when (val r = refresh()) {
                    AuthResult.Ok -> {}
                    AuthResult.Offline -> return MediaResult.Offline
                    AuthResult.InvalidCredentials -> return MediaResult.AuthExpired
                    is AuthResult.ServerError -> return MediaResult.Http(r.code, "")
                }
            }
            var raw = storageRaw(method, url + endpoint, key, body, upsert, readTimeoutMs)
            if (needsNewToken(raw)) {
                when (val r = refresh()) {
                    AuthResult.Ok -> {}
                    AuthResult.Offline -> return MediaResult.Offline
                    AuthResult.InvalidCredentials -> return MediaResult.AuthExpired
                    is AuthResult.ServerError -> return MediaResult.Http(r.code, "")
                }
                raw = storageRaw(method, url + endpoint, key, body, upsert, readTimeoutMs)
                if (needsNewToken(raw)) return MediaResult.AuthExpired
            }
            classifyStorage(raw)
        } catch (e: IOException) {
            MediaResult.Offline
        } catch (e: Exception) {
            MediaResult.Http(-1, "unexpected error")
        }
    }

    /** Storage answers an expired token with 401, or sometimes with 400/403 and a message about the JWT. */
    private fun needsNewToken(raw: RawBytes): Boolean {
        if (raw.code == 401) return true
        if (raw.code == 400 || raw.code == 403) {
            return String(raw.bytes.take(400).toByteArray(), Charsets.UTF_8).contains("jwt", ignoreCase = true)
        }
        return false
    }

    private fun storageRaw(
        method: String,
        fullUrl: String,
        key: String,
        body: ByteArray?,
        upsert: Boolean,
        readTimeoutMs: Int
    ): RawBytes {
        val conn = URL(fullUrl).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = readTimeoutMs
            conn.doInput = true
            conn.setRequestProperty("apikey", key)
            conn.setRequestProperty("Authorization", "Bearer " + config.accessToken)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "image/jpeg")
                conn.setRequestProperty("x-upsert", if (upsert) "true" else "false")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            return RawBytes(code, bytes)
        } finally {
            conn.disconnect()
        }
    }

    private fun classifyStorage(raw: RawBytes): MediaResult {
        if (raw.code in 200..299) return MediaResult.Ok(raw.bytes)
        val text = String(raw.bytes.take(600).toByteArray(), Charsets.UTF_8)
        if (raw.code == 404) return MediaResult.NotFound
        // Storage reports a missing object or an existing one inside a 400 with a statusCode in the text.
        if (text.contains("\"statusCode\":\"404\"") || text.contains("\"statusCode\":404") ||
            text.contains("not_found", ignoreCase = true) || text.contains("Object not found", ignoreCase = true)
        ) {
            return MediaResult.NotFound
        }
        if (raw.code == 409 || text.contains("Duplicate", ignoreCase = true) ||
            text.contains("already exists", ignoreCase = true)
        ) {
            return MediaResult.Duplicate
        }
        return MediaResult.Http(raw.code, errorSnippet(text))
    }

    private fun call(url: String, key: String, name: String, text: String, readTimeout: Int): RawResponse {
        val headers = mapOf(
            "apikey" to key,
            "Authorization" to "Bearer " + config.accessToken
        )
        return request("$url/rest/v1/rpc/$name", headers, text, readTimeout)
    }

    private fun toApiResult(raw: RawResponse): ApiResult {
        if (raw.code in 200..299) {
            val json = parseBody(raw.body)
            if (json.optString("status") == "error") {
                return ApiResult.AppError(json.optString("code", ""), json.optString("message", ""))
            }
            return ApiResult.Ok(json)
        }
        return ApiResult.Http(raw.code, errorSnippet(raw.body))
    }

    /** An object stays an object, a one-row array becomes that row, any other array goes under "rows". */
    private fun parseBody(text: String): JSONObject {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return JSONObject()
        return try {
            when (val value = JSONTokener(trimmed).nextValue()) {
                is JSONObject -> value
                is JSONArray -> {
                    val only = if (value.length() == 1) value.optJSONObject(0) else null
                    only ?: JSONObject().put("rows", value)
                }
                else -> JSONObject().put("value", value ?: JSONObject.NULL)
            }
        } catch (e: Exception) {
            JSONObject()
        }
    }

    /** Only the server's own short message (never the request, never tokens). */
    private fun errorSnippet(body: String): String {
        return try {
            val o = JSONObject(body.trim())
            val parts = ArrayList<String>()
            for (k in listOf("code", "message", "msg", "hint")) {
                val v = o.optString(k, "")
                if (v.isNotEmpty()) parts.add(v)
            }
            parts.joinToString(" | ").take(200)
        } catch (e: Exception) {
            ""
        }
    }
}
