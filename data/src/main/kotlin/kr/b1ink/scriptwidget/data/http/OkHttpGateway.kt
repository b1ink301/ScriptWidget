package kr.b1ink.scriptwidget.data.http

import kotlinx.coroutines.suspendCancellableCoroutine
import kr.b1ink.scriptwidget.data.engine.HttpGateway
import kr.b1ink.scriptwidget.data.engine.HttpRequestSpec
import kr.b1ink.scriptwidget.data.engine.HttpResponseSpec
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [HttpGateway] backed by OkHttp. Bodies larger than [maxResponseBytes] fail rather than filling
 * the memory of the script's process.
 */
class OkHttpGateway(
    private val client: OkHttpClient = defaultClient(),
    private val maxResponseBytes: Long = MAX_RESPONSE_BYTES,
) : HttpGateway {
    private val methodsWithBody = setOf("POST", "PUT", "PATCH", "PROPPATCH", "REPORT")

    override suspend fun loadString(spec: HttpRequestSpec): HttpResponseSpec = execute(spec) { response ->
        HttpResponseSpec(
            url = response.request.url.toString(),
            statusCode = response.code,
            headers = response.headers.toMap(),
            mimeType = response.body.contentType()?.let { "${it.type}/${it.subtype}" },
            body = response.limitedBytes().toString(response.body.contentType()?.charset() ?: Charsets.UTF_8),
        )
    }

    override suspend fun loadBytes(spec: HttpRequestSpec): ByteArray = execute(spec) { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code} for ${spec.url}")
        response.limitedBytes()
    }

    /** Reads at most [maxResponseBytes] (+1 to detect more), whatever Content-Length claims. */
    private fun Response.limitedBytes(): ByteArray {
        val source = body.source()
        source.request(maxResponseBytes + 1)
        if (source.buffer.size > maxResponseBytes) {
            throw IOException("Response too large (over ${maxResponseBytes / 1_000_000} MB): ${request.url}")
        }
        return source.buffer.readByteArray()
    }

    private suspend fun <T> execute(spec: HttpRequestSpec, read: (Response) -> T): T {
        val method = spec.method.uppercase()
        val contentType = spec.headers.entries
            .firstOrNull { it.key.equals("Content-Type", ignoreCase = true) }?.value
            ?.toMediaTypeOrNull()
        val body = when {
            spec.body != null -> spec.body.toRequestBody(contentType)
            method in methodsWithBody -> ByteArray(0).toRequestBody(contentType)
            else -> null
        }
        val request = Request.Builder()
            .url(spec.url)
            .method(method, body)
            .apply { spec.headers.forEach { (k, v) -> header(k, v) } }
            .build()

        val call = client.newBuilder()
            .callTimeout((spec.timeout * 1000).toLong(), TimeUnit.MILLISECONDS)
            .build()
            .newCall(request)

        return suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use(read) }
                    result.fold(cont::resume, cont::resumeWithException)
                }
            })
        }
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> =
        names().associateWith { values(it).joinToString(", ") }

    companion object {
        const val MAX_RESPONSE_BYTES = 10L * 1_000_000
        private const val CACHE_BYTES = 10L * 1024 * 1024

        /** The app's gateway: responses are cached in [cacheDir] as their headers allow. */
        fun cached(cacheDir: java.io.File): OkHttpGateway = OkHttpGateway(defaultClient(cacheDir))

        /** With [cacheDir], responses are cached as their HTTP headers allow (e.g. icons, static JSON). */
        fun defaultClient(cacheDir: java.io.File? = null): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .apply { cacheDir?.let { cache(okhttp3.Cache(it, CACHE_BYTES)) } }
            .build()
    }
}
