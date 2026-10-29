package kr.b1ink.scriptwidget.data.http

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.HttpRequestSpec
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Before
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OkHttpGatewayTest {
    private val server = MockWebServer()
    private val gateway = OkHttpGateway(OkHttpClient())

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() = server.close()

    @Test
    fun `GET returns status, headers, mime type and body`() = runTest {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json; charset=utf-8")
                .addHeader("X-Test", "yes").body("""{"a":1}""").build()
        )

        val response = gateway.loadString(HttpRequestSpec(url = server.url("/data").toString()))

        assertEquals(200, response.statusCode)
        assertEquals("application/json", response.mimeType)
        assertEquals("yes", response.headers["X-Test"])
        assertEquals("""{"a":1}""", response.body)
        assertEquals("GET", server.takeRequest().method)
    }

    @Test
    fun `POST sends method, headers and body`() = runTest {
        server.enqueue(MockResponse.Builder().code(204).build())

        gateway.loadString(
            HttpRequestSpec(
                url = server.url("/p").toString(),
                method = "post",
                headers = mapOf("Content-Type" to "application/json", "X-Key" to "k"),
                body = """{"q":1}""",
            )
        )

        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("k", recorded.headers["X-Key"])
        assertEquals("""{"q":1}""", recorded.body?.utf8())
    }

    @Test
    fun `non-2xx responses are returned, not thrown, like Scriptable`() = runTest {
        server.enqueue(MockResponse.Builder().code(500).body("oops").build())

        val response = gateway.loadString(HttpRequestSpec(url = server.url("/e").toString()))

        assertEquals(500, response.statusCode)
        assertEquals("oops", response.body)
    }

    @Test
    fun `loadBytes returns raw bytes`() = runTest {
        server.enqueue(MockResponse.Builder().body(okio.Buffer().write(byteArrayOf(1, 2, 3))).build())

        assertContentEquals(byteArrayOf(1, 2, 3), gateway.loadBytes(HttpRequestSpec(server.url("/b").toString())))
    }

    @Test
    fun `loadBytes fails on error status`() = runTest {
        server.enqueue(MockResponse.Builder().code(404).build())

        assertFailsWith<IOException> { gateway.loadBytes(HttpRequestSpec(server.url("/b").toString())) }
    }

    @Test
    fun `requests time out`() = runTest {
        server.enqueue(MockResponse.Builder().headersDelay(2, java.util.concurrent.TimeUnit.SECONDS).build())

        assertFailsWith<IOException> {
            gateway.loadString(HttpRequestSpec(url = server.url("/slow").toString(), timeout = 0.3))
        }
    }

    // --- memory & data use ---

    @Test
    fun `responses over the size limit fail instead of filling memory`() = runTest {
        val small = OkHttpGateway(OkHttpClient(), maxResponseBytes = 1_000)
        server.enqueue(MockResponse.Builder().body("x".repeat(2_000)).build())
        server.enqueue(MockResponse.Builder().body("y".repeat(2_000)).build())

        val text = assertFailsWith<IOException> { small.loadString(HttpRequestSpec(url = server.url("/big").toString())) }
        assertFailsWith<IOException> { small.loadBytes(HttpRequestSpec(url = server.url("/big").toString())) }
        kotlin.test.assertContains(text.message.orEmpty(), "too large")
    }

    @Test
    fun `responses within the limit are read whole`() = runTest {
        val small = OkHttpGateway(OkHttpClient(), maxResponseBytes = 1_000)
        server.enqueue(MockResponse.Builder().body("z".repeat(1_000)).build())

        assertEquals(1_000, small.loadString(HttpRequestSpec(url = server.url("/ok").toString())).body.length)
    }

    @Test
    fun `cacheable responses are served from the disk cache`() = runTest {
        val dir = java.nio.file.Files.createTempDirectory("http-cache").toFile()
        val cached = OkHttpGateway(OkHttpGateway.defaultClient(cacheDir = dir))
        server.enqueue(MockResponse.Builder().addHeader("Cache-Control", "max-age=600").body("icon").build())

        repeat(2) { cached.loadBytes(HttpRequestSpec(url = server.url("/icon.png").toString())) }

        assertEquals(1, server.requestCount)
        dir.deleteRecursively()
    }
}
