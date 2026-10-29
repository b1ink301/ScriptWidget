package kr.b1ink.scriptwidget.deeplink

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.CallbackResult
import kr.b1ink.scriptwidget.data.engine.UrlOpener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppCallbackBrokerTest {
    private val opened = mutableListOf<String>()
    private val broker = AppCallbackBroker(UrlOpener { opened += it })

    @Test
    fun `opens the url and waits for the matching reply`() = runTest {
        val result = async { broker.open("other://x-callback-url/do", requestId = "R1") }
        testScheduler.runCurrent()
        assertEquals(listOf("other://x-callback-url/do"), opened)

        assertFalse(broker.complete(DeepLink.Callback("success", "OTHER", emptyMap())))
        broker.complete(DeepLink.Callback("success", "R1", mapOf("a" to "1")))

        assertEquals(CallbackResult("success", mapOf("a" to "1")), result.await())
    }
}
