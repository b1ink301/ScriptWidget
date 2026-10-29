package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.ScriptCommandResult
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptCommandsTest {
    private val scripts = FakeScriptRepository(mapOf("a" to "A", "script_1" to "S1"))
    private val bindings = FakeWidgetBindingRepository(
        WidgetBinding(1, "a", null),
        WidgetBinding(2, "a", "p"),
        WidgetBinding(3, "script_1", null),
    )
    private val scheduler = FakeRefreshScheduler()

    // --- create ---

    @Test
    fun `create picks the first free script_N name and writes the template`() = runTest {
        val name = CreateScriptUseCase(scripts)()

        assertEquals("script_2", name)
        assertEquals(CreateScriptUseCase.TEMPLATE, scripts.files["script_2"])
    }

    // --- save ---

    @Test
    fun `save writes the content and refreshes widgets using the script`() = runTest {
        val result = SaveScriptUseCase(scripts, bindings, scheduler)("a", "new A")

        assertEquals(ScriptCommandResult.Success("a"), result)
        assertEquals("new A", scripts.files["a"])
        assertEquals(listOf(1, 2), scheduler.refreshed)
    }

    @Test
    fun `save rejects invalid names`() = runTest {
        val result = SaveScriptUseCase(scripts, bindings, scheduler)("../x", "c")

        assertEquals(ScriptCommandResult.InvalidName, result)
        assertFalse("../x" in scripts.files)
    }

    // --- rename ---

    @Test
    fun `rename moves the file, rebinds widgets and refreshes them`() = runTest {
        val result = RenameScriptUseCase(scripts, bindings, scheduler)("a", "b")

        assertEquals(ScriptCommandResult.Success("b"), result)
        assertEquals("A", scripts.files["b"])
        assertFalse("a" in scripts.files)
        assertEquals(WidgetBinding(1, "b", null), bindings.bindings[1])
        assertEquals(WidgetBinding(2, "b", "p"), bindings.bindings[2])
        assertEquals(WidgetBinding(3, "script_1", null), bindings.bindings[3])
        assertEquals(listOf(1, 2), scheduler.refreshed)
    }

    @Test
    fun `rename to an existing name fails without changes`() = runTest {
        val result = RenameScriptUseCase(scripts, bindings, scheduler)("a", "script_1")

        assertEquals(ScriptCommandResult.AlreadyExists, result)
        assertEquals("A", scripts.files["a"])
        assertEquals("a", bindings.bindings[1]!!.scriptName)
    }

    @Test
    fun `rename to an invalid name fails`() = runTest {
        assertEquals(ScriptCommandResult.InvalidName, RenameScriptUseCase(scripts, bindings, scheduler)("a", " "))
    }

    @Test
    fun `rename of a missing script fails`() = runTest {
        assertEquals(ScriptCommandResult.NotFound, RenameScriptUseCase(scripts, bindings, scheduler)("zzz", "y"))
    }

    @Test
    fun `rename to the same name is a no-op success`() = runTest {
        assertEquals(ScriptCommandResult.Success("a"), RenameScriptUseCase(scripts, bindings, scheduler)("a", "a"))
        assertTrue(scheduler.refreshed.isEmpty())
    }

    // --- delete ---

    @Test
    fun `delete removes the file and refreshes widgets so they show the error`() = runTest {
        DeleteScriptUseCase(scripts, bindings, scheduler)("a")

        assertFalse("a" in scripts.files)
        assertEquals(listOf(1, 2), scheduler.refreshed)
    }
}
