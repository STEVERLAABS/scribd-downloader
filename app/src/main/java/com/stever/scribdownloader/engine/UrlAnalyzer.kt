package com.stever.scribdownloader.engine

import java.net.URI

enum class UrlKind { DOCUMENT, READ, BOOK, AUDIOBOOK, EMBEDS }

/**
 * Analysis product: the document id, the URL pasted into the external service
 * (verbatim, like the extension's cachedUrl — except /embeds/ which are
 * normalized, plan Task 4), and the url kind.
 */
data class AnalyzedUrl(val docId: Long, val pasteUrl: String, val kind: UrlKind)

/**
 * Port of content.js/Utils : same id patterns, host trust rules and filename
 * sanitization — extended with the app's paste-URL contract (design doc).
 */
object UrlAnalyzer {
    // (?:doc|document|embeds|read|book|audiobook)/(\d+) — mirrors Utils.getDocumentId.
    private val ID_PATH = Regex("""(?:doc|document|embeds|read|book|audiobook)/(\d+)""")
    private val ALLOWED_CHARS = Regex("""[^a-zA-Z0-9\s\-_\u00C0-\u00FF]""")
    private val NEWLINES = Regex("""[\r\n]+""")
    private val SPACES = Regex("""\s+""")
    private val SCHEME_HTTPS = "https://"

    fun analyze(raw: String): AnalyzedUrl? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null

        val withScheme = when {
            trimmed.startsWith(SCHEME_HTTPS, ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) ->
                SCHEME_HTTPS + trimmed.removePrefix("http://")
            else -> SCHEME_HTTPS + trimmed
        }
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val trusted = host == "scribd.com" || host.endsWith(".scribd.com")
        if (!trusted) return null

        val path = uri.path ?: ""
        val id = ID_PATH.find(path)?.groupValues?.get(1)?.toLong() ?: return null

        val kind = when {
            path.contains("/embeds/") -> UrlKind.EMBEDS
            path.contains("/read/") -> UrlKind.READ
            path.contains("/book/") -> UrlKind.BOOK
            path.contains("/audiobook/") -> UrlKind.AUDIOBOOK
            else -> UrlKind.DOCUMENT
        }

        val pasteUrl = if (kind == UrlKind.EMBEDS) "${SCHEME_HTTPS}www.scribd.com/doc/$id" else withScheme
        return AnalyzedUrl(id, pasteUrl, kind)
    }

    /** Port of content.js sanitizeFilename (+ 120-char cap and docId fallback). */
    fun sanitizeFileName(name: String, docId: Long): String {
        val cleaned = name
            .replace(NEWLINES, " ")
            .replace(ALLOWED_CHARS, "")
            .trim()
            .replace(SPACES, "_")
        if (cleaned.isEmpty()) return "Scribd_$docId"
        return cleaned.take(120)
    }
}
