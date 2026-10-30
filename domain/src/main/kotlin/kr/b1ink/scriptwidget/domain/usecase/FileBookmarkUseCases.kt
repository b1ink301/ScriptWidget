package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.BookmarkName
import kr.b1ink.scriptwidget.domain.model.BookmarkResult
import kr.b1ink.scriptwidget.domain.repository.FileBookmarkRepository
import kr.b1ink.scriptwidget.domain.repository.ScriptDocuments
import kotlin.coroutines.cancellation.CancellationException

/** Copies a file the user picked (by URI) into the bookmark [name]. */
class AddFileBookmarkUseCase(
    private val bookmarks: FileBookmarkRepository,
    private val documents: ScriptDocuments,
) {
    suspend operator fun invoke(name: String, uri: String): BookmarkResult {
        val trimmed = name.trim()
        BookmarkName.problem(trimmed)?.let { return BookmarkResult.Invalid(it) }
        val bytes = try {
            documents.readBytes(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return BookmarkResult.Failed(e.message ?: e.toString())
        }
        bookmarks.save(trimmed, bytes)
        return BookmarkResult.Saved(trimmed)
    }
}

class RemoveFileBookmarkUseCase(private val bookmarks: FileBookmarkRepository) {
    suspend operator fun invoke(name: String) = bookmarks.remove(name)
}
