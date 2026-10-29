package kr.b1ink.scriptwidget.deeplink

import kotlinx.coroutines.CompletableDeferred
import kr.b1ink.scriptwidget.data.engine.CallbackResult
import kr.b1ink.scriptwidget.data.engine.CallbackUrlBroker
import kr.b1ink.scriptwidget.data.engine.UrlOpener
import java.util.concurrent.ConcurrentHashMap

/** Matches `scriptwidget://x-callback-url/…` replies (delivered to MainActivity) with pending requests. */
class AppCallbackBroker(private val urls: UrlOpener) : CallbackUrlBroker {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<CallbackResult>>()

    override suspend fun open(url: String, requestId: String): CallbackResult {
        val reply = CompletableDeferred<CallbackResult>()
        pending[requestId] = reply
        try {
            urls.open(url)
            return reply.await()
        } finally {
            pending.remove(requestId)
        }
    }

    /** @return false when no request is waiting for this reply. */
    fun complete(link: DeepLink.Callback): Boolean =
        pending[link.requestId]?.complete(CallbackResult(link.status, link.params)) ?: false
}
