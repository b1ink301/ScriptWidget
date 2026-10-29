package kr.b1ink.scriptwidget.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.b1ink.scriptwidget.domain.model.FileBookmark

/** File bookmarks by name, sorted by name. */
interface FileBookmarkRepository {
    val bookmarks: Flow<List<FileBookmark>>

    /** Adds the bookmark, or replaces the file of an existing one. */
    suspend fun save(name: String, bytes: ByteArray)

    suspend fun remove(name: String)
}
