package com.stever.scribdownloader.download

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager

data class DlProgress(val status: Int, val bytes: Long, val total: Long)

/**
 * Saving step (design: DownloadManager only, no extra permissions — the
 * system DownloadProvider writes into the shared Downloads store itself).
 *
 * Review findings applied: the destination name IS the sanitized+uniquified
 * file name (history/library parity with disk), notifications are hidden
 * (plan: "no notifications"), and the request rides the WebView cookie jar +
 * a desktop-grade UA exactly like the browser-driven download did.
 */
class Downloader(context: Context) {
    private val dm = context.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    fun enqueue(sourceUrl: String, fileName: String, title: String): Long {
        val request = DownloadManager.Request(Uri.parse(sourceUrl))
            .setTitle(title)
            .setDescription(fileName)
            .setMimeType(MIME_PDF)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        CookieManager.getInstance().getCookie(sourceUrl)?.let { request.addRequestHeader("Cookie", it) }
        request.addRequestHeader("User-Agent", UA)
        return dm.enqueue(request)
    }

    fun progress(id: Long): DlProgress? =
        query(id).use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                DlProgress(
                    status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                    bytes = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_SO_FAR)),
                    total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                )
            }
        }

    fun localUri(id: Long): Uri? =
        query(id).use { c ->
            if (!c.moveToFirst()) {
                null
            } else {
                c.getString(c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI))?.let { Uri.parse(it) }
            }
        }

    fun remove(id: Long): Int = dm.remove(id)

    private fun query(id: Long): Cursor =
        dm.query(DownloadManager.Query().setFilterById(id))

    private companion object {
        const val MIME_PDF = "application/pdf"
        const val UA = "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
