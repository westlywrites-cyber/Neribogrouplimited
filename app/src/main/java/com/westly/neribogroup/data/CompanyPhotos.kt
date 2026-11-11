package com.westly.neribogroup.data

import android.content.Context
import java.io.File

/** Photos of company documents and tools. They are saved on this phone (one JPEG per item). */
object CompanyPhotos {

    private fun dir(context: Context): File = File(File(context.applicationContext.filesDir, "media"), "company")

    /** [kind] is "doc" or "tool". */
    fun file(context: Context, kind: String, id: String): File = File(dir(context), kind + "_" + id + ".jpg")

    fun write(context: Context, kind: String, id: String, bytes: ByteArray): Boolean {
        return try {
            val target = file(context, kind, id)
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
}
