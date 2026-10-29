package kr.b1ink.scriptwidget.data.engine

import kotlinx.serialization.Serializable

/** `Pasteboard` (strings). Reading may return null when the app isn't in the foreground (Android 10+). */
interface Clipboard {
    fun copy(text: String)
    fun paste(): String?
}

/** `ShareSheet.present()`: plain [texts] plus [files] (absolute paths of cached images / sandbox files). */
@Serializable
data class ShareSpec(val texts: List<String> = emptyList(), val files: List<String> = emptyList())

@Serializable
data class MailSpec(
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    val html: Boolean = false,
    /** Absolute paths of the attached files. */
    val attachments: List<String> = emptyList(),
)

@Serializable
data class MessageSpec(
    val recipients: List<String> = emptyList(),
    val body: String = "",
    val attachments: List<String> = emptyList(),
)

/** Hands content to other apps; the user confirms there (share sheet, mail and SMS composers). */
interface Composer {
    fun share(spec: ShareSpec)
    fun mail(spec: MailSpec)
    fun message(spec: MessageSpec)
}

/** How an x-callback-url call came back: [status] is "success", "error" or "cancel". */
@Serializable
data class CallbackResult(val status: String, val params: Map<String, String> = emptyMap())

/** Opens an x-callback-url and waits for the other app to call back `scriptwidget://x-callback-url/…`. */
interface CallbackUrlBroker {
    suspend fun open(url: String, requestId: String): CallbackResult
}

/** `Speech.speak()`. */
interface Speaker {
    suspend fun speak(text: String)
}

object NoClipboard : Clipboard {
    override fun copy(text: String) = Unit
    override fun paste(): String? = null
}

object NoComposer : Composer {
    private fun unavailable(): Nothing = throw IllegalStateException("Sharing is not available")
    override fun share(spec: ShareSpec) = unavailable()
    override fun mail(spec: MailSpec) = unavailable()
    override fun message(spec: MessageSpec) = unavailable()
}

object NoCallbacks : CallbackUrlBroker {
    override suspend fun open(url: String, requestId: String): CallbackResult =
        throw IllegalStateException("x-callback-url is not available")
}

object NoSpeaker : Speaker {
    override suspend fun speak(text: String) = throw IllegalStateException("Speech is not available")
}
