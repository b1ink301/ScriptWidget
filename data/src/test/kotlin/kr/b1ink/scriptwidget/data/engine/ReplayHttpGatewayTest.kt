package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ReplayHttpGatewayTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val live = FakeHttpGateway().apply {
        var calls = 0
        responses = { HttpResponseSpec(it.url, 200, mapOf("Content-Type" to "application/json"), "application/json", "{\"n\":${++calls}}") }
        bytes = { if ("broken" in it.url) throw IOException("offline") else byteArrayOf(7, 8, 9) }
    }

    private suspend fun recordAndReload(block: suspend (HttpGateway) -> Unit): ReplayHttpGateway {
        val recorder = RecordingHttpGateway(live, recordedAt = 1_700_000_000_000)
        block(recorder)
        val file = File(tmp.root, "rec.json")
        recorder.save(file)
        return ReplayHttpGateway(ReplayHttpGateway.load(file))
    }

    @Test
    fun `replays responses, bytes and errors as recorded`() = runTest {
        val replay = recordAndReload {
            it.loadString(HttpRequestSpec("https://api.test/a"))
            it.loadBytes(HttpRequestSpec("https://img.test/p.png"))
            runCatching { it.loadBytes(HttpRequestSpec("https://img.test/broken.png")) }
        }

        assertEquals("{\"n\":1}", replay.loadString(HttpRequestSpec("https://api.test/a")).body)
        assertContentEquals(byteArrayOf(7, 8, 9), replay.loadBytes(HttpRequestSpec("https://img.test/p.png")))
        assertEquals("offline", assertFailsWith<IOException> { replay.loadBytes(HttpRequestSpec("https://img.test/broken.png")) }.message)
    }

    @Test
    fun `repeated requests get their responses in order, then the last one`() = runTest {
        val replay = recordAndReload {
            it.loadString(HttpRequestSpec("https://api.test/a"))
            it.loadString(HttpRequestSpec("https://api.test/a"))
        }
        val spec = HttpRequestSpec("https://api.test/a")

        assertEquals(listOf("{\"n\":1}", "{\"n\":2}", "{\"n\":2}"), List(3) { replay.loadString(spec).body })
    }

    @Test
    fun `a query that changed (like a timestamp) still finds the recording`() = runTest {
        val replay = recordAndReload { it.loadString(HttpRequestSpec("https://api.test/a?t=1")) }

        assertEquals("{\"n\":1}", replay.loadString(HttpRequestSpec("https://api.test/a?t=2")).body)
    }

    @Test
    fun `unrecorded requests are 404s or network errors, and are reported`() = runTest {
        val replay = recordAndReload { }

        assertEquals(404, replay.loadString(HttpRequestSpec("https://api.test/new")).statusCode)
        assertFailsWith<IOException> { replay.loadBytes(HttpRequestSpec("https://img.test/new.png")) }
        assertEquals(listOf("GET https://api.test/new", "GET https://img.test/new.png"), replay.misses)
    }

    @Test
    fun `requests with different bodies are told apart`() = runTest {
        val replay = recordAndReload {
            it.loadString(HttpRequestSpec("https://api.test/q", method = "POST", body = "a"))
            it.loadString(HttpRequestSpec("https://api.test/q", method = "POST", body = "b"))
        }

        assertEquals("{\"n\":2}", replay.loadString(HttpRequestSpec("https://api.test/q", method = "POST", body = "b")).body)
        assertEquals("{\"n\":1}", replay.loadString(HttpRequestSpec("https://api.test/q", method = "POST", body = "a")).body)
    }
}
