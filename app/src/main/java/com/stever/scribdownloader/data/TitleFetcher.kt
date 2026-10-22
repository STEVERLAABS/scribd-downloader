package com.stever.scribdownloader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the document page HTML only to derive a nicer file name
 * ("Clean Filename" of the extension). Fails silently: the download itself
 * never depends on it (fallback name = Scribd_<docId>).
 */
class TitleFetcher {

    suspend fun fetch(docUrl: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(docUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 6_000
                conn.readTimeout = 6_000
                conn.instanceFollowRedirects = true
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", UA)
                if (conn.responseCode !in 200..399) return@runCatching null
                val stream = conn.inputStream
                requireNotNull(stream)
                stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    val sb = StringBuilder()
                    for (line in lines) {
                        sb.append(line).append('\n')
                        if (sb.length > MAX_HTML) break
                    }
                    sb.toString()
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private companion object {
        const val MAX_HTML = 300_000
        const val UA = "Mozilla/5.0 (Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    }
}
