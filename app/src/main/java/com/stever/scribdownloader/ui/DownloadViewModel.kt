package com.stever.scribdownloader.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stever.scribdownloader.data.HistoryEntry
import com.stever.scribdownloader.data.HistoryStore
import com.stever.scribdownloader.engine.TitleParser
import com.stever.scribdownloader.data.TitleFetcher
import com.stever.scribdownloader.download.DlProgress
import com.stever.scribdownloader.download.Downloader
import com.stever.scribdownloader.download.FilenameResolver
import com.stever.scribdownloader.engine.AnalyzedUrl
import com.stever.scribdownloader.engine.AutopilotEngine
import com.stever.scribdownloader.engine.EngineEvent
import com.stever.scribdownloader.engine.PdfEligibility
import com.stever.scribdownloader.engine.PdfValidator
import com.stever.scribdownloader.engine.UrlAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Orchestrates the full pipeline: analyze → engine (transfer) → HEAD validate
 * → DownloadManager (download) → history entry completed (saved).
 * Precise errors, no fake success (design doc §1, §3).
 */
class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val historyStore = HistoryStore(app)
    private val downloader = Downloader(app)

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state

    private val _library = MutableStateFlow(historyStore.all().sortedByDescending { it.addedAt })
    val library: StateFlow<List<HistoryEntry>> = _library

    // Engine wiring: the screen owns the WebView + engine; the VM consumes its events.
    private var engine: AutopilotEngine? = null
    private var eventCollector: Job? = null
    private var pollJob: Job? = null

    private var activeDownloadId: Long = -1
    private var lastAnalyzed: AnalyzedUrl? = null
    private var lastUpstreamFileName: String = ""

    fun bindEngine(engine: AutopilotEngine) {
        this.engine = engine
        eventCollector?.cancel()
        eventCollector = viewModelScope.launch {
            engine.events.collect { onEngineEvent(it) }
        }
    }

    /** Screen (tab) left composition while a flow was live — be honest about it. */
    fun onScreenLost() {
        if (_state.value.phase != Phase.DOWNLOAD && _state.value.engineRunning) {
            cancel()
        }
    }

    fun onUrlChange(text: String) {
        val analyzed = UrlAnalyzer.analyze(text)
        _state.value = _state.value.copy(
            inputText = text,
            analyzedDocId = analyzed?.docId,
            // Fresh typing leaves an error/finished state cleanly behind.
            phase = if (_state.value.phase == Phase.ERROR || _state.value.phase == Phase.SAVED) Phase.IDLE else _state.value.phase,
            errorKey = null,
            statusKey = null,
        )
    }

    /**
     * Priority no.1: from the user pressing Download, everything run further
     * happens automatically unless the service shows a turnstile challenge.
     */
    fun submit() {
        val current = _state.value
        if (current.engineRunning) return
        val analyzed = UrlAnalyzer.analyze(current.inputText)
        if (analyzed == null) {
            _state.value = current.copy(phase = Phase.ERROR, errorKey = "error_invalid_url", retryable = false)
            return
        }
        lastAnalyzed = analyzed
        lastUpstreamFileName = ""
        _state.value = _state.value.copy(
            phase = Phase.ANALYZE,
            lastPasteUrl = analyzed.pasteUrl,
            errorKey = null,
            statusKey = null,
            lastSavedEntry = null,
            engineRunning = true,
        )
        // Document title fetch runs in parallel; the doc name feeds the file name.
        viewModelScope.launch(Dispatchers.IO) {
            lastUpstreamFileName = analyzed.run {
                val html = TitleFetcher().fetch(pasteUrl)
                val title = if (html != null) TitleParser.parseTitle(html, "Scribd_$docId") else "Scribd_$docId"
                UrlAnalyzer.sanitizeFileName(title, docId)
            }
        }
        val eng = engine
        if (eng == null) {
            showError("error_network")
        } else {
            eng.start(analyzed.pasteUrl)
        }
    }

    fun retry() {
        val url = _state.value.lastPasteUrl ?: return
        _state.value = _state.value.copy(inputText = url, phase = Phase.IDLE, errorKey = null, statusKey = null)
        submit()
    }

    fun cancel() {
        pollJob?.cancel()
        pollJob = null
        // NOTE: the event collector stays alive for the VM lifetime — killing it
        // here would leave Retry/start collecting nothing (PdfFound ignored).
        engine?.cancel()
        _state.value = _state.value.copy(
            phase = Phase.ERROR,
            engineRunning = false,
            captchaVisible = false,
            errorKey = "error_cancelled",
            retryable = true,
        )
    }

    private fun onEngineEvent(event: EngineEvent) {
        when (event) {
            is EngineEvent.Status -> {
                // verify_manual keeps the engine card expanded so the user can tap the checkbox.
                val showCaptcha = event.key == "verify_manual"
                _state.value = _state.value.copy(
                    phase = Phase.TRANSFER,
                    engineStep = event.step,
                    statusKey = event.key,
                    captchaVisible = showCaptcha || event.key == "captcha",
                )
            }
            is EngineEvent.Captcha -> {
                _state.value = _state.value.copy(captchaVisible = true)
            }
            is EngineEvent.PdfFound -> beginDownload(event.url)
            is EngineEvent.Failed -> showError(event.key)
            is EngineEvent.TimedOut -> showError(event.key)
        }
    }

    private fun beginDownload(upstreamUrl: String) {
        viewModelScope.launch {
            val head = withContext(Dispatchers.IO) { PdfValidator.head(upstreamUrl) }
            if (head == null) {
                showError("error_network")
                return@launch
            }
            val eligibility = PdfValidator.evaluate(head.mime, head.contentLength.takeIf { it >= 0 })
            if (eligibility != PdfEligibility.OK) {
                // Extension maps both "wrong type" and "too small/corrupt" to a damaged/unavailable file.
                showError("error_damaged")
                return@launch
            }

            val analyzed = lastAnalyzed ?: run { showError("error_network"); return@launch }
            stopEngine()
            _state.value = _state.value.copy(phase = Phase.DOWNLOAD, statusKey = "downloading", engineRunning = false, captchaVisible = false)

            val taken = historyStore.all().map { it.fileName }.toSet()
            val base = (lastUpstreamFileName.ifBlank { "Scribd_${analyzed.docId}" }).removeSuffix(".pdf")
            val fileName = FilenameResolver.uniquify("$base.pdf", taken)
            val entryId = System.currentTimeMillis()
            val downloadId = withContext(Dispatchers.IO) {
                downloader.enqueue(upstreamUrl, fileName, entryTitle())
            }

            activeDownloadId = downloadId
            historyStore.append(
                HistoryEntry(
                    id = entryId,
                    docId = analyzed.docId,
                    title = entryTitle(),
                    fileName = fileName,
                    downloadId = downloadId,
                    completed = false,
                    sizeBytes = 0L,
                    addedAt = entryId,
                ),
            )
            refreshLibrary()
            pollLoop()
        }
    }

    private fun entryTitle(): String =
        lastAnalyzed?.let { a -> lastUpstreamFileName.ifBlank { "Scribd_${a.docId}" } } ?: "Scribd"

    private fun pollLoop() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (activeDownloadId > 0) {
                val p: DlProgress? = withContext(Dispatchers.IO) { downloader.progress(activeDownloadId) }
                when {
                    p == null -> {
                        showError("error_network")
                        return@launch
                    }
                    p.status == STATUS_SUCCESSFUL -> {
                        finishDownload(p.total)
                        return@launch
                    }
                    p.status == STATUS_FAILED -> {
                        showError("error_network")
                        return@launch
                    }
                    else -> _state.value = _state.value.copy(progressBytes = p.bytes, progressTotal = p.total)
                }
                delay(500L)
            }
        }
    }

    private fun finishDownload(total: Long) {
        val old = _state.value.lastSavedEntry
        maybeMarkCompleted(activeDownloadId, total)
        _state.value = _state.value.copy(
            phase = Phase.SAVED,
            progressTotal = total,
            lastSavedEntry = storeEntryFor(activeDownloadId),
        )
        refreshLibrary()
        if (old != null && old.downloadId != activeDownloadId) refreshLibrary()
    }

    private fun maybeMarkCompleted(downloadId: Long, total: Long) {
        if (downloadId <= 0) return
        viewModelScope.launch(Dispatchers.IO) { historyStore.markCompleted(downloadId, total.takeIf { it > 0 }) }
    }

    private fun storeEntryFor(downloadId: Long): HistoryEntry? {
        refreshLibrary()
        return _library.value.firstOrNull { it.downloadId == downloadId }
    }

    fun refreshLibrary() {
        _library.value = historyStore.all().sortedByDescending { it.addedAt }
    }

    private fun showError(key: String) {
        stopEngine()
        _state.value = _state.value.copy(
            phase = Phase.ERROR,
            engineRunning = false,
            captchaVisible = false,
            errorKey = key,
            retryable = key != "error_invalid_url",
        )
        refreshLibrary()
    }

    private fun stopEngine() {
        engine?.cancel()
    }

    private companion object {
        const val STATUS_SUCCESSFUL = 8   // DownloadManager.STATUS_SUCCESSFUL
        const val STATUS_FAILED = 16      // DownloadManager.STATUS_FAILED
    }
}
