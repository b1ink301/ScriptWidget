package kr.b1ink.scriptwidget.deeplink

import java.net.URI
import java.net.URLDecoder

/** `scriptwidget://` links produced by `URLScheme` (e.g. a widget's `url = URLScheme.forRunningScript()`). */
sealed interface DeepLink {
    data object Home : DeepLink
    data class Open(val scriptName: String) : DeepLink
    data class Run(val scriptName: String, val queryParameters: Map<String, String>) : DeepLink

    /** Another app answering an x-callback-url opened by `CallbackURL` ([status]: success | error | cancel). */
    data class Callback(val status: String, val requestId: String, val params: Map<String, String>) : DeepLink

    companion object {
        const val SCHEME = "scriptwidget"

        /** iOS Scriptable's scheme: scripts written for it open `scriptable:///run/<name>` and similar links. */
        const val SCRIPTABLE_SCHEME = "scriptable"
        private const val SCRIPT_NAME = "scriptName"
        private const val CALLBACK_HOST = "x-callback-url"
        private const val REQUEST_ID = "requestId"

        fun parse(link: String): DeepLink? {
            // `scriptwidget://` (URLScheme.forOpeningApp) has an empty authority, which java.net.URI rejects.
            if (link.trimEnd('/') == "$SCHEME:" || link.trimEnd('/') == "$SCRIPTABLE_SCHEME:") return Home
            val uri = runCatching { URI(link) }.getOrNull() ?: return null
            if (uri.scheme != SCHEME && uri.scheme != SCRIPTABLE_SCHEME) return null
            // Scriptable style puts the action in the path: scriptable:///run/<script name>.
            val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
            val action = uri.host ?: segments.firstOrNull() ?: return Home
            val pathName = if (uri.host == null) segments.drop(1).joinToString("/").takeIf { it.isNotEmpty() } else null
            val params = uri.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate { pair ->
                val (key, value) = pair.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                decode(key) to decode(value)
            }
            if (action == CALLBACK_HOST) {
                val status = uri.path.orEmpty().trim('/').takeIf { it in setOf("success", "error", "cancel") } ?: return null
                val requestId = params[REQUEST_ID] ?: return null
                return Callback(status, requestId, params - REQUEST_ID)
            }
            val name = params[SCRIPT_NAME]?.takeIf { it.isNotEmpty() } ?: pathName ?: return null
            return when (action) {
                "run" -> Run(name, params - SCRIPT_NAME)
                "open", "settings" -> Open(name)
                else -> null
            }
        }

        // The Charset overload needs API 33; minSdk is 26.
        private fun decode(text: String) = URLDecoder.decode(text, "UTF-8")
    }
}
