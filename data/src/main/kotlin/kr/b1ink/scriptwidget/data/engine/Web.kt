package kr.b1ink.scriptwidget.data.engine

import kotlinx.serialization.Serializable

/** A web page backing `WebView`. Usable headless (also during widget refreshes). */
interface WebPage {
    suspend fun loadUrl(url: String)
    suspend fun loadHtml(html: String, baseUrl: String?)

    /**
     * Runs [script] and returns its result as JSON. With [useCallback] the script must call
     * `completion(value)` (it may be asynchronous).
     */
    suspend fun evaluate(script: String, useCallback: Boolean): String
    suspend fun html(): String
    fun close()
}

fun interface WebPageFactory {
    fun create(): WebPage
}

/** A system document picker request: "open" existing documents or "create" one to export to. */
@Serializable
data class DocumentRequest(
    val kind: String,
    val mimeTypes: List<String>,
    val multiple: Boolean = false,
    val suggestedName: String? = null,
)

/** Access to documents chosen with the system picker, identified by opaque handles (content URIs). */
interface DocumentStore {
    /** Copies the document into the script sandbox and returns its path. */
    fun importToSandbox(handle: String): String
    fun write(handle: String, bytes: ByteArray)
}

object NoWebPages : WebPageFactory {
    override fun create(): WebPage = throw IllegalStateException("WebView is not available")
}

object NoDocuments : DocumentStore {
    override fun importToSandbox(handle: String): String = throw IllegalStateException("Documents are not available")
    override fun write(handle: String, bytes: ByteArray) = throw IllegalStateException("Documents are not available")
}
