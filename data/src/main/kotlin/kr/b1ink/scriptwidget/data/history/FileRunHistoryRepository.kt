package kr.b1ink.scriptwidget.data.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.domain.model.RunRecord
import kr.b1ink.scriptwidget.domain.repository.RunHistoryRepository
import java.io.File

/**
 * The last [maxRecords] runs, one JSON line per run in [file] (oldest first). A run appends one
 * line; the file is rewritten only when it reaches twice the limit, so frequent widget refreshes
 * don't rewrite the whole history each time. [legacyFile] (the old single JSON array) is imported once.
 */
class FileRunHistoryRepository(
    private val file: File,
    private val maxRecords: Int = 200,
    private val legacyFile: File? = null,
) : RunHistoryRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    /** Newest first, as shown. */
    private val records = MutableStateFlow<List<RunRecord>?>(null)
    private var linesInFile = 0

    override val history: Flow<List<RunRecord>> = flow {
        loaded()
        emitAll(records.filterNotNull())
    }

    override suspend fun add(record: RunRecord) = lock.withLock {
        val next = (listOf(record) + loadedLocked()).take(maxRecords)
        withContext(Dispatchers.IO) {
            if (linesInFile + 1 > maxRecords * 2) {
                rewrite(next)
            } else {
                file.parentFile?.mkdirs()
                file.appendText(json.encodeToString(RunRecord.serializer(), record) + "\n")
                linesInFile++
            }
        }
        records.value = next
    }

    override suspend fun clear() = lock.withLock {
        withContext(Dispatchers.IO) { rewrite(emptyList()) }
        records.value = emptyList()
    }

    private suspend fun loaded(): List<RunRecord> = lock.withLock { loadedLocked() }

    private suspend fun loadedLocked(): List<RunRecord> = records.value ?: withContext(Dispatchers.IO) {
        importLegacy()
        val lines = if (file.isFile) file.readLines().filter { it.isNotBlank() } else emptyList()
        linesInFile = lines.size
        // A damaged line (e.g. cut off by a crash) is skipped, not fatal.
        lines.mapNotNull { runCatching { json.decodeFromString(RunRecord.serializer(), it) }.getOrNull() }
            .asReversed()
            .take(maxRecords)
    }.also { records.value = it }

    private fun importLegacy() {
        val legacy = legacyFile?.takeIf { it.isFile } ?: return
        val old = runCatching { json.decodeFromString(ListSerializer(RunRecord.serializer()), legacy.readText()) }.getOrDefault(emptyList())
        if (!file.isFile) rewrite(old.take(maxRecords))
        legacy.delete()
    }

    /** Writes [newestFirst] as lines, oldest first. */
    private fun rewrite(newestFirst: List<RunRecord>) {
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, "${file.name}.tmp")
        temp.writeText(newestFirst.asReversed().joinToString("") { json.encodeToString(RunRecord.serializer(), it) + "\n" })
        if (!temp.renameTo(file)) {
            file.writeText(temp.readText())
            temp.delete()
        }
        linesInFile = newestFirst.size
    }
}
