package kr.b1ink.scriptwidget.ui.bookmarks

import kr.b1ink.scriptwidget.ui.text.UiText
import kr.b1ink.scriptwidget.R
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeFileBookmarks
import kr.b1ink.scriptwidget.domain.fake.FakeScriptDocuments
import kr.b1ink.scriptwidget.ui.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileBookmarksViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val bookmarks = FakeFileBookmarks()
    private val docs = FakeScriptDocuments().apply { bytes["content://wall.png"] = ByteArray(2_048) }
    private fun vm() = FileBookmarksViewModel(bookmarks, docs)

    @Test
    fun `rows show each bookmark with its size`() = runTest {
        bookmarks.files.value = mapOf("still" to ByteArray(2_048), "memo" to ByteArray(10))

        val rows = vm().uiState.value.rows

        assertEquals(listOf("memo" to "10 B", "still" to "2 KB"), rows.map { it.name to it.size })
    }

    @Test
    fun `adding copies the picked file and confirms`() = runTest {
        val vm = vm()

        vm.add("still", "content://wall.png")

        assertEquals(listOf("still"), vm.uiState.value.rows.map { it.name })
        assertEquals(UiText.res(R.string.bookmarks_saved, "still"), vm.uiState.value.message)
    }

    @Test
    fun `a bad name is explained and nothing is saved`() = runTest {
        val vm = vm()

        vm.add("a/b", "content://wall.png")

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertEquals(UiText.res(R.string.bookmarks_name_not_file_name), vm.uiState.value.message)
    }

    @Test
    fun `remove and message dismissal`() = runTest {
        bookmarks.files.value = mapOf("still" to ByteArray(1))
        val vm = vm()

        vm.remove("still")
        vm.messageShown()

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertNull(vm.uiState.value.message)
    }
}
