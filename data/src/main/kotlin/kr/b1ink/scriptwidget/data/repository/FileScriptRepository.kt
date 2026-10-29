package kr.b1ink.scriptwidget.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository
import java.io.File

/** Scripts are plain `<name>.js` files in [dir]. */
class FileScriptRepository(
    private val dir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ScriptRepository {

    private val names = MutableStateFlow(scan())
    override val scriptNames: StateFlow<List<String>> = names.asStateFlow()

    private fun scan(): List<String> =
        dir.listFiles { f -> f.isFile && f.extension == EXTENSION }.orEmpty()
            .map { it.nameWithoutExtension }
            .sortedBy { it.lowercase() }

    private fun refresh() {
        names.value = scan()
    }

    private fun fileOf(name: String) = File(dir, "$name.$EXTENSION")

    override suspend fun list(): List<String> = withContext(ioDispatcher) { scan() }

    override suspend fun read(name: String): String? = withContext(ioDispatcher) {
        fileOf(name).takeIf { it.isFile }?.readText()
    }

    override suspend fun exists(name: String): Boolean = withContext(ioDispatcher) { fileOf(name).isFile }

    override suspend fun write(name: String, content: String) = withContext(ioDispatcher) {
        dir.mkdirs()
        fileOf(name).writeText(content)
        refresh()
    }

    override suspend fun rename(from: String, to: String): Boolean = withContext(ioDispatcher) {
        val source = fileOf(from)
        val target = fileOf(to)
        if (!source.isFile || target.exists()) return@withContext false
        source.renameTo(target).also { refresh() }
    }

    override suspend fun delete(name: String) = withContext(ioDispatcher) {
        fileOf(name).delete()
        refresh()
    }

    private companion object {
        const val EXTENSION = "js"
    }
}
