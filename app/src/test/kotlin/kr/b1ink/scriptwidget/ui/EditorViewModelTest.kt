package kr.b1ink.scriptwidget.ui

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeScriptEngine
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.domain.usecase.GetScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.RenameScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.RunScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.SaveScriptUseCase
import kotlinx.coroutines.flow.first
import kr.b1ink.scriptwidget.ui.editor.EditorEvent
import kr.b1ink.scriptwidget.ui.editor.EditorTab
import kr.b1ink.scriptwidget.ui.editor.PermissionChecker
import kr.b1ink.scriptwidget.ui.editor.ScriptPermission
import kr.b1ink.scriptwidget.ui.editor.EditorViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scripts = FakeScriptRepository(mapOf("weather" to "old code", "taken" to ""))
    private val bindings = FakeWidgetBindingRepository(WidgetBinding(2, "weather", null))
    private val scheduler = FakeRefreshScheduler()
    private val engine = FakeScriptEngine(ScriptRunResult(widget = WidgetRoot(children = listOf(TextNode("ok")))))

    private val granted = mutableSetOf<ScriptPermission>()

    private fun viewModel(name: String = "weather") = EditorViewModel(
        initialName = name,
        getScript = GetScriptUseCase(scripts),
        saveScript = SaveScriptUseCase(scripts, bindings, scheduler),
        renameScript = RenameScriptUseCase(scripts, bindings, scheduler),
        runScript = RunScriptUseCase(engine),
        permissions = PermissionChecker { it in granted },
    )

    @Test
    fun `loads the script on start`() = runTest {
        val state = viewModel().uiState.value

        assertEquals("weather", state.name)
        assertEquals("old code", state.code)
        assertFalse(state.dirty)
        assertEquals(EditorTab.Code, state.tab)
    }

    @Test
    fun `editing marks the script dirty and saving writes it and refreshes widgets`() = runTest {
        val vm = viewModel()

        vm.onCodeChange("new code")
        assertTrue(vm.uiState.value.dirty)

        vm.save()

        assertEquals("new code", scripts.files["weather"])
        assertFalse(vm.uiState.value.dirty)
        assertEquals(listOf(2), scheduler.refreshed)
    }

    @Test
    fun `saving without changes does nothing`() = runTest {
        viewModel().save()

        assertTrue(scheduler.refreshed.isEmpty())
    }

    @Test
    fun `run saves first, runs as an in-app preview and shows the result`() = runTest {
        val vm = viewModel()
        vm.onCodeChange("edited")
        vm.onParameterChange("  ")
        vm.selectFamily(WidgetFamily.Large)

        vm.run()

        assertEquals("edited", scripts.files["weather"])
        val call = engine.calls.single()
        assertEquals("edited", call.script)
        assertEquals(ScriptEnvironment(runsInWidget = false, widgetFamily = WidgetFamily.Large, widgetParameter = null, cacheKey = "preview"), call.env)
        val state = vm.uiState.value
        assertEquals(EditorTab.Result, state.tab)
        assertFalse(state.running)
        assertEquals("ok", (assertNotNull(state.result).widget!!.children.single() as TextNode).text)
    }

    @Test
    fun `changing the family re-runs only when a result is shown`() = runTest {
        val vm = viewModel()

        vm.selectFamily(WidgetFamily.Medium)
        assertTrue(engine.calls.isEmpty())

        vm.run()
        vm.selectFamily(WidgetFamily.Small)

        assertEquals(listOf(WidgetFamily.Medium, WidgetFamily.Small), engine.calls.map { it.env.widgetFamily })
    }

    @Test
    fun `parameter is passed to the script`() = runTest {
        val vm = viewModel()
        vm.onParameterChange("JPY")

        vm.run()

        assertEquals("JPY", engine.calls.single().env.widgetParameter)
    }

    @Test
    fun `rename updates the name`() = runTest {
        val vm = viewModel()

        vm.rename("forecast")

        assertEquals("forecast", vm.uiState.value.name)
        assertNull(vm.uiState.value.message)
        assertEquals("forecast", bindings.bindings[2]!!.scriptName)
    }

    @Test
    fun `rename failures are reported and keep the name`() = runTest {
        val vm = viewModel()

        vm.rename("taken")
        assertEquals(EditorViewModel.MESSAGE_NAME_TAKEN, vm.uiState.value.message)

        vm.rename("a/b")
        assertEquals(EditorViewModel.MESSAGE_NAME_INVALID, vm.uiState.value.message)
        assertEquals("weather", vm.uiState.value.name)

        vm.dismissMessage()
        assertNull(vm.uiState.value.message)
    }

    // --- location permission ---

    @Test
    fun `scripts using Location ask for permission before the first run`() = runTest {
        val vm = viewModel()
        vm.onCodeChange("const l = await Location.current();")

        vm.run()

        assertEquals(EditorEvent.RequestPermissions(setOf(ScriptPermission.Location)), vm.events.first())
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun `the run continues after the permission prompt, whatever the answer`() = runTest {
        val vm = viewModel()
        vm.onCodeChange("await Location.current();")
        vm.run()

        vm.onPermissionsResult()

        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `no prompt when permission is granted or Location is unused`() = runTest {
        granted += ScriptPermission.Location
        val vm = viewModel()
        vm.onCodeChange("await Location.current();")
        vm.run()

        granted.clear()
        val other = viewModel("taken")
        other.run()

        assertEquals(2, engine.calls.size)
    }

    @Test
    fun `scripts posting notifications ask for the notification permission too`() = runTest {
        val vm = viewModel()
        vm.onCodeChange("const l = await Location.current(); const n = new Notification(); await n.schedule();")

        vm.run()

        assertEquals(
            EditorEvent.RequestPermissions(setOf(ScriptPermission.Location, ScriptPermission.Notifications)),
            vm.events.first(),
        )
    }

    @Test
    fun `only missing permissions are requested`() = runTest {
        granted += ScriptPermission.Location
        val vm = viewModel()
        vm.onCodeChange("await Location.current(); await new Notification().schedule();")

        vm.run()

        assertEquals(EditorEvent.RequestPermissions(setOf(ScriptPermission.Notifications)), vm.events.first())
    }

    @Test
    fun `deep link runs pass their query parameters to the script`() = runTest {
        val vm = viewModel()

        vm.run(queryParameters = mapOf("city" to "Seoul"))

        assertEquals(mapOf("city" to "Seoul"), engine.calls.single().env.queryParameters)
    }

    @Test
    fun `calendar, contacts and photos usage asks for those permissions`() = runTest {
        val vm = viewModel()
        vm.onCodeChange("await CalendarEvent.today(); await Contact.all(); await Photos.latestPhoto();")

        vm.run()

        assertEquals(
            EditorEvent.RequestPermissions(setOf(ScriptPermission.Calendar, ScriptPermission.Contacts, ScriptPermission.Photos)),
            vm.events.first(),
        )
    }

    @Test
    fun `a run requested before the script has loaded waits for it`() = runTest {
        // scriptwidget://run opens the editor and runs immediately; reading the file can be slower than that.
        val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow = object : kr.b1ink.scriptwidget.domain.repository.ScriptRepository by scripts {
            override suspend fun read(name: String): String? {
                loaded.await()
                return scripts.read(name)
            }
        }
        val vm = EditorViewModel(
            initialName = "weather",
            getScript = GetScriptUseCase(slow),
            saveScript = SaveScriptUseCase(slow, bindings, scheduler),
            renameScript = RenameScriptUseCase(slow, bindings, scheduler),
            runScript = RunScriptUseCase(engine),
            permissions = PermissionChecker { it in granted },
        )

        vm.run(queryParameters = mapOf("from" to "link"))
        loaded.complete(Unit)

        assertEquals("old code", engine.calls.single().script)
        assertNotNull(vm.uiState.value.result)
    }

    @Test
    fun `a run request hands its context to the script`() = runTest {
        val vm = viewModel()
        val request = kr.b1ink.scriptwidget.ui.editor.RunRequest(
            source = kr.b1ink.scriptwidget.domain.model.RunSource.ShareSheet,
            shareInput = kr.b1ink.scriptwidget.domain.model.ShareInput(plainTexts = listOf("hi")),
        )

        vm.run(request)

        val env = engine.calls.single().env
        assertEquals(kr.b1ink.scriptwidget.domain.model.RunSource.ShareSheet, env.source)
        assertEquals(listOf("hi"), env.shareInput.plainTexts)
        assertFalse(env.runsInWidget)
    }

    @Test
    fun `the request survives the permission prompt`() = runTest {
        val vm = viewModel()
        vm.onCodeChange("await Location.current();")
        val payload = kr.b1ink.scriptwidget.domain.model.NotificationPayload(identifier = "n")

        vm.run(kr.b1ink.scriptwidget.ui.editor.RunRequest(source = kr.b1ink.scriptwidget.domain.model.RunSource.Notification, notification = payload))
        assertTrue(engine.calls.isEmpty())
        vm.onPermissionsResult()

        assertEquals(payload, engine.calls.single().env.notification)
    }
}
