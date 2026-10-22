package com.stever.scribdownloader.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.webkit.CookieManager
import java.net.HttpURLConnection
import java.net.URL

enum class PdfEligibility { OK, BAD_TYPE, TOO_SMALL }

/** Result of a HEAD request on the upstream PDF URL. */
data class HeadResult(val mime: String?, val contentLength: Long)

/**
 * Mirrors background.js/validate_download : HEAD, content-type ∈ {pdf,
 * octet, force-download}, size > 2048 (unknown size trusted), executed natively
 * instead of in a service worker (design doc §5).
 */
object PdfValidator {

    fun evaluate(mime: String?, contentLength: Long?): PdfEligibility {
        val type = mime?.lowercase()
            ?: return PdfEligibility.BAD_TYPE              // extension: no type → invalid
        val validType = type.contains("pdf") || type.contains("octet") || type.contains("force-download")
        if (!validType) return PdfEligibility.BAD_TYPE
        val size = contentLength ?: return PdfEligibility.OK // no content-length → trust the type
        val realSize = if (size < 0) null else size           // -1 = unknown (HttpURLConnection)
        return if (realSize == null || realSize > 2048) PdfEligibility.OK else PdfEligibility.TOO_SMALL
    }

    suspend fun head(pdfUrl: String): HeadResult? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(pdfUrl).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "HEAD"
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 8_000
                conn.readTimeout = 8_000
                conn.setRequestProperty("User-Agent", UA)
                // Same as the browser fetch: ride the WebView cookie jar.
                CookieManager.getInstance().getCookie(pdfUrl)?.let { conn.setRequestProperty("Cookie", it) }
                val code = conn.responseCode
                if (code !in 200..399) return@runCatching null
                val mime = conn.contentType
                val length = conn.contentLengthLong
                HeadResult(
                    mime = mime,
                    // -1 (unknown) maps to null: the eligibility rule trusts the type.
                    contentLength = if (length >= 0) length else -1L,
                )
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private const val UA = "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
}
