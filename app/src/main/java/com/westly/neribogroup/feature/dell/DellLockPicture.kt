package com.westly.neribogroup.feature.dell

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.io.FileOutputStream

/**
 * The picture behind the Dell lock screen. Until the owner picks a picture, the Dell shows the built-in lock picture.
 * A picture the owner picks is copied (made smaller) into the app's private no-backup folder on this phone only:
 * it is never put in the ledger, the backup file, the cloud sync or Supabase.
 */
object DellLockPicture {
    private const val FILE_NAME = "dell_lock_custom.jpg"
    private const val MAX_SIDE = 1920

    /** Changes whenever the lock picture changes, so the lock screen redraws at once. */
    var version by mutableIntStateOf(0)
        private set

    private fun file(context: Context): File = File(context.applicationContext.noBackupFilesDir, FILE_NAME)

    fun hasCustom(context: Context): Boolean = file(context).exists()

    /** The picture the owner picked, or null when the built-in wallpaper is in use. */
    fun loadCustom(context: Context): ImageBitmap? {
        val f = file(context)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.path)?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    /** Copies the picked picture in, made smaller and turned the right way up. Call it off the main thread. */
    fun saveFrom(context: Context, uri: Uri): Boolean {
        return try {
            val resolver = context.applicationContext.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Size-only read: decodeStream returns null here by design, so do not treat null as a failure.
            val opened = resolver.openInputStream(uri) ?: return false
            opened.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE * 2) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return false

            val degrees = try {
                resolver.openInputStream(uri)?.use { stream ->
                    when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                        ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } ?: 0
            } catch (e: Exception) {
                0
            }

            val longest = maxOf(decoded.width, decoded.height)
            val scale = if (longest > MAX_SIDE) MAX_SIDE.toFloat() / longest else 1f
            val matrix = Matrix()
            if (degrees != 0) matrix.postRotate(degrees.toFloat())
            if (scale != 1f) matrix.postScale(scale, scale)
            val finished: Bitmap = if (degrees != 0 || scale != 1f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            } else {
                decoded
            }

            val target = file(context)
            val temp = File(target.parentFile, FILE_NAME + ".tmp")
            FileOutputStream(temp).use { out ->
                if (!finished.compress(Bitmap.CompressFormat.JPEG, 88, out)) return false
            }
            if (target.exists()) target.delete()
            temp.renameTo(target)
        } catch (e: Exception) {
            false
        }
    }

    /** Called on the main thread after a new picture was saved. */
    fun changed() {
        version += 1
    }

    /** Goes back to the built-in lock picture. */
    fun reset(context: Context) {
        try {
            file(context).delete()
        } catch (e: Exception) {
            // Nothing more to remove.
        }
        version += 1
    }
}
