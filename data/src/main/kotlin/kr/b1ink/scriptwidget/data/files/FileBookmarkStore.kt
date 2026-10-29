package kr.b1ink.scriptwidget.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kr.b1ink.scriptwidget.domain.model.FileBookmark
import kr.b1ink.scriptwidget.domain.repository.FileBookmarkRepository
import java.io.File

/**
 * Each bookmark is a copy of the picked file in [dir], named after the bookmark. [dir] is the
 * sandbox's `Bookmarks` folder, so scripts read it with the normal FileManager calls.
 */
class FileBookmarkStore(private val dir: File) : FileBookmarkRepository {
    private val changes = MutableStateFlow(0)

    override val bookmarks: Flow<List<FileBookmark>> = changes
        .map { dir.listFiles().orEmpty().filter { it.isFile }.sortedBy { it.name }.map { FileBookmark(it.name, it.length()) } }
        .flowOn(Dispatchers.IO)

    override suspend fun save(name: String, bytes: ByteArray) {
        val file = fileFor(name)
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            val temp = File(dir, ".$name.tmp")
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                file.writeBytes(bytes)
                temp.delete()
            }
        }
        changes.value++
    }

    override suspend fun remove(name: String) {
        val file = fileFor(name)
        withContext(Dispatchers.IO) { file.delete() }
        changes.value++
    }

    private fun fileFor(name: String): File {
        val file = File(dir, name)
        require(file.parentFile == dir && name.isNotBlank() && name != "." && name != "..") { "Invalid bookmark name: $name" }
        return file
    }
}
