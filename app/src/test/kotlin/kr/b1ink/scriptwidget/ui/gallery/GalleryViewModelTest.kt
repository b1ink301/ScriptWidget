package kr.b1ink.scriptwidget.ui.gallery

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeCatalogSource
import kr.b1ink.scriptwidget.domain.fake.FakeCatalogUrls
import kr.b1ink.scriptwidget.domain.fake.FakeInstalledScripts
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kr.b1ink.scriptwidget.domain.model.EntryStatus
import kr.b1ink.scriptwidget.ui.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GalleryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scripts = FakeScriptRepository()
    private val installed = FakeInstalledScripts()
    private val urls = FakeCatalogUrls()

    private fun entry(id: String, name: String, category: String, version: Int = 1, permissions: List<String> = emptyList()) =
        CatalogEntry(id = id, name = name, category = category, script = "$id.js", version = version, permissions = permissions)

    private val builtin = FakeCatalogSource(
        "builtin", "기본 갤러리",
        entries = listOf(entry("events", "오늘 일정", "일정·할 일"), entry("quotes", "명언", "정보")),
        code = mapOf("events" to "// events", "quotes" to "// quotes"),
    )
    private val remote = FakeCatalogSource(
        "https://example.com/index.json", "Example",
        entries = listOf(entry("clock", "세계 시계", "정보", permissions = listOf("network"))),
        code = mapOf("clock" to "// remote clock"),
    )

    private fun vm() = GalleryViewModel(
        builtin = builtin,
        remote = { url -> if (url == remote.id) remote else FakeCatalogSource(url, url, failure = "404") },
        urls = urls,
        installed = installed,
        scripts = scripts,
    )

    @Test
    fun `sections and categories are listed`() = runTest {
        urls.add(remote.id)
        val state = vm().uiState.value

        assertEquals(listOf("기본 갤러리", "Example"), state.sections.map { it.title })
        assertEquals(listOf("일정·할 일", "정보"), state.categories)
    }

    @Test
    fun `a category filter keeps only matching entries`() = runTest {
        urls.add(remote.id)
        val vm = vm()

        vm.selectCategory("정보")

        assertEquals(listOf("명언", "세계 시계"), vm.uiState.value.visibleSections.flatMap { s -> s.items.map { it.entry.name } })
    }

    @Test
    fun `built-in entries install right away and can be opened`() = runTest {
        val vm = vm()

        vm.install(builtin.id, "events")

        assertEquals("// events", scripts.files["오늘 일정"])
        assertEquals(EntryStatus.Installed("오늘 일정"), vm.uiState.value.sections[0].items[0].status)
        assertEquals("'오늘 일정'을 설치했습니다", vm.uiState.value.message)
    }

    @Test
    fun `remote entries show their code and permissions before installing`() = runTest {
        urls.add(remote.id)
        val vm = vm()

        vm.install(remote.id, "clock")

        val preview = assertNotNull(vm.uiState.value.preview)
        assertEquals("// remote clock", preview.code)
        assertEquals(listOf("network"), preview.entry.permissions)
        assertTrue(scripts.files.isEmpty(), "nothing installed before confirming")

        vm.confirmInstall()

        assertNull(vm.uiState.value.preview)
        assertEquals("// remote clock", scripts.files["세계 시계"])
    }

    @Test
    fun `cancelling the preview installs nothing`() = runTest {
        urls.add(remote.id)
        val vm = vm()
        vm.install(remote.id, "clock")

        vm.cancelPreview()

        assertNull(vm.uiState.value.preview)
        assertTrue(scripts.files.isEmpty())
    }

    @Test
    fun `updates replace unchanged scripts`() = runTest {
        val vm = vm()
        vm.install(builtin.id, "events")
        builtin.entries = listOf(entry("events", "오늘 일정", "일정·할 일", version = 2), entry("quotes", "명언", "정보"))
        builtin.code = mapOf("events" to "// events v2", "quotes" to "// quotes")
        vm.refresh()

        vm.update(builtin.id, "events")

        assertEquals("// events v2", scripts.files["오늘 일정"])
        assertEquals("'오늘 일정'을 업데이트했습니다", vm.uiState.value.message)
    }

    @Test
    fun `catalog urls can be added and removed`() = runTest {
        val vm = vm()

        vm.addUrl("https://example.com/index.json")
        assertEquals(listOf("기본 갤러리", "Example"), vm.uiState.value.sections.map { it.title })

        vm.addUrl("ftp://nope")
        assertEquals("https:// 로 시작하는 주소를 입력하세요", vm.uiState.value.message)

        vm.removeUrl("https://example.com/index.json")
        assertEquals(listOf("기본 갤러리"), vm.uiState.value.sections.map { it.title })
    }

    @Test
    fun `a failing catalog shows its error`() = runTest {
        urls.add("https://broken.example/index.json")

        assertEquals("404", vm().uiState.value.sections[1].error)
    }
}
