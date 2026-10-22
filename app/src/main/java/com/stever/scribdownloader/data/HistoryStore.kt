package com.stever.scribdownloader.data

import android.content.Context

/** One downloaded document record (JSON codec in HistoryCodec — unit tested). */
data class HistoryEntry(
    val id: Long,
    val docId: Long,
    val title: String,
    val fileName: String,
    val downloadId: Long,
    val completed: Boolean,
    val sizeBytes: Long,
    val addedAt: Long,
)

/** SharedPreferences persistence — small volume by design (design doc §5). */
class HistoryStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun all(): List<HistoryEntry> = HistoryCodec.decode(prefs.getString(KEY, "[]") ?: "[]")

    private fun save(entries: List<HistoryEntry>) {
        prefs.edit().putString(KEY, HistoryCodec.encode(entries)).apply()
    }

    /** Called at enqueue time so the system receiver can complete entries even after process death. */
    fun append(entry: HistoryEntry) {
        val next = all().filterNot { it.id == entry.id } + entry
        save(next.sortedByDescending { it.addedAt })
    }

    fun markCompleted(downloadId: Long, sizeBytes: Long?) {
        val next = all().map {
            if (it.downloadId == downloadId && !it.completed) {
                it.copy(completed = true, sizeBytes = sizeBytes ?: it.sizeBytes)
            } else it
        }
        save(next)
    }

    fun deleteEntry(entryId: Long) {
        save(all().filterNot { it.id == entryId })
    }

    private companion object {
        const val PREF = "stever_history"
        const val KEY = "entries"
    }
}
