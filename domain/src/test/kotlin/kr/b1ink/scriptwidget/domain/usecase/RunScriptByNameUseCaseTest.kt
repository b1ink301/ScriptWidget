package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeScriptEngine
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunScriptByNameUseCaseTest {
    private val scripts = FakeScriptRepository(mapOf("lights" to "code"))
    private val engine = FakeScriptEngine(ScriptRunResult(shortcutOutput = "\"ok\""))
    private val run = RunScriptByNameUseCase(scripts, RunScriptUseCase(engine))

    @Test
    fun `runs the named script headless with the parameter`() = runTest {
        val result = run("lights", RunSource.Automation, parameter = "on")

        val call = engine.calls.single()
        assertEquals("code", call.script)
        assertEquals("lights", call.scriptName)
        assertEquals(RunSource.Automation, call.env.source)
        assertEquals("on", call.env.shortcutParameter)
        assertFalse(call.env.runsInWidget)
        assertEquals("automation", call.env.cacheKey)
        assertEquals("\"ok\"", result.shortcutOutput)
    }

    @Test
    fun `a missing script is an error, not a crash`() = runTest {
        val result = run("nope", RunSource.Automation)

        assertTrue(engine.calls.isEmpty())
        assertEquals("Script not found: nope", result.error)
    }
}
