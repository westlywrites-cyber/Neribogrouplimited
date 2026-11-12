package com.westly.neribogroup.data

import android.content.Context
import java.io.File

/**
 * Photos of company documents and equipment. They are saved on this phone (one JPEG per photo). A document has one
 * photo; a piece of equipment has up to [ToolItem.MAX_PHOTOS].
 */
object CompanyPhotos {

    private fun dir(context: Context): File = File(File(context.applicationContext.filesDir, "media"), "company")

    /** [kind] is "doc" or "tool". This is photo number 1 of the item. */
    fun file(context: Context, kind: String, id: String): File = File(dir(context), kind + "_" + id + ".jpg")

    /** Photo number [index] (0 to 4). Photo 0 keeps the older file name, so older photos still show. */
    fun fileAt(context: Context, kind: String, id: String, index: Int): File =
        if (index <= 0) file(context, kind, id) else File(dir(context), kind + "_" + id + "_" + index + ".jpg")

    fun write(context: Context, kind: String, id: String, bytes: ByteArray): Boolean =
        writeAt(context, kind, id, 0, bytes)

    fun writeAt(context: Context, kind: String, id: String, index: Int, bytes: ByteArray): Boolean {
        return try {
            val target = fileAt(context, kind, id, index)
            val folder = target.parentFile ?: return false
            if (!folder.exists() && !folder.mkdirs()) return false
            val tmp = File(folder, target.name + ".tmp")
            tmp.outputStream().use { it.write(bytes) }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            false
        }
    }

    fun delete(context: Context, kind: String, id: String) {
        try {
            file(context, kind, id).delete()
        } catch (e: Exception) {
            // nothing to do
        }
    }

    /** Removes photo [index] of an item that has [count] photos, and moves the later photos up one place. */
    fun removeAt(context: Context, kind: String, id: String, index: Int, count: Int) {
        try {
            fileAt(context, kind, id, index).delete()
            for (i in index + 1 until count) {
                val from = fileAt(context, kind, id, i)
                val to = fileAt(context, kind, id, i - 1)
                if (from.exists()) {
                    if (to.exists()) to.delete()
                    from.renameTo(to)
                }
            }
        } catch (e: Exception) {
            // nothing to do
        }
    }

    /** Removes every photo of an item. */
    fun deleteAll(context: Context, kind: String, id: String) {
        for (i in 0 until ToolItem.MAX_PHOTOS) {
            try {
                fileAt(context, kind, id, i).delete()
            } catch (e: Exception) {
                // nothing to do
            }
        }
    }
}
