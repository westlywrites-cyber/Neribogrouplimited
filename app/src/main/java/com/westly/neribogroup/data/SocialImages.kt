package com.westly.neribogroup.data

import android.content.Context
import java.io.File

/** Profile pictures are small: the longest side is at most this many pixels. */
const val SOCIAL_PHOTO_MAX_SIDE = 512

/**
 * Where the profile picture of a social account lives on the phone:
 * filesDir/media/socials/<accountId>/profile.jpg
 * Next to it, profile.ver holds the version of the picture that file shows, so a phone knows when the
 * cloud has a newer one. An account never gets a default or placeholder picture.
 */
object SocialMedia {
    private const val PROFILE_NAME = "profile.jpg"
    private const val VERSION_NAME = "profile.ver"

    fun root(context: Context): File = File(File(context.applicationContext.filesDir, "media"), "socials")

    fun dir(context: Context, accountId: String): File = File(root(context), accountId)

    fun file(context: Context, accountId: String): File = File(dir(context, accountId), PROFILE_NAME)

    private fun versionFile(context: Context, accountId: String): File = File(dir(context, accountId), VERSION_NAME)

    /** The version of the picture file on this phone, or 0 when there is none. */
    fun readVersion(context: Context, accountId: String): Int {
        if (!file(context, accountId).exists()) return 0
        return try {
            versionFile(context, accountId).readText(Charsets.UTF_8).trim().toIntOrNull() ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /** Writes the picture (temporary file, then rename, so a half-written picture never exists) and its version. */
    fun write(context: Context, accountId: String, bytes: ByteArray, version: Int): Boolean {
        return try {
            val target = file(context, accountId)
            val dir = target.parentFile ?: return false
            if (!dir.exists() && !dir.mkdirs()) return false
            val tmp = File(dir, "$PROFILE_NAME.tmp")
            tmp.outputStream().use { it.write(bytes) }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) return false
            versionFile(context, accountId).writeText(version.toString(), Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Removes the picture of one account from the phone. */
    fun deleteProfile(context: Context, accountId: String) {
        runCatching { file(context, accountId).delete() }
        runCatching { versionFile(context, accountId).delete() }
        val dir = dir(context, accountId)
        if (dir.exists() && (dir.list()?.isEmpty() == true)) runCatching { dir.delete() }
    }

    fun deleteAccountFolder(context: Context, accountId: String) {
        runCatching { dir(context, accountId).deleteRecursively() }
    }
}
