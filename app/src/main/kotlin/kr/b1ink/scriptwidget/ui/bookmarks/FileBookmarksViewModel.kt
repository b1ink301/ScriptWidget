package kr.b1ink.scriptwidget.ui.bookmarks

import kr.b1ink.scriptwidget.ui.text.UiText
import kr.b1ink.scriptwidget.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.BookmarkResult
import kr.b1ink.scriptwidget.domain.repository.FileBookmarkRepository
import kr.b1ink.scriptwidget.domain.repository.ScriptDocuments
import kr.b1ink.scriptwidget.domain.usecase.AddFileBookmarkUseCase
import kr.b1ink.scriptwidget.domain.usecase.RemoveFileBookmarkUseCase

data class BookmarkRow(val name: String, val size: String)

data class FileBookmarksUiState(val rows: List<BookmarkRow> = emptyList(), val message: UiText? = null)

class FileBookmarksViewModel(bookmarks: FileBookmarkRepository, documents: ScriptDocuments) : ViewModel() {
    private val addBookmark = AddFileBookmarkUseCase(bookmarks, documents)
    private val removeBookmark = RemoveFileBookmarkUseCase(bookmarks)
    private val message = MutableStateFlow<UiText?>(null)

    val uiState: StateFlow<FileBookmarksUiState> =
        combine(bookmarks.bookmarks, message) { all, msg ->
            FileBookmarksUiState(all.map { BookmarkRow(it.name, sizeLabel(it.sizeBytes)) }, msg)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, FileBookmarksUiState())

    /** Saves the picked file ([uri]) as bookmark [name]; an existing bookmark gets the new file. */
    fun add(name: String, uri: String) {
        viewModelScope.launch {
            message.value = when (val result = addBookmark(name, uri)) {
                is BookmarkResult.Saved -> UiText.res(R.string.bookmarks_saved, result.name)
                is BookmarkResult.Invalid -> when (result.reason) {
                    kr.b1ink.scriptwidget.domain.model.BookmarkName.Problem.Blank -> UiText.res(R.string.bookmarks_name_blank)
                    kr.b1ink.scriptwidget.domain.model.BookmarkName.Problem.NotAFileName -> UiText.res(R.string.bookmarks_name_not_file_name)
                }
                is BookmarkResult.Failed -> UiText.res(R.string.bookmarks_read_failed, result.message)
            }
        }
    }

    fun remove(name: String) {
        viewModelScope.launch { removeBookmark(name) }
    }

    fun messageShown() {
        message.value = null
    }

    private fun sizeLabel(bytes: Long) = when {
        bytes < 1_024 -> "$bytes B"
        bytes < 1_024 * 1_024 -> "${bytes / 1_024} KB"
        else -> "%.1f MB".format(bytes / (1_024.0 * 1_024))
    }
}
