package kr.b1ink.scriptwidget.ui

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.api.ApiEntry
import kr.b1ink.scriptwidget.data.api.ApiMember
import kr.b1ink.scriptwidget.ui.api.ApiReferenceViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiReferenceViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val catalog = listOf(
        ApiEntry("ListWidget", ApiEntry.Kind.Class, members = listOf(ApiMember("addText", ApiMember.Kind.Method, listOf("text"))), summary = "위젯의 루트"),
        ApiEntry("Request", ApiEntry.Kind.Class, members = listOf(ApiMember("loadJSON", ApiMember.Kind.Method, isAsync = true)), summary = "HTTP 요청"),
        ApiEntry("Font", ApiEntry.Kind.Class, members = listOf(ApiMember("boldSystemFont", ApiMember.Kind.Method, listOf("size"), isStatic = true)), summary = "글꼴"),
    )

    private fun vm() = ApiReferenceViewModel { catalog }

    @Test
    fun `everything is listed alphabetically`() = runTest {
        assertEquals(listOf("Font", "ListWidget", "Request"), vm().uiState.value.entries.map { it.name })
    }

    @Test
    fun `search matches names, members and descriptions`() = runTest {
        val vm = vm()

        vm.search("json")
        assertEquals(listOf("Request"), vm.uiState.value.entries.map { it.name })

        vm.search("글꼴")
        assertEquals(listOf("Font"), vm.uiState.value.entries.map { it.name })

        vm.search("  ")
        assertEquals(3, vm.uiState.value.entries.size)
    }

    @Test
    fun `selecting an entry shows its details`() = runTest {
        val vm = vm()

        vm.select("Request")

        assertEquals("Request", vm.uiState.value.selected?.name)
        vm.select(null)
        assertEquals(null, vm.uiState.value.selected)
    }
}
