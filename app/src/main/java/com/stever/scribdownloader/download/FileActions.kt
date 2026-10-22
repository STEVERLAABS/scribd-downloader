package com.stever.scribdownloader.download

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.stever.scribdownloader.data.HistoryEntry
import com.stever.scribdownloader.data.HistoryStore
import java.io.File

/** Library actions: open / share / delete. Precise outcomes, never fake success (Task 12). */
object FileActions {

    enum class DeleteOutcome { DONE, MISSING_FILE }

    fun resolveUri(context: Context, entry: HistoryEntry): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryMediaStore(context, entry)?.let { return it }
        }
        queryDownloadManager(context, entry)?.let { return it }
        return null
    }

    private fun queryMediaStore(context: Context, entry: HistoryEntry): Uri? = runCatching {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(entry.fileName),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                ContentUris.withAppendedId(collection, c.getLong(0))
            } else {
                null
            }
        }
    }.getOrNull()

    private fun queryDownloadManager(context: Context, entry: HistoryEntry): Uri? = runCatching {
        val dl = Downloader(context)
        val raw = dl.localUri(entry.downloadId) ?: return@runCatching null
        if ("file".equals(raw.scheme, ignoreCase = true)) {
            val file = File(raw.path.orEmpty())
            if (!file.exists()) return@runCatching null
            FileProvider.getUriForFile(context, fileProviderAuthority(context), file)
        } else {
            raw
        }
    }.getOrNull()

    private fun fileProviderAuthority(context: Context) = "${context.packageName}.fileprovider"

    fun open(context: Context, entry: HistoryEntry): Boolean {
        val uri = resolveUri(context, entry) ?: return false
        val i = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(i)
            true
        }.getOrElse { false }
    }

    fun share(context: Context, entry: HistoryEntry) {
        val uri = resolveUri(context, entry) ?: return
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, entry.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { context.startActivity(Intent.createChooser(i, entry.title)) }
    }

    /**
     * Primary: DownloadManager.remove(downloadId) — deletes record + file.
     * Fallback: ContentResolver.delete on the resolved MediaStore URI.
     * Neither → MISSING_FILE (displayed honestly, Task 12 / design §"Suppression").
     */
    fun delete(context: Context, entry: HistoryEntry): DeleteOutcome {
        val dl = Downloader(context)
        val removed = runCatching { dl.remove(entry.downloadId) }.getOrDefault(0)
        if (removed > 0) {
            HistoryStore(context).deleteEntry(entry.id)
            return DeleteOutcome.DONE
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = resolveUri(context, entry)
            if (uri == null || !isMediaStore(uri)) {
                HistoryStore(context).deleteEntry(entry.id)
                return DeleteOutcome.MISSING_FILE
            }
            val rows = runCatching {
                context.contentResolver.delete(uri, null, null)
            }.getOrDefault(0)
            HistoryStore(context).deleteEntry(entry.id)
            return if (rows > 0) DeleteOutcome.DONE else DeleteOutcome.MISSING_FILE
        }

        HistoryStore(context).deleteEntry(entry.id)
        return DeleteOutcome.MISSING_FILE
    }

    private fun isMediaStore(uri: Uri): Boolean {
        val host = uri.authority
        return host == "media" || (host != null && host.endsWith(".providers.media") || host.startsWith("com.android"))
    }
}
