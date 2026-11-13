package com.westly.neribogroup.feature.storageroom

import androidx.compose.runtime.mutableStateListOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The level of a console line. */
enum class RoomLevel { INFO, SYNC, WARN, ERROR, CHANGE }

/** One console line: when, how serious, where it came from and what happened. */
class RoomLine(
    val id: Long,
    val millis: Long,
    val level: RoomLevel,
    val source: String,
    val message: String
)

/**
 * The lines of the Storage room's console. They come from real events only: the owner's edits (from the room or
 * from the Storage page), PIN checks, refused changes, new users, sync status, storage warnings and lines other
 * phones send through the live feed. The newest 200 are kept, in memory, while the app runs. Use it on the main
 * thread.
 */
object StorageRoomLog {

    const val MAX_LINES = 200

    val lines = mutableStateListOf<RoomLine>()

    private var nextId = 1L

    fun add(level: RoomLevel, source: String, message: String) {
        val text = message.trim()
        if (text.isEmpty()) return
        lines.add(RoomLine(nextId, System.currentTimeMillis(), level, source, text))
        nextId += 1L
        while (lines.size > MAX_LINES) lines.removeAt(0)
    }

    /**
     * Adds the line unless the same text was added in the last two minutes. The same change can reach this phone
     * twice (once from the live feed, once from the sync), and it must show once.
     */
    fun addUnique(level: RoomLevel, source: String, message: String) {
        val text = message.trim()
        val since = System.currentTimeMillis() - DUPLICATE_WINDOW_MILLIS
        if (lines.any { it.millis >= since && it.message == text }) return
        add(level, source, text)
    }

    fun clear() {
        lines.clear()
    }

    fun timeText(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

    /** "Neribo Ai" -> "neribo-ai", the short name shown as the source of an app's lines. */
    fun slug(name: String): String =
        name.trim().lowercase(Locale.US).replace(Regex("\\s+"), "-")

    private const val DUPLICATE_WINDOW_MILLIS = 120_000L
}
