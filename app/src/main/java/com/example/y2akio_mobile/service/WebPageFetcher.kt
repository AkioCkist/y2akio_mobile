package com.example.y2akio_mobile.service

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import kotlin.coroutines.resume

/**
 * Loads a page in an off-screen WebView (real Chromium: browser TLS fingerprint, runs the site's
 * JS bot challenge) and returns the text of the first `<script id=…>` of [scriptIds] that appears. Used when a WAF —
 * TikTok's — answers OkHttp with its challenge page instead of the content.
 */
class WebPageFetcher(private val context: Context) {

    /**
     * @param cookies `name=value` pairs to plant for [cookieUrl] first (logged-in TikTok session).
     * @return the script's text, or null on timeout / when the element never shows up.
     */
    suspend fun fetchScriptText(
        url: String,
        scriptIds: List<String>,
        cookieUrl: String,
        cookies: List<String> = emptyList(),
        timeoutMs: Long = 25_000,
    ): String? = withTimeoutOrNull(timeoutMs) {
        withContext(Dispatchers.Main) { load(url, scriptIds, cookieUrl, cookies) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun load(url: String, scriptIds: List<String>, cookieUrl: String, cookies: List<String>): String? =
        suspendCancellableCoroutine { cont ->
            val handler = Handler(Looper.getMainLooper())
            val cookieManager = CookieManager.getInstance()
            cookies.forEach { cookieManager.setCookie(cookieUrl, it) }

            val web = WebView(context.applicationContext)
            web.settings.javaScriptEnabled = true
            web.settings.domStorageEnabled = true
            web.settings.blockNetworkImage = true
            web.settings.loadsImagesAutomatically = false
            web.webViewClient = WebViewClient()

            var finished = false
            fun finish(result: String?) {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                web.stopLoading()
                web.destroy()
                if (cont.isActive) cont.resume(result)
            }

            // The challenge page reloads itself into the real page; poll the DOM until the data
            // script exists (bounded by the caller's timeout).
            val lookups = scriptIds.joinToString("||") { "document.getElementById(${JSONArray(listOf(it)).toString().removeSurrounding("[", "]")})" }
            val js = "(function(){var e=$lookups;return e?e.textContent:null;})()"
            val poll = object : Runnable {
                override fun run() {
                    if (finished) return
                    web.evaluateJavascript(js) { raw ->
                        val text = decodeJsString(raw)
                        if (!text.isNullOrBlank()) finish(text) else handler.postDelayed(this, 700)
                    }
                }
            }
            cont.invokeOnCancellation { handler.post { finish(null) } }
            web.loadUrl(url)
            handler.postDelayed(poll, 1500)
        }

    /** evaluateJavascript hands back a JSON literal: "null" or a quoted, escaped string. */
    private fun decodeJsString(raw: String?): String? {
        if (raw == null || raw == "null") return null
        return runCatching { JSONArray("[$raw]").getString(0) }.getOrNull()
    }
}
