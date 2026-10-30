package kr.b1ink.scriptwidget.domain.model

/**
 * Problems the app itself reports on a widget (not script errors). They travel as codes in
 * [RenderState.error] and are put into words, in the user's language, where the widget is drawn.
 */
sealed interface WidgetError {
    /** The widget has no script yet. */
    data object NotBound : WidgetError

    data class ScriptNotFound(val name: String) : WidgetError

    companion object {
        private const val PREFIX = "@scriptwidget:"

        fun encode(error: WidgetError): String = PREFIX + when (error) {
            NotBound -> "not-bound"
            is ScriptNotFound -> "script-not-found:${error.name}"
        }

        /** The app error in [error], or null when it is a script's own error message. */
        fun decode(error: String?): WidgetError? {
            val code = error?.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX) ?: return null
            return when {
                code == "not-bound" -> NotBound
                code.startsWith("script-not-found:") -> ScriptNotFound(code.removePrefix("script-not-found:"))
                else -> null
            }
        }
    }
}
