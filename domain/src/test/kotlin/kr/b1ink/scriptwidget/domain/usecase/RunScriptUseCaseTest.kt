package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeScriptEngine
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunScriptUseCaseTest {
    private val engine = FakeScriptEngine()
    private val runScript = RunScriptUseCase(engine)

    @Test
    fun `passes script, environment and name to the engine and returns its result`() = runTest {
        val widget = WidgetRoot(children = listOf(TextNode("hi")))
        engine.result = ScriptRunResult(widget = widget)
        val env = ScriptEnvironment(runsInWidget = false)

        val result = runScript("code", env, "name")

        assertEquals(widget, result.widget)
        assertNull(result.error)
        assertEquals(FakeScriptEngine.Call("code", env, "name"), engine.calls.single())
    }

    @Test
    fun `in a widget, finishing without setWidget is an error`() = runTest {
        engine.result = ScriptRunResult(widget = null)

        val result = runScript("code", ScriptEnvironment(runsInWidget = true), "name")

        assertEquals(RunScriptUseCase.MISSING_WIDGET_ERROR, result.error)
    }

    @Test
    fun `in the app, finishing without setWidget is fine`() = runTest {
        engine.result = ScriptRunResult(widget = null)

        val result = runScript("code", ScriptEnvironment(runsInWidget = false), "name")

        assertNull(result.error)
    }

    @Test
    fun `engine errors are kept as they are`() = runTest {
        engine.result = ScriptRunResult(error = "ReferenceError: x is not defined")

        val result = runScript("code", ScriptEnvironment(runsInWidget = true), "name")

        assertEquals("ReferenceError: x is not defined", result.error)
    }

    // --- history ---

    @Test
    fun `every run is recorded with its outcome`() = runTest {
        val history = kr.b1ink.scriptwidget.domain.fake.FakeRunHistory()
        val clock = kr.b1ink.scriptwidget.domain.fake.FixedClock(10_000L)
        engine.handler = { _, _ ->
            clock.millis += 250
            ScriptRunResult(error = "boom", logs = listOf(kr.b1ink.scriptwidget.domain.model.LogLine("log", "a")))
        }

        RunScriptUseCase(engine, history, clock)("code", ScriptEnvironment(runsInWidget = true, cacheKey = "widget-7"), "Weather")

        val record = history.records.value.single()
        assertEquals("Weather", record.scriptName)
        assertEquals(kr.b1ink.scriptwidget.domain.model.RunSource.Widget, record.source)
        assertEquals(10_000L, record.startedAt)
        assertEquals(250L, record.durationMs)
        assertEquals("boom", record.error)
        assertEquals(7, record.widgetId)
        assertEquals(listOf("a"), record.logs.map { it.message })
    }

    @Test
    fun `long logs are trimmed in the history`() = runTest {
        val history = kr.b1ink.scriptwidget.domain.fake.FakeRunHistory()
        engine.result = ScriptRunResult(logs = List(500) { kr.b1ink.scriptwidget.domain.model.LogLine("log", "x".repeat(1_000)) })

        val result = RunScriptUseCase(engine, history)("code", ScriptEnvironment(runsInWidget = false), "n")

        assertEquals(500, result.logs.size) // the caller still gets everything
        val kept = history.records.value.single().logs
        assertEquals(kr.b1ink.scriptwidget.domain.model.RunRecord.MAX_LOG_LINES, kept.size)
        kotlin.test.assertTrue(kept.all { it.message.length <= kr.b1ink.scriptwidget.domain.model.RunRecord.MAX_LOG_LENGTH })
        assertNull(history.records.value.single().widgetId)
    }
}
