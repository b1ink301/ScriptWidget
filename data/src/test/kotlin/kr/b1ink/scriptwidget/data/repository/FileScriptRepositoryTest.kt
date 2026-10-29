package kr.b1ink.scriptwidget.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileScriptRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dir by lazy { File(tmp.root, "scripts") }
    private val repo by lazy { FileScriptRepository(dir) }

    @Test
    fun `writes and reads scripts as js files`() = runTest {
        repo.write("hello", "code")

        assertEquals("code", File(dir, "hello.js").readText())
        assertEquals("code", repo.read("hello"))
        assertTrue(repo.exists("hello"))
        assertNull(repo.read("missing"))
        assertFalse(repo.exists("missing"))
    }

    @Test
    fun `lists only js files, sorted case-insensitively`() = runTest {
        dir.mkdirs()
        File(dir, "notes.txt").writeText("x")
        repo.write("b", "")
        repo.write("A", "")

        assertEquals(listOf("A", "b"), repo.list())
    }

    @Test
    fun `scriptNames reflects every change`() = runTest {
        repo.write("one", "")
        assertEquals(listOf("one"), repo.scriptNames.value)

        repo.rename("one", "two")
        assertEquals(listOf("two"), repo.scriptNames.value)

        repo.delete("two")
        assertEquals(emptyList(), repo.scriptNames.value)
    }

    @Test
    fun `picks up files that already exist`() = runTest {
        dir.mkdirs()
        File(dir, "existing.js").writeText("x")

        assertEquals(listOf("existing"), FileScriptRepository(dir).scriptNames.value)
    }

    @Test
    fun `rename fails when the target exists or the source is missing`() = runTest {
        repo.write("a", "A")
        repo.write("b", "B")

        assertFalse(repo.rename("a", "b"))
        assertFalse(repo.rename("zzz", "c"))
        assertEquals("A", repo.read("a"))
        assertEquals("B", repo.read("b"))
    }
}
