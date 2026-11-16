package com.westly.opay.feature.receipt

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ReceiptExporter {
    private const val FOLDER = "receipts"
    private const val PADDING_DP = 16
    private const val CHOOSER_TITLE = "Share Receipt"

    suspend fun shareImage(context: Context, card: Bitmap, background: Int, name: String, padded: Boolean) {
        val file = withContext(Dispatchers.IO) {
            val page = compose(context, card, background, padded)
            val target = newFile(context, name, "png")
            FileOutputStream(target).use { page.compress(Bitmap.CompressFormat.PNG, 100, it) }
            target
        }
        share(context, file, "image/png")
    }

    suspend fun sharePdf(context: Context, card: Bitmap, background: Int, name: String, padded: Boolean) {
        val file = withContext(Dispatchers.IO) {
            val page = compose(context, card, background, padded)
            val target = newFile(context, name, "pdf")
            val document = PdfDocument()
            val info = PdfDocument.PageInfo.Builder(page.width, page.height, 1).create()
            val pdfPage = document.startPage(info)
            pdfPage.canvas.drawBitmap(page, 0f, 0f, null)
            document.finishPage(pdfPage)
            FileOutputStream(target).use { document.writeTo(it) }
            document.close()
            target
        }
        share(context, file, "application/pdf")
    }

    private fun compose(context: Context, card: Bitmap, background: Int, padded: Boolean): Bitmap {
        val source = if (card.config == Bitmap.Config.HARDWARE) {
            card.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            card
        }
        val padding = if (padded) (PADDING_DP * context.resources.displayMetrics.density).toInt() else 0
        val page = Bitmap.createBitmap(
            source.width + padding * 2,
            source.height + padding * 2,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(page)
        canvas.drawColor(background)
        canvas.drawBitmap(source, padding.toFloat(), padding.toFloat(), null)
        return page
    }

    private fun newFile(context: Context, name: String, extension: String): File {
        val folder = File(context.cacheDir, FOLDER)
        folder.mkdirs()
        folder.listFiles()?.forEach { it.delete() }
        val safe = name.filter { it.isLetterOrDigit() }.ifEmpty { "receipt" }
        return File(folder, "OPay_Receipt_$safe.$extension")
    }

    private fun share(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, CHOOSER_TITLE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
