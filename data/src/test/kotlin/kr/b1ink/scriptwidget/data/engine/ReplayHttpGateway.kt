package kr.b1ink.scriptwidget.data.engine

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.Base64

/** One request and what came back for it: a response, raw bytes, or a network error. */
@Serializable
data class HttpExchange(
    val key: String,
    val response: HttpResponseSpec? = null,
    val bytesBase64: String? = null,
    val error: String? = null,
)

/** The network traffic of one script run, and when it was recorded (scripts run at that time on replay). */
@Serializable
data class HttpRecording(val recordedAt: Long, val exchanges: List<HttpExchange>)

/**
 * Harness-only: records what a script fetched from the real network, so later runs can replay
 * the same responses offline and give the same widget, run after run.
 */
class RecordingHttpGateway(private val delegate: HttpGateway, private val recordedAt: Long = System.currentTimeMillis()) : HttpGateway {
    private val exchanges = mutableListOf<HttpExchange>()

    override suspend fun loadString(spec: HttpRequestSpec): HttpResponseSpec {
        val response = try {
            delegate.loadString(spec)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Also bad URLs (IllegalArgumentException), so a script's placeholder URL replays as the same failure.
            synchronized(exchanges) { exchanges += HttpExchange(spec.key(), error = e.message ?: e.toString()) }
            throw e
        }
        synchronized(exchanges) { exchanges += HttpExchange(spec.key(), response = response) }
        return response
    }

    override suspend fun loadBytes(spec: HttpRequestSpec): ByteArray {
        val bytes = try {
            delegate.loadBytes(spec)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Also bad URLs (IllegalArgumentException), so a script's placeholder URL replays as the same failure.
            synchronized(exchanges) { exchanges += HttpExchange(spec.key(), error = e.message ?: e.toString()) }
            throw e
        }
        synchronized(exchanges) { exchanges += HttpExchange(spec.key(), bytesBase64 = Base64.getEncoder().encodeToString(bytes)) }
        return bytes
    }

    fun save(file: File) {
        file.parentFile.mkdirs()
        file.writeText(json.encodeToString(HttpRecording.serializer(), HttpRecording(recordedAt, synchronized(exchanges) { exchanges.toList() })))
    }

    companion object {
        private val json = Json { prettyPrint = true }
    }
}

/**
 * Serves a [HttpRecording]. Repeated requests get the recorded responses in order (the last one
 * once they run out); a request the script didn't make while recording gets a 404, or a network
 * error for bytes, so a changed script fails visibly instead of hanging.
 */
class ReplayHttpGateway(recording: HttpRecording) : HttpGateway {
    private val queues = recording.exchanges.groupBy { it.key }.mapValues { ArrayDeque(it.value) }
    private val byPath = recording.exchanges.groupBy { withoutQuery(it.key) }.mapValues { ArrayDeque(it.value) }
    val misses = mutableListOf<String>()

    private fun next(spec: HttpRequestSpec): HttpExchange? = synchronized(queues) {
        val key = spec.key()
        val queue = queues[key] ?: byPath[withoutQuery(key)]
        if (queue == null) {
            misses += key
            return null
        }
        if (queue.size > 1) queue.removeFirst() else queue.first()
    }

    override suspend fun loadString(spec: HttpRequestSpec): HttpResponseSpec {
        val exchange = next(spec) ?: return HttpResponseSpec(spec.url, 404, emptyMap(), "text/plain", "not recorded")
        exchange.error?.let { throw IOException(it) }
        return exchange.response ?: HttpResponseSpec(spec.url, 200, emptyMap(), null, String(Base64.getDecoder().decode(exchange.bytesBase64.orEmpty())))
    }

    override suspend fun loadBytes(spec: HttpRequestSpec): ByteArray {
        val exchange = next(spec) ?: throw IOException("not recorded: ${spec.url}")
        exchange.error?.let { throw IOException(it) }
        return exchange.bytesBase64?.let { Base64.getDecoder().decode(it) } ?: exchange.response!!.body.toByteArray()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(file: File): HttpRecording = json.decodeFromString(HttpRecording.serializer(), file.readText())
    }
}

/** Method, URL and body identify a request; headers don't (they often carry tokens or dates). */
private fun HttpRequestSpec.key(): String = "$method $url" + (body?.let { " #${it.hashCode()}" } ?: "")

/** Fallback for URLs that carry the time or a nonce in the query (`?t=1730000000`). */
private fun withoutQuery(key: String): String = key.replace(Regex("""\?\S*"""), "")
