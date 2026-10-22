package com.stever.scribdownloader.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.stever.scribdownloader.data.HistoryStore

/**
 * System-level completion signal (manifest-registered). Runs even when the
 * app died mid-download: the history entry was appended at enqueue time
 * (Task 9), so this receiver can flip it to completed.
 */
class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (id <= 0L) return
        // Best-effort real size so the library row is complete even after death.
        val size = runCatching { Downloader(context).progress(id)?.total }.getOrNull()?.takeIf { it > 0 }
        HistoryStore(context).markCompleted(id, size)
    }
}
