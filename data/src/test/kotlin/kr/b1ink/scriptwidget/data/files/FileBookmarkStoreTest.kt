package kr.b1ink.scriptwidget.data.files

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.model.FileBookmark
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileBookmarkStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dir by lazy { java.io.File(tmp.root, "Bookmarks") }
    private val store by lazy { FileBookmarkStore(dir) }

    @Test
    fun `bookmarks are files named after the bookmark, listed by name`() = runTest {
        store.save("still", byteArrayOf(1, 2, 3))
        store.save("photo", byteArrayOf(4))

        assertEquals(listOf(FileBookmark("photo", 1), FileBookmark("still", 3)), store.bookmarks.first())
        assertContentEquals(byteArrayOf(1, 2, 3), java.io.File(dir, "still").readBytes())
    }

    @Test
    fun `saving an existing name replaces its file`() = runTest {
        store.save("still", byteArrayOf(1, 2, 3))
        store.save("still", byteArrayOf(9))

        assertEquals(listOf(FileBookmark("still", 1)), store.bookmarks.first())
    }

    @Test
    fun `remove deletes the file`() = runTest {
        store.save("still", byteArrayOf(1))

        store.remove("still")

        assertTrue(store.bookmarks.first().isEmpty())
        assertFalse(java.io.File(dir, "still").exists())
    }

    @Test
    fun `bookmarks already on disk are listed`() = runTest {
        dir.mkdirs()
        java.io.File(dir, "old").writeBytes(byteArrayOf(1, 1))

        assertEquals(listOf(FileBookmark("old", 2)), store.bookmarks.first())
    }

    @Test
    fun `names can't escape the folder`() = runTest {
        kotlin.test.assertFailsWith<IllegalArgumentException> { store.save("../evil", byteArrayOf(1)) }
    }
}
