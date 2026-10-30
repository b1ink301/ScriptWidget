package kr.b1ink.scriptwidget.data.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kr.b1ink.scriptwidget.domain.repository.FontRepository
import java.io.File

/** Font files in [dir] (the sandbox's Documents/fonts), where `new Font(name)` finds them. */
class FontStore(private val dir: File) : FontRepository {
    private val changes = MutableStateFlow(0)

    override val fonts: Flow<List<String>> = changes
        .map {
            dir.listFiles().orEmpty()
                .filter { it.isFile && it.extension.lowercase() in EXTENSIONS }
                .map { it.nameWithoutExtension }
                .distinct()
                .sorted()
        }
        .flowOn(Dispatchers.IO)

    override suspend fun save(fileName: String, bytes: ByteArray) {
        val file = File(dir, fileName)
        require(file.parentFile == dir && file.extension.lowercase() in EXTENSIONS) { "Invalid font file name: $fileName" }
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            file.writeBytes(bytes)
        }
        changes.value++
    }

    private companion object {
        val EXTENSIONS = setOf("ttf", "otf")
    }
}
