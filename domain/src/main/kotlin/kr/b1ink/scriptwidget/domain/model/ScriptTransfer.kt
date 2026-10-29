package kr.b1ink.scriptwidget.domain.model

/** A script file outside the app: its file name (as the user sees it) and text. */
data class ScriptFile(val fileName: String, val content: String)

sealed interface ImportOutcome {
    data class Imported(val name: String) : ImportOutcome

    /** [source] is the file name, or the URI when the file couldn't be opened. */
    data class Rejected(val source: String, val reason: Reason) : ImportOutcome

    enum class Reason { NotAScript, Unreadable }
}
