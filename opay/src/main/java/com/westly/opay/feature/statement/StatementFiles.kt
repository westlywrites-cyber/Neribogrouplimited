package com.westly.opay.feature.statement

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opens and saves a finished statement PDF. */
object StatementFiles {
    private const val MIME = "application/pdf"

    private fun uriOf(context: Context, record: StatementRecord): Uri? {
        if (record.filePath.isEmpty()) return null
        val file = File(record.filePath)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, context.packageName + ".statementfiles", file)
    }

    /** False when the file is gone or no app can open a PDF. */
    fun open(context: Context, record: StatementRecord): Boolean {
        val uri = uriOf(context, record) ?: return false
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(view)
            true
        } catch (e: ActivityNotFoundException) {
            false
        }
    }

    /** Copies the PDF to a place the person chose with the system "save as" screen. */
    suspend fun saveTo(context: Context, record: StatementRecord, target: Uri): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val source = File(record.filePath)
                val output = context.contentResolver.openOutputStream(target) ?: return@withContext false
                source.inputStream().use { input -> output.use { input.copyTo(it) } }
                true
            } catch (e: Exception) {
                false
            }
        }
}
