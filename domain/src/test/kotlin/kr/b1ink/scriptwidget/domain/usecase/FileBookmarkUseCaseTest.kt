package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeFileBookmarks
import kr.b1ink.scriptwidget.domain.fake.FakeScriptDocuments
import kr.b1ink.scriptwidget.domain.model.BookmarkResult
import kr.b1ink.scriptwidget.domain.model.FileBookmark
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FileBookmarkUseCaseTest {
    private val bookmarks = FakeFileBookmarks()
    private val docs = FakeScriptDocuments().apply { bytes["content://wall.png"] = byteArrayOf(1, 2, 3) }
    private val add = AddFileBookmarkUseCase(bookmarks, docs)

    @Test
    fun `a picked file is saved under the bookmark name`() = runTest {
        val result = add("still", "content://wall.png")

        assertEquals(BookmarkResult.Saved("still"), result)
        assertEquals(listOf(FileBookmark("still", 3)), bookmarks.bookmarks.first())
    }

    @Test
    fun `names are trimmed`() = runTest {
        add("  still  ", "content://wall.png")

        assertEquals(listOf("still"), bookmarks.bookmarks.first().map { it.name })
    }

    @Test
    fun `picking again for the same name replaces the file`() = runTest {
        add("still", "content://wall.png")
        docs.bytes["content://other.png"] = byteArrayOf(9)

        add("still", "content://other.png")

        assertEquals(listOf(FileBookmark("still", 1)), bookmarks.bookmarks.first())
    }

    @Test
    fun `names that aren't a single file name are rejected`() = runTest {
        for (bad in listOf("", "   ", "a/b", ".", "..", "x\\y")) {
            assertIs<BookmarkResult.Invalid>(add(bad, "content://wall.png"), bad)
        }
        assertTrue(bookmarks.bookmarks.first().isEmpty())
    }

    @Test
    fun `an unreadable file is reported and nothing is saved`() = runTest {
        val result = add("still", "content://gone")

        assertIs<BookmarkResult.Failed>(result)
        assertTrue(bookmarks.bookmarks.first().isEmpty())
    }

    @Test
    fun `remove deletes the bookmark`() = runTest {
        add("still", "content://wall.png")

        RemoveFileBookmarkUseCase(bookmarks)("still")

        assertTrue(bookmarks.bookmarks.first().isEmpty())
    }
}
