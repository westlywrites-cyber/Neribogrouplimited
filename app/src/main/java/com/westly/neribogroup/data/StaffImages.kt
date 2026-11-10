package com.westly.neribogroup.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

/** The most photos one staff member can have. Enforced here, in the screen, and when loading. */
const val MAX_STAFF_IMAGES = 5

/** The biggest file the cloud's media storage accepts (2 MB). */
const val MAX_IMAGE_BYTES = 2_097_152

private val MEDIA_ID_REGEX = Regex("^[a-z0-9-]{1,64}$")

/** A photo id or staff id that is safe to use in a file name and in a cloud path. */
fun isValidMediaId(id: String): Boolean = MEDIA_ID_REGEX.matches(id)

/** A new photo id: a lowercase UUID. */
fun newImageId(): String = UUID.randomUUID().toString().lowercase()

/** Adds photos to a staff member. Never goes over [MAX_STAFF_IMAGES]; extra photos are left out. */
fun Staff.withImagesAdded(added: List<StaffImage>): Staff {
    val room = MAX_STAFF_IMAGES - images.size
    if (room <= 0 || added.isEmpty()) return this
    return copy(images = images + added.take(room))
}

/** Removes one photo (matched by id). */
fun Staff.withImageRemoved(imageId: String): Staff =
    if (images.none { it.id == imageId }) this else copy(images = images.filter { it.id != imageId })

/** Where the photo files of staff members live on the phone: filesDir/media/staff/<staffId>/<imageId>.jpg */
object StaffMedia {
    fun root(context: Context): File = File(File(context.applicationContext.filesDir, "media"), "staff")

    fun dir(context: Context, staffId: String): File = File(root(context), staffId)

    fun file(context: Context, staffId: String, imageId: String): File =
        File(dir(context, staffId), "$imageId.jpg")

    /** Writes the bytes to a temporary file and renames it, so a half-written photo never exists. */
    fun write(context: Context, staffId: String, imageId: String, bytes: ByteArray): Boolean {
        return try {
            val target = file(context, staffId, imageId)
            val dir = target.parentFile ?: return false
            if (!dir.exists() && !dir.mkdirs()) return false
            val tmp = File(dir, "$imageId.jpg.tmp")
            tmp.outputStream().use { it.write(bytes) }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        } catch (e: Exception) {
            false
        }
    }

    fun deleteImage(context: Context, staffId: String, imageId: String) {
        runCatching { file(context, staffId, imageId).delete() }
        val dir = dir(context, staffId)
        if (dir.exists() && (dir.list()?.isEmpty() == true)) runCatching { dir.delete() }
    }

    fun deleteStaffFolder(context: Context, staffId: String) {
        runCatching { dir(context, staffId).deleteRecursively() }
    }
}

/** The answer of [ImageProcessor.process]. */
sealed class ProcessedImage {
    class Ok(val bytes: ByteArray) : ProcessedImage()
    class Failed(val message: String) : ProcessedImage()
}

/**
 * Turns a picked photo into a small JPEG: reads it with inSampleSize (a 12-megapixel photo is never loaded
 * at full size), turns it the right way up using its EXIF orientation, makes the longest side at most
 * [MAX_SIDE] pixels, and saves it as JPEG quality about 80. The new JPEG has no EXIF or location data.
 * Blocks: call it from a background thread.
 */
object ImageProcessor {
    const val MAX_SIDE = 1280
    private const val START_QUALITY = 80
    private const val SOFT_LIMIT_BYTES = 1_500_000

    fun process(context: Context, uri: Uri, maxSide: Int = MAX_SIDE): ProcessedImage {
        val resolver = context.applicationContext.contentResolver
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return ProcessedImage.Failed("That photo could not be opened.")
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return ProcessedImage.Failed("That file is not a photo.")
            }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return ProcessedImage.Failed("That photo could not be read.")

            val orientation = try {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (e: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            // Turn it the right way up and scale it, in one step.
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(270f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }
            val longest = max(decoded.width, decoded.height)
            if (longest > maxSide) {
                val scale = maxSide.toFloat() / longest.toFloat()
                matrix.postScale(scale, scale)
            }
            var oriented = if (matrix.isIdentity) {
                decoded
            } else {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            }
            if (oriented !== decoded) decoded.recycle()

            // JPEG has no transparency: put a see-through photo on white.
            if (oriented.hasAlpha()) {
                val flat = Bitmap.createBitmap(oriented.width, oriented.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(flat)
                canvas.drawColor(Color.WHITE)
                canvas.drawBitmap(oriented, 0f, 0f, null)
                oriented.recycle()
                oriented = flat
            }

            var quality = START_QUALITY
            var bytes = encode(oriented, quality)
            while (bytes.size > SOFT_LIMIT_BYTES && quality > 40) {
                quality -= 10
                bytes = encode(oriented, quality)
            }
            oriented.recycle()
            if (bytes.size > MAX_IMAGE_BYTES) {
                ProcessedImage.Failed("That photo is still too big after shrinking.")
            } else {
                ProcessedImage.Ok(bytes)
            }
        } catch (e: OutOfMemoryError) {
            ProcessedImage.Failed("That photo is too big for this phone to handle.")
        } catch (e: Exception) {
            ProcessedImage.Failed("That photo could not be used.")
        }
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
