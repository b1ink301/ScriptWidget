package kr.b1ink.scriptwidget.domain.repository

import kotlinx.coroutines.flow.Flow

/** Font files scripts can use by name (`new Font("Name", 17)`): Documents/fonts/<Name>.ttf|.otf. */
interface FontRepository {
    /** Font names (file names without the extension), sorted. */
    val fonts: Flow<List<String>>

    /** Adds the font file, or replaces one with the same [fileName]. */
    suspend fun save(fileName: String, bytes: ByteArray)
}
