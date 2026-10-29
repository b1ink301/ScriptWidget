package kr.b1ink.scriptwidget.data.history

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.model.LogLine
import kr.b1ink.scriptwidget.domain.model.RunRecord
import kr.b1ink.scriptwidget.domain.model.RunSource
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class FileRunHistoryRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val file by lazy { tmp.root.resolve("history/runs.jsonl") }

    private fun record(name: String, at: Long, error: String? = null) =
        RunRecord(scriptName = name, source = RunSource.App, startedAt = at, durationMs = 5, error = error, logs = listOf(LogLine("log", "hi")))

    @Test
    fun `newest first and kept across instances`() = runTest {
        val repo = FileRunHistoryRepository(file)
        repo.add(record("a", 1))
        repo.add(record("b", 2, error = "x"))

        assertEquals(listOf("b", "a"), repo.history.first().map { it.scriptName })
        assertEquals(listOf("b", "a"), FileRunHistoryRepository(file).history.first().map { it.scriptName })
        assertEquals("x", FileRunHistoryRepository(file).history.first().first().error)
    }

    @Test
    fun `only the most recent runs are kept`() = runTest {
        val repo = FileRunHistoryRepository(file, maxRecords = 3)
        (1..5).forEach { repo.add(record("s$it", it.toLong())) }

        assertEquals(listOf("s5", "s4", "s3"), repo.history.first().map { it.scriptName })
    }

    @Test
    fun `clear empties the history`() = runTest {
        val repo = FileRunHistoryRepository(file)
        repo.add(record("a", 1))
        repo.clear()

        assertEquals(emptyList(), repo.history.first())
        assertEquals(emptyList(), FileRunHistoryRepository(file).history.first())
    }

    @Test
    fun `a damaged file starts an empty history`() = runTest {
        file.parentFile.mkdirs()
        file.writeText("{ not json")

        assertEquals(emptyList(), FileRunHistoryRepository(file).history.first())
    }

    // --- storage writes: a run appends one line instead of rewriting the whole file ---

    @Test
    fun `adding a run appends to the file`() = runTest {
        val repo = FileRunHistoryRepository(file)
        repo.add(record("a", 1))
        val before = file.readText()

        repo.add(record("b", 2))

        val after = file.readText()
        kotlin.test.assertTrue(after.startsWith(before), "existing lines must stay untouched")
        assertEquals(2, after.lines().count { it.isNotBlank() })
    }

    @Test
    fun `the file is compacted once it holds twice the limit`() = runTest {
        val repo = FileRunHistoryRepository(file, maxRecords = 3)
        (1..6).forEach { repo.add(record("s$it", it.toLong())) }
        assertEquals(6, file.readText().lines().count { it.isNotBlank() })

        repo.add(record("s7", 7))

        assertEquals(3, file.readText().lines().count { it.isNotBlank() })
        assertEquals(listOf("s7", "s6", "s5"), FileRunHistoryRepository(file, maxRecords = 3).history.first().map { it.scriptName })
    }

    @Test
    fun `history from the old single JSON file is carried over`() = runTest {
        val legacy = tmp.root.resolve("history/runs.json").apply {
            parentFile.mkdirs()
            writeText(kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(RunRecord.serializer()), listOf(record("old2", 2), record("old1", 1))))
        }

        val repo = FileRunHistoryRepository(file, legacyFile = legacy)
        repo.add(record("new", 3))

        assertEquals(listOf("new", "old2", "old1"), repo.history.first().map { it.scriptName })
        kotlin.test.assertFalse(legacy.exists())
    }

    @Test
    fun `a damaged line doesn't lose the other runs`() = runTest {
        FileRunHistoryRepository(file).add(record("a", 1))
        file.appendText("{not json\n")
        FileRunHistoryRepository(file).add(record("b", 2))

        assertEquals(listOf("b", "a"), FileRunHistoryRepository(file).history.first().map { it.scriptName })
    }
}
