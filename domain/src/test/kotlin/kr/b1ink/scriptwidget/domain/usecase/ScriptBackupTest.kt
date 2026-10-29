package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeArchiveCodec
import kr.b1ink.scriptwidget.domain.fake.FakeScriptDocuments
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ScriptBackupTest {
    private val codec = FakeArchiveCodec()
    private val documents = FakeScriptDocuments()

    @Test
    fun `backup writes every script as a js file`() = runTest {
        val scripts = FakeScriptRepository(mapOf("a" to "A", "b" to "B"))

        val count = BackupScriptsUseCase(scripts, codec, documents)("content://backup")

        assertEquals(2, count)
        assertEquals(mapOf("a.js" to "A", "b.js" to "B"), codec.decode(documents.bytes["content://backup"]!!))
    }

    @Test
    fun `restore adds missing scripts, skips identical ones and never overwrites`() = runTest {
        val scripts = FakeScriptRepository(mapOf("same" to "1", "changed" to "old"))
        documents.bytes["content://zip"] = codec.encode(mapOf("same.js" to "1", "changed.js" to "new", "fresh.js" to "f", "notes.txt" to "x"))

        val result = RestoreScriptsUseCase(scripts, codec, documents)("content://zip")

        assertEquals(RestoreResult(added = listOf("changed 2", "fresh"), unchanged = listOf("same")), result)
        assertEquals("old", scripts.files["changed"])
        assertEquals("new", scripts.files["changed 2"])
        assertEquals("f", scripts.files["fresh"])
    }

    @Test
    fun `a file that isn't a backup fails clearly`() = runTest {
        documents.bytes["content://bad"] = "garbage".toByteArray()

        assertFailsWith<java.io.IOException> { RestoreScriptsUseCase(FakeScriptRepository(), codec, documents)("content://bad") }
    }
}
