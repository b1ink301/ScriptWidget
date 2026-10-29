package kr.b1ink.scriptwidget.data.android

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.data.engine.WebPage
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** [WebPage] backed by an Android [WebView]; works without being on screen (widget scraping). */
class AndroidWebPage(
    context: Context,
    private val timeoutMillis: Long = 30_000,
) : WebPage {
    private val context = context.applicationContext
    private var webView: WebView? = null
    private var pageLoad: CompletableDeferred<Unit>? = null
    private val callbacks = ConcurrentHashMap<Int, CompletableDeferred<String>>()
    private val nextCallback = AtomicInteger(1)

    /** Receives `completion(value)` calls from page JavaScript. */
    private inner class Bridge {
        @JavascriptInterface
        fun complete(id: Int, json: String?) {
            callbacks.remove(id)?.complete(json ?: "null")
        }
    }

    /** The underlying view, created on the main thread (also used to present the page). */
    @SuppressLint("SetJavaScriptEnabled")
    suspend fun view(): WebView = withContext(Dispatchers.Main.immediate) {
        webView ?: WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    pageLoad?.complete(Unit)
                }
            }
            addJavascriptInterface(Bridge(), BRIDGE)
        }.also { webView = it }
    }

    override suspend fun loadUrl(url: String) = load { it.loadUrl(url) }

    override suspend fun loadHtml(html: String, baseUrl: String?) =
        load { it.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null) }

    private suspend fun load(start: (WebView) -> Unit) {
        val loaded = CompletableDeferred<Unit>()
        val view = view()
        withContext(Dispatchers.Main.immediate) {
            pageLoad = loaded
            start(view)
        }
        withTimeout(timeoutMillis) { loaded.await() }
    }

    override suspend fun evaluate(script: String, useCallback: Boolean): String {
        if (!useCallback) return evaluateNow(script)
        val id = nextCallback.getAndIncrement()
        val result = CompletableDeferred<String>()
        callbacks[id] = result
        evaluateNow(
            """
            (function () {
              const completion = (value) => $BRIDGE.complete($id, JSON.stringify(value === undefined ? null : value));
              $script
            })();
            """.trimIndent()
        )
        return try {
            withTimeout(timeoutMillis) { result.await() }
        } finally {
            callbacks.remove(id)
        }
    }

    private suspend fun evaluateNow(script: String): String {
        val view = view()
        return withContext(Dispatchers.Main.immediate) {
            suspendCancellableCoroutine { cont ->
                view.evaluateJavascript(script) { value -> if (cont.isActive) cont.resume(value ?: "null") }
            }
        }
    }

    override suspend fun html(): String =
        Json.decodeFromString<String?>(evaluateNow("document.documentElement.outerHTML")).orEmpty()

    override fun close() {
        val view = webView ?: return
        webView = null
        view.post {
            view.removeJavascriptInterface(BRIDGE)
            view.destroy()
        }
    }

    private companion object {
        const val BRIDGE = "__scriptwidget"
    }
}
