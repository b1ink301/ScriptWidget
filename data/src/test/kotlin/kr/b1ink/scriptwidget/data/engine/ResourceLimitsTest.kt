package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A runaway script must end with an error in its own result instead of taking the app process
 * (and every other widget with it) down.
 */
class ResourceLimitsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val engine by lazy {
        QuickJsScriptEngine(Prelude.load(), fakeServices(files = SandboxedFileSystem(tmp.newFolder("sandbox"))), timeoutMillis = 5_000)
    }
    private val appEnv = ScriptEnvironment(runsInWidget = false)

    private suspend fun run(script: String) = engine.execute(script, appEnv, "test")

    @Test
    fun `allocating past the memory limit ends the run with an error`() = runTest {
        val result = run("const a = []; while (true) a.push(new Array(100000).fill(1));")
        assertContains(assertNotNull(result.error), "out of memory")
    }

    @Test
    fun `unbounded recursion ends the run with an error`() = runTest {
        val result = run("function f(n) { return f(n + 1) + 1; } f(0);")
        assertContains(assertNotNull(result.error), "stack overflow")
    }

    @Test
    fun `deep but finite recursion still works`() = runTest {
        assertNull(run("function f(n) { return n === 0 ? 0 : f(n - 1) + 1; } if (f(5000) !== 5000) throw new Error('bad');").error)
    }

    @Test
    fun `the engine still works after a run hit the memory limit`() = runTest {
        run("const a = []; while (true) a.push(new Array(100000).fill(1));")
        val result = run("const w = new ListWidget(); w.addText('ok'); Script.setWidget(w);")
        assertNull(result.error)
        assertNotNull(result.widget)
    }

    @Test
    fun `a log flood keeps only a bounded number of lines`() = runTest {
        val result = run("for (let i = 0; i < 50000; i++) console.log('line ' + i);")
        assertNull(result.error)
        assertTrue(result.logs.size <= QuickJsScriptEngine.MAX_LOG_LINES + 1, "kept ${result.logs.size} lines")
        // The newest lines are the useful ones for debugging, and the cut is announced.
        assertEquals("line 49999", result.logs.last().message)
        assertTrue(result.logs.any { "omitted" in it.message }, "no truncation notice")
    }

    @Test
    fun `a huge log line is shortened`() = runTest {
        val result = run("console.log('x'.repeat(5 * 1024 * 1024));")
        assertNull(result.error)
        val line = result.logs.single().message
        assertTrue(line.length <= QuickJsScriptEngine.MAX_LOG_LINE_CHARS + 64, "line has ${line.length} chars")
    }
}
