package kr.b1ink.scriptwidget.domain.model

/**
 * A named file the user picked for scripts, like Scriptable's file bookmarks: scripts find it with
 * `FileManager.bookmarkedPath(name)`.
 */
data class FileBookmark(val name: String, val sizeBytes: Long)

sealed interface BookmarkResult {
    data class Saved(val name: String) : BookmarkResult
    data class Invalid(val reason: BookmarkName.Problem) : BookmarkResult
    data class Failed(val message: String) : BookmarkResult
}

object BookmarkName {
    enum class Problem { Blank, NotAFileName }

    /** Why [name] can't be used as a bookmark name, or null when it can. */
    fun problem(name: String): Problem? = when {
        name.isBlank() -> Problem.Blank
        name == "." || name == ".." || name.any { it == '/' || it == '\\' } -> Problem.NotAFileName
        else -> null
    }
}
