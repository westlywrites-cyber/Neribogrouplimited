package com.westly.opay.feature.register

import android.content.Context
import com.westly.opay.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The offline list of Nigerian states and their local government areas, read once from res/raw. */
object NigeriaLocations {
    @Volatile
    private var cache: Map<String, List<String>>? = null

    private val mapSerializer = MapSerializer(String.serializer(), ListSerializer(String.serializer()))

    /** Reads the file off the main thread the first time and returns the cached copy afterwards. */
    suspend fun load(context: Context): Map<String, List<String>> {
        cache?.let { return it }
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            val text = appContext.resources.openRawResource(R.raw.ng_states_lgas)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            parse(text).also { cache = it }
        }
    }

    fun parse(json: String): Map<String, List<String>> =
        Json.decodeFromString(mapSerializer, json)

    fun states(locations: Map<String, List<String>>): List<String> =
        locations.keys.sortedBy { it.lowercase() }

    fun lgasFor(locations: Map<String, List<String>>, state: String): List<String> =
        locations[state].orEmpty().sortedBy { it.lowercase() }

    /** Case-insensitive "contains" match used by the search box of the selection sheet. */
    fun filter(items: List<String>, query: String): List<String> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return items
        return items.filter { it.lowercase().contains(needle) }
    }
}
