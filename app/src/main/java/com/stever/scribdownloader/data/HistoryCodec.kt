package com.stever.scribdownloader.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure JSON codec (android org.json, real implementation on the JVM during
 * tests via testImplementation "org.json:json"). Field names are part of the
 * storage contract — never rename without a migration.
 */
object HistoryCodec {
    fun encode(entries: List<HistoryEntry>): String {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("docId", e.docId)
                    .put("title", e.title)
                    .put("fileName", e.fileName)
                    .put("downloadId", e.downloadId)
                    .put("completed", e.completed)
                    .put("sizeBytes", e.sizeBytes)
                    .put("addedAt", e.addedAt),
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<HistoryEntry> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            runCatching {
                HistoryEntry(
                    id = o.getLong("id"),
                    docId = o.getLong("docId"),
                    title = o.getString("title"),
                    fileName = o.getString("fileName"),
                    downloadId = o.getLong("downloadId"),
                    completed = o.getBoolean("completed"),
                    sizeBytes = o.optLong("sizeBytes", 0L),
                    addedAt = o.getLong("addedAt"),
                )
            }.onFailure { Log.w(TAG, "skipping malformed history row", it) }.getOrNull()
        }
    }.onFailure { Log.w(TAG, "history JSON unreadable", it) }.getOrDefault(emptyList())

    private const val TAG = "HistoryCodec"
}
