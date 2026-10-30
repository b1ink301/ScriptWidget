package kr.b1ink.scriptwidget.ui

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeScriptDocuments
import kr.b1ink.scriptwidget.domain.model.ScriptFile
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.repository.SampleScriptSource
import kr.b1ink.scriptwidget.domain.usecase.CreateScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.DeleteScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ExportScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ImportScriptsUseCase
import kr.b1ink.scriptwidget.domain.usecase.ObserveScriptNamesUseCase
import kr.b1ink.scriptwidget.domain.usecase.SeedSampleScriptsUseCase
import kr.b1ink.scriptwidget.ui.list.ScriptListEvent
import kr.b1ink.scriptwidget.ui.list.ScriptListViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptListViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scripts = FakeScriptRepository()
    private val bindings = FakeWidgetBindingRepository(WidgetBinding(4, "01_hello", null))
    private val scheduler = FakeRefreshScheduler()
    private val documents = FakeScriptDocuments(
        "content://a" to ScriptFile("clock.js", "a"),
        "content://b" to ScriptFile("news.js", "b"),
        "content://png" to ScriptFile("photo.png", "\u0000"),
    )

    private fun viewModel() = ScriptListViewModel(
        seedSamples = SeedSampleScriptsUseCase(scripts, SampleScriptSource { mapOf("01_hello" to "h") })::invoke,
        createScript = CreateScriptUseCase(scripts),
        deleteScript = DeleteScriptUseCase(scripts, bindings, scheduler),
        importScripts = ImportScriptsUseCase(scripts, documents),
        exportScript = ExportScriptUseCase(scripts, documents),
        summaries = summaries,
        setAppearance = kr.b1ink.scriptwidget.domain.usecase.SetScriptAppearanceUseCase(scripts),
        backupScripts = kr.b1ink.scriptwidget.domain.usecase.BackupScriptsUseCase(scripts, codec, documents),
        restoreScripts = kr.b1ink.scriptwidget.domain.usecase.RestoreScriptsUseCase(scripts, codec, documents),
        today = { "2026-10-29" },
        activity = activity,
        now = { now },
    )
    private val activity = kotlinx.coroutines.flow.MutableStateFlow<Map<String, kr.b1ink.scriptwidget.domain.model.ScriptActivity>>(emptyMap())
    private var now = 10_000_000L
    private val summaries = kr.b1ink.scriptwidget.domain.usecase.ObserveScriptSummariesUseCase(scripts)
    private val codec = kr.b1ink.scriptwidget.domain.fake.FakeArchiveCodec()

    @Test
    fun `seeds samples on first launch and lists them`() = runTest {
        val vm = viewModel()

        assertEquals(listOf("01_hello"), vm.uiState.value.names)
    }

    @Test
    fun `creating a script opens it in the editor`() = runTest {
        val vm = viewModel()

        vm.createScript()

        assertEquals(ScriptListEvent.OpenEditor("script_1"), vm.events.first())
        assertEquals(listOf("01_hello", "script_1"), vm.uiState.value.names)
    }

    @Test
    fun `delete asks for confirmation first`() = runTest {
        val vm = viewModel()

        vm.requestDelete("01_hello")
        assertEquals("01_hello", vm.uiState.value.pendingDelete)

        vm.cancelDelete()
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(listOf("01_hello"), vm.uiState.value.names)
    }

    @Test
    fun `confirmed delete removes the script and refreshes its widgets`() = runTest {
        val vm = viewModel()

        vm.requestDelete("01_hello")
        vm.confirmDelete()

        assertEquals(emptyList(), vm.uiState.value.names)
        assertNull(vm.uiState.value.pendingDelete)
        assertEquals(listOf(4), scheduler.refreshed)
    }

    // --- import / export ---

    @Test
    fun `importing one file opens it in the editor`() = runTest {
        val vm = viewModel()

        vm.importFiles(listOf("content://a"))

        assertEquals(ScriptListEvent.OpenEditor("clock"), vm.events.first())
        assertEquals(listOf("01_hello", "clock"), vm.uiState.value.names)
    }

    @Test
    fun `importing several files reports the result`() = runTest {
        val vm = viewModel()

        vm.importFiles(listOf("content://a", "content://b", "content://png"))

        val event = vm.events.first() as ScriptListEvent.ShowMessage
        assertTrue("2개" in event.text && "photo.png" in event.text, event.text)
        assertEquals(listOf("01_hello", "clock", "news"), vm.uiState.value.names)
    }

    @Test
    fun `cancelled picker does nothing`() = runTest {
        val vm = viewModel()

        vm.importFiles(emptyList())

        assertEquals(listOf("01_hello"), vm.uiState.value.names)
    }

    @Test
    fun `export asks where to save, then writes the script there`() = runTest {
        val vm = viewModel()

        vm.requestExport("01_hello")
        assertEquals(ScriptListEvent.ChooseExportTarget("01_hello", "01_hello.js"), vm.events.first())

        vm.exportTo("01_hello", "content://out")
        assertEquals("h", documents.written["content://out"])
        assertTrue(vm.events.first() is ScriptListEvent.ShowMessage)
    }

    // --- icons, backup ---

    @Test
    fun `scripts are listed with their icon`() = runTest {
        scripts.write("styled", "// icon-color: red; icon-glyph: bolt;\nx")
        val vm = viewModel()

        val styled = vm.uiState.value.scripts.single { it.name == "styled" }
        assertEquals(kr.b1ink.scriptwidget.domain.model.ScriptAppearance("red", "bolt"), styled.appearance)
    }

    @Test
    fun `changing the icon updates the list`() = runTest {
        val vm = viewModel()

        vm.setAppearance("01_hello", kr.b1ink.scriptwidget.domain.model.ScriptAppearance("teal", "leaf"))

        assertEquals("teal", vm.uiState.value.scripts.single { it.name == "01_hello" }.appearance?.color)
    }

    @Test
    fun `backup asks where to save, then writes every script`() = runTest {
        val vm = viewModel()

        vm.requestBackup()
        assertEquals(ScriptListEvent.ChooseBackupTarget("ScriptWidget-backup-2026-10-29.zip"), vm.events.first())

        vm.backupTo("content://backup")
        assertEquals(mapOf("01_hello.js" to "h"), codec.decode(documents.bytes["content://backup"]!!))
        assertTrue("1개" in (vm.events.first() as ScriptListEvent.ShowMessage).text)
    }

    @Test
    fun `restore reports what was added and what was already there`() = runTest {
        documents.bytes["content://zip"] = codec.encode(mapOf("01_hello.js" to "h", "news.js" to "n"))
        val vm = viewModel()

        vm.restoreFrom("content://zip")

        val text = (vm.events.first() as ScriptListEvent.ShowMessage).text
        assertTrue("news" in text && "1개" in text, text)
        assertEquals("n", scripts.files["news"])
    }

    @Test
    fun `a broken backup file is reported, not crashed on`() = runTest {
        documents.bytes["content://bad"] = "garbage".toByteArray()
        val vm = viewModel()

        vm.restoreFrom("content://bad")

        assertTrue("복원 실패" in (vm.events.first() as ScriptListEvent.ShowMessage).text)
    }

    // --- search and activity ---

    @Test
    fun `search filters scripts by name, ignoring case`() = runTest {
        listOf("Clock", "news", "world clock").forEach { scripts.write(it, "") }
        val vm = viewModel()

        vm.search("CLOCK")

        assertEquals(listOf("Clock", "world clock"), vm.uiState.value.visible.map { it.summary.name })
        vm.search("")
        assertEquals(3, vm.uiState.value.visible.size)
    }

    @Test
    fun `rows say how many widgets use the script and when it last ran`() = runTest {
        listOf("a", "b", "c", "d").forEach { scripts.write(it, "") }
        val minute = 60_000L
        activity.value = mapOf(
            "a" to kr.b1ink.scriptwidget.domain.model.ScriptActivity(widgets = 2, lastRunAt = now - 3 * minute),
            "b" to kr.b1ink.scriptwidget.domain.model.ScriptActivity(widgets = 0, lastRunAt = now - 5 * 60 * minute),
            "c" to kr.b1ink.scriptwidget.domain.model.ScriptActivity(widgets = 1, lastRunAt = now - 3 * 24 * 60 * minute),
        )
        val vm = viewModel()

        val rows = vm.uiState.value.visible.associate { it.summary.name to it.subtitle }
        assertEquals("위젯 2개 · 3분 전 실행", rows["a"])
        assertEquals("위젯 없음 · 5시간 전 실행", rows["b"])
        assertEquals("위젯 1개 · 3일 전 실행", rows["c"])
        assertEquals("위젯 없음", rows["d"], "never ran: only the widget count")
    }
}
