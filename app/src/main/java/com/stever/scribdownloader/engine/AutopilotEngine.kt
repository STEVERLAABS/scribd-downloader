package com.stever.scribdownloader.engine

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject

/**
 * Drives the external service inside a visible in-app WebView using the
 * 1:1 port of the extension's autopilot (assets/autopilot.js).
 *
 * Design doc §5: no UA spoofing, top-level navigation restricted to the flow
 * hosts, single typed JS bridge, cancel support.
 */
class AutopilotEngine(
    private val webView: WebView,
    val events: MutableSharedFlow<EngineEvent>,
) {

    inner class JsBridge {
        @JavascriptInterface
        fun post(msg: String) {
            val event = parse(msg) ?: return
            // SharedFlow emissions are thread-safe (JSC binder thread OK).
            events.tryEmit(event)
        }
    }

    private fun parse(msg: String): EngineEvent? = runCatching {
        val o = JSONObject(msg)
        val key = o.optString("key")
        when (o.optString("event")) {
            "status" -> EngineEvent.Status(o.optInt("step", 0), key)
            "captcha" -> EngineEvent.Captcha
            "pdf" -> o.optString("url").takeIf { it.isNotBlank() }?.let { EngineEvent.PdfFound(it) }
            "failed" -> EngineEvent.Failed(key)
            "timeout" -> EngineEvent.TimedOut(key)
            else -> null
        }
    }.getOrNull()

    fun start(pasteUrl: String) {
        prepWebView()
        // Extension behavior: the same document URL is pasted again on every
        // fresh page that shows the form (captcha chains re-run the form phase).
        inspectPasteUrlOnNextPage = pasteUrl
        webView.loadUrl(SERVICE_URL)
    }

    private var inspectPasteUrlOnNextPage: String? = null

    fun cancel() {
        val js = "try{window.__spdCancel=true;}catch(e){} try{window.__spdEngineV1=false;}catch(e){}"
        webView.evaluateJavascript(js, null)
        webView.stopLoading()
        webView.loadUrl("about:blank")
        inspectPasteUrlOnNextPage = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun prepWebView() {
        webView.settings.run {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (!isAllowedHost(url)) {
                    cancel()
                    events.tryEmit(EngineEvent.Failed("error_detected"))
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                val script = runCatching {
                    webView.context.assets.open("autopilot.js").bufferedReader().readText()
                }.getOrNull() ?: return
                // Only seed __spdPaste when present: never overwrite a value
                // with null (later pages must keep it for captcha chains).
                val paste = inspectPasteUrlOnNextPage
                    ?.let { "window.__spdPaste = " + JSONObject.quote(it) + ";\n" }
                    ?: ""
                view.evaluateJavascript(paste + script, null)
            }
        }
    }

    private fun isAllowedHost(uri: Uri): Boolean {
        val host = (uri.host ?: return false).lowercase()
        return host == SERVICE_HOST || host.endsWith(".vdownloaders.com") ||
            host.endsWith(".ilide.info") || host == "ilide.info"
    }

    companion object {
        const val SERVICE_URL = "https://scribd.vdownloaders.com/"
        const val SERVICE_HOST = "scribd.vdownloaders.com"
    }
}
