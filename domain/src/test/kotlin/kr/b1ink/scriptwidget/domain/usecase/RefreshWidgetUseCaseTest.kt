package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeNetworkState
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshSettings
import kr.b1ink.scriptwidget.domain.fake.FakeRenderStateRepository
import kr.b1ink.scriptwidget.domain.fake.FakeScriptEngine
import kr.b1ink.scriptwidget.domain.fake.FakeScreenState
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.fake.FixedClock
import kr.b1ink.scriptwidget.domain.model.RefreshTrigger
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.RunTimings
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RefreshWidgetUseCaseTest {
    private val scripts = FakeScriptRepository(mapOf("weather" to "weather code", "other" to "other code"))
    private val bindings = FakeWidgetBindingRepository(WidgetBinding(7, "weather", "Seoul"))
    private val renderStates = FakeRenderStateRepository()
    private val engine = FakeScriptEngine()
    private val clock = FixedClock(42L)
    private val scheduler = FakeRefreshScheduler()
    private val screen = FakeScreenState(on = true)
    private val network = FakeNetworkState(connected = true)
    private val settings = FakeRefreshSettings(whileScreenOff = false)
    private val refresh = RefreshWidgetUseCase(bindings, scripts, RunScriptUseCase(engine), renderStates, scheduler, clock, screen, network, settings)

    private val okTree = WidgetRoot(children = listOf(TextNode("sunny")))

    @Test
    fun `runs the bound script with the widget environment`() = runTest {
        engine.result = ScriptRunResult(widget = okTree)

        refresh(appWidgetId = 7, family = WidgetFamily.Medium)

        val call = engine.calls.single()
        assertEquals("weather code", call.script)
        assertEquals("weather", call.scriptName)
        assertEquals(
            ScriptEnvironment(
                runsInWidget = true,
                widgetFamily = WidgetFamily.Medium,
                widgetParameter = "Seoul",
                cacheKey = "widget-7",
            ),
            call.env,
        )
    }

    @Test
    fun `publishes the produced tree on success`() = runTest {
        val timings = RunTimings(totalMs = 5)
        engine.result = ScriptRunResult(widget = okTree, timings = timings)

        val state = refresh(7, WidgetFamily.Small)

        assertEquals(RenderState(scriptName = "weather", tree = okTree, updatedAt = 42L, timings = timings, family = WidgetFamily.Small), state)
        assertEquals(listOf(7 to state), renderStates.published)
    }

    @Test
    fun `keeps the last good tree when the script fails`() = runTest {
        renderStates.states[7] = RenderState(scriptName = "weather", tree = okTree, updatedAt = 1)
        engine.result = ScriptRunResult(error = "network down")

        val state = refresh(7, WidgetFamily.Small)

        assertEquals(okTree, state.tree)
        assertEquals("network down", state.error)
        assertEquals(42L, state.updatedAt)
    }

    @Test
    fun `does not keep a tree that belonged to a different script`() = runTest {
        renderStates.states[7] = RenderState(scriptName = "other", tree = okTree, updatedAt = 1)
        engine.result = ScriptRunResult(error = "boom")

        val state = refresh(7, WidgetFamily.Small)

        assertNull(state.tree)
        assertEquals("boom", state.error)
    }

    @Test
    fun `unbound widget shows an error without running anything`() = runTest {
        val state = refresh(99, WidgetFamily.Small)

        assertTrue(engine.calls.isEmpty())
        assertNull(state.scriptName)
        assertEquals(RefreshWidgetUseCase.NOT_BOUND_ERROR, state.error)
        assertEquals(state, renderStates.states[99])
    }

    @Test
    fun `missing script shows an error naming the script`() = runTest {
        bindings.bindings[8] = WidgetBinding(8, "deleted", null)

        val state = refresh(8, WidgetFamily.Small)

        assertTrue(engine.calls.isEmpty())
        assertEquals("deleted", state.scriptName)
        assertNotNull(state.error)
        assertTrue("deleted" in state.error)
    }

    // --- refreshAfterDate ---

    private val minute = 60_000L
    private val later = 42L + 60 * minute
    private val laterTree = okTree.copy(refreshAfter = later)

    @Test
    fun `schedules a refresh at the time the script asked for`() = runTest {
        engine.result = ScriptRunResult(widget = laterTree)

        refresh(7, WidgetFamily.Small)

        assertEquals(listOf(7 to later), scheduler.refreshAt)
    }

    @Test
    fun `a refresh time in the past waits for the minimum interval`() = runTest {
        clock.millis = 100 * minute
        engine.result = ScriptRunResult(widget = okTree.copy(refreshAfter = 10L))

        refresh(7, WidgetFamily.Small)

        assertEquals(listOf(7 to 105 * minute), scheduler.refreshAt)
    }

    @Test
    fun `periodic refresh before refreshAfter is skipped and keeps the current state`() = runTest {
        clock.millis = 30 * minute
        val current = RenderState(scriptName = "weather", tree = laterTree, updatedAt = 1)
        renderStates.states[7] = current

        val state = refresh(7, WidgetFamily.Small, RefreshTrigger.Periodic)

        assertEquals(current, state)
        assertTrue(engine.calls.isEmpty())
        assertTrue(renderStates.published.isEmpty())
    }

    @Test
    fun `periodic refresh after refreshAfter runs the script`() = runTest {
        renderStates.states[7] = RenderState(scriptName = "weather", tree = laterTree, updatedAt = 1)
        clock.millis = later
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Periodic)

        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `manual and scheduled refreshes ignore refreshAfter`() = runTest {
        renderStates.states[7] = RenderState(scriptName = "weather", tree = laterTree, updatedAt = 1)
        clock.millis = 30 * minute
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Manual)
        clock.millis += 6 * minute
        refresh(7, WidgetFamily.Small, RefreshTrigger.Scheduled)

        assertEquals(2, engine.calls.size)
    }

    @Test
    fun `refreshAfter of another script does not block a periodic refresh`() = runTest {
        renderStates.states[7] = RenderState(scriptName = "other", tree = laterTree, updatedAt = 1)
        clock.millis = 30 * minute
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Periodic)

        assertEquals(1, engine.calls.size)
    }

    // --- battery: minimum refresh interval (5 min with the screen on, 15 min with it off) ---

    @Test
    fun `with the screen on a refresh is at least 5 minutes away and doesn't wake the device`() = runTest {
        engine.result = ScriptRunResult(widget = okTree.copy(refreshAfter = 42L + minute))

        refresh(7, WidgetFamily.Small)

        assertEquals(listOf(7 to 42L + 5 * minute), scheduler.refreshAt)
        assertEquals(listOf(false), scheduler.wakeUps)
    }

    @Test
    fun `with refreshing while the screen is off, a refresh is at least 15 minutes away and may wake the device`() = runTest {
        settings.whileScreenOff = true
        screen.on = false
        engine.result = ScriptRunResult(widget = okTree.copy(refreshAfter = 42L + 3 * minute))

        refresh(7, WidgetFamily.Small)

        assertEquals(listOf(7 to 42L + 15 * minute), scheduler.refreshAt)
        assertEquals(listOf(true), scheduler.wakeUps)
    }

    @Test
    fun `a scheduled refresh too soon after the last one is skipped and moved`() = runTest {
        val current = RenderState(scriptName = "weather", tree = okTree.copy(refreshAfter = 2 * minute), updatedAt = minute)
        renderStates.states[7] = current
        clock.millis = 3 * minute

        val state = refresh(7, WidgetFamily.Small, RefreshTrigger.Scheduled)

        assertEquals(current, state)
        assertTrue(engine.calls.isEmpty())
        assertEquals(listOf(7 to 6 * minute), scheduler.refreshAt)
    }

    @Test
    fun `with refreshing while the screen is off, scheduled refreshes run at most every 15 minutes`() = runTest {
        settings.whileScreenOff = true
        screen.on = false
        renderStates.states[7] = RenderState(scriptName = "weather", tree = okTree.copy(refreshAfter = 5 * minute), updatedAt = 1)
        engine.result = ScriptRunResult(widget = okTree)

        clock.millis = 10 * minute
        refresh(7, WidgetFamily.Small, RefreshTrigger.Scheduled)
        assertTrue(engine.calls.isEmpty())
        assertEquals(listOf(7 to 15 * minute + 1), scheduler.refreshAt)
        assertEquals(listOf(true), scheduler.wakeUps)

        clock.millis = 15 * minute + 1
        refresh(7, WidgetFamily.Small, RefreshTrigger.Scheduled)
        assertEquals(1, engine.calls.size)
    }

    // --- event triggers (calendar changed, ...) ---

    @Test
    fun `an event refresh runs right away even shortly after the last run`() = runTest {
        renderStates.states[7] = RenderState(scriptName = "weather", tree = okTree, updatedAt = 1)
        clock.millis = 1 * minute
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Event)

        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `with the screen off, an event refresh waits for the screen`() = runTest {
        screen.on = false
        renderStates.states[7] = RenderState(scriptName = "weather", tree = okTree, updatedAt = 1)
        clock.millis = 30 * minute

        refresh(7, WidgetFamily.Small, RefreshTrigger.Event)

        assertTrue(engine.calls.isEmpty())
        assertEquals(listOf(7 to 32 * minute), scheduler.refreshAt)
        assertEquals(listOf(false), scheduler.wakeUps)
    }

    // --- screen off (default): nothing runs, the widget catches up when the screen turns on ---

    @Test
    fun `with the screen off, a scheduled refresh waits for the screen without waking the device`() = runTest {
        screen.on = false
        val current = RenderState(scriptName = "weather", tree = okTree.copy(refreshAfter = 20 * minute), updatedAt = 1)
        renderStates.states[7] = current
        clock.millis = 30 * minute

        val state = refresh(7, WidgetFamily.Small, RefreshTrigger.Scheduled)

        assertEquals(current, state)
        assertTrue(engine.calls.isEmpty())
        assertEquals(listOf(7 to 32 * minute), scheduler.refreshAt)
        assertEquals(listOf(false), scheduler.wakeUps)
    }

    @Test
    fun `with the screen off, periodic refreshes don't run`() = runTest {
        screen.on = false
        renderStates.states[7] = RenderState(scriptName = "weather", tree = okTree, updatedAt = 1)
        clock.millis = 60 * minute

        refresh(7, WidgetFamily.Small, RefreshTrigger.Periodic)

        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun `when the screen turns on, an overdue refresh runs right away`() = runTest {
        renderStates.states[7] = RenderState(scriptName = "weather", tree = okTree.copy(refreshAfter = 20 * minute), updatedAt = 1)
        clock.millis = 90 * minute
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Scheduled)

        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `widgets without refreshAfterDate get a catch-up refresh 15 minutes later that doesn't wake the device`() = runTest {
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small)

        assertEquals(listOf(7 to 42L + 15 * minute), scheduler.refreshAt)
        assertEquals(listOf(false), scheduler.wakeUps)
    }

    @Test
    fun `a manual run while the screen is off schedules without waking the device`() = runTest {
        screen.on = false
        engine.result = ScriptRunResult(widget = okTree.copy(refreshAfter = 42L + minute))

        refresh(7, WidgetFamily.Small, RefreshTrigger.Manual)

        assertEquals(1, engine.calls.size)
        assertEquals(listOf(7 to 42L + 5 * minute), scheduler.refreshAt)
        assertEquals(listOf(false), scheduler.wakeUps)
    }

    @Test
    fun `manual refreshes are never held back`() = runTest {
        renderStates.states[7] = RenderState(scriptName = "weather", tree = okTree, updatedAt = 42L)
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Manual)

        assertEquals(1, engine.calls.size)
    }

    // --- offline ---

    @Test
    fun `offline, background runs of scripts that use the network are skipped`() = runTest {
        scripts.files["weather"] = "const r = await new Request(url).loadJSON()"
        val current = RenderState(scriptName = "weather", tree = okTree, updatedAt = 1)
        renderStates.states[7] = current
        clock.millis = 30 * minute
        network.connected = false

        val periodic = refresh(7, WidgetFamily.Small, RefreshTrigger.Periodic)
        val scheduled = refresh(7, WidgetFamily.Small, RefreshTrigger.Scheduled)

        assertEquals(current, periodic)
        assertEquals(current, scheduled)
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun `offline, scripts without network access still refresh`() = runTest {
        scripts.files["weather"] = "const w = new ListWidget(); w.addText(new Date().toString())"
        clock.millis = 30 * minute
        network.connected = false
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Periodic)

        assertEquals(1, engine.calls.size)
    }

    @Test
    fun `offline, a manual refresh still runs`() = runTest {
        scripts.files["weather"] = "await new Request(url).loadString()"
        network.connected = false
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Manual)

        assertEquals(1, engine.calls.size)
    }

    // --- one run at a time per widget ---

    @Test
    fun `overlapping background refreshes of one widget run the script once`() = runTest {
        clock.millis = 30 * minute
        val slow = object : kr.b1ink.scriptwidget.domain.engine.ScriptEngine {
            var calls = 0
            override suspend fun execute(script: String, env: ScriptEnvironment, scriptName: String): ScriptRunResult {
                calls++
                kotlinx.coroutines.delay(1_000)
                return ScriptRunResult(widget = okTree)
            }
        }
        val refreshSlow = RefreshWidgetUseCase(bindings, scripts, RunScriptUseCase(slow), renderStates, scheduler, clock, screen)

        val a = async { refreshSlow(7, WidgetFamily.Small, RefreshTrigger.Scheduled) }
        val b = async { refreshSlow(7, WidgetFamily.Small, RefreshTrigger.Periodic) }
        a.await(); b.await()

        assertEquals(1, slow.calls)
    }

    @Test
    fun `the published state remembers the family it was drawn for`() = runTest {
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Large)

        assertEquals(WidgetFamily.Large, renderStates.states[7]?.family)
    }

    // --- interactive widgets ---

    @Test
    fun `a tapped widget action is passed to the script`() = runTest {
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small, RefreshTrigger.Manual, action = "increment")

        assertEquals("increment", engine.calls.single().env.widgetAction)
    }

    @Test
    fun `regular refreshes have no action`() = runTest {
        engine.result = ScriptRunResult(widget = okTree)

        refresh(7, WidgetFamily.Small)

        assertEquals(null, engine.calls.single().env.widgetAction)
    }
}
