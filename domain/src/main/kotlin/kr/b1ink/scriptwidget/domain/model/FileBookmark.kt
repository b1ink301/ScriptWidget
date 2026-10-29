package kr.b1ink.scriptwidget.domain.model

/**
 * A named file the user picked for scripts, like Scriptable's file bookmarks: scripts find it with
 * `FileManager.bookmarkedPath(name)`.
 */
data class FileBookmark(val name: String, val sizeBytes: Long)

sealed interface BookmarkResult {
    data class Saved(val name: String) : BookmarkResult
    data class Invalid(val reason: String) : BookmarkResult
    data class Failed(val message: String) : BookmarkResult
}

object BookmarkName {
    /** The reason [name] can't be used as a bookmark name, or null when it can. */
    fun problem(name: String): String? = when {
        name.isBlank() -> "이름을 입력하세요"
        name == "." || name == ".." || name.any { it == '/' || it == '\\' } -> "이름에 / \\ 를 쓸 수 없습니다"
        else -> null
    }
}
