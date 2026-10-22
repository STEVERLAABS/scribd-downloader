package com.stever.scribdownloader.ui

import com.stever.scribdownloader.data.HistoryEntry

enum class Phase { IDLE, ANALYZE, TRANSFER, DOWNLOAD, SAVED, ERROR }

/** Single source of truth for the Download screen (Task 9 state machine). */
data class DownloadUiState(
    val inputText: String = "",
    val analyzedDocId: Long? = null,
    val phase: Phase = Phase.IDLE,
    val engineRunning: Boolean = false,
    val engineStep: Int = 0,
    val statusKey: String? = null,
    val captchaVisible: Boolean = false,
    val progressBytes: Long? = null,
    val progressTotal: Long? = null,
    val errorKey: String? = null,
    val retryable: Boolean = false,
    val lastPasteUrl: String? = null,
    val lastSavedEntry: HistoryEntry? = null,
)
