package kr.b1ink.scriptwidget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Runaway scripts on Android QuickJS: thread stacks are much smaller than on a desktop JVM, so
 * deep recursion has to stop at QuickJS's stack check before the native stack overflows and
 * kills the process (and every widget with it).
 */
@RunWith(AndroidJUnit4::class)
class ResourceLimitsIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "limits-it-${System.nanoTime()}")
    private val keychain = "limits-it-keychain-${System.nanoTime()}"
    private val container = AppContainer(context, rootDir = root, schedulerOverride = FakeRefreshScheduler(), keychainName = keychain)
    private val widget = ScriptEnvironment(runsInWidget = true)

    @After
    fun tearDown() {
        root.deleteRecursively()
        context.deleteSharedPreferences(keychain)
    }

    private suspend fun run(script: String) = container.runScript(script, widget, "limits")

    @Test
    fun unboundedRecursionEndsWithAnError() = runTest {
        assertContains(assertNotNull(run("function f(n) { return f(n + 1) + 1; } f(0);").error), "stack overflow")
    }

    @Test
    fun deepButFiniteRecursionStillWorks() = runTest {
        val result = run(
            "function f(n) { return n === 0 ? 0 : f(n - 1) + 1; }\n" +
                "const w = new ListWidget(); w.addText(String(f(5000))); Script.setWidget(w);",
        )
        assertNull(result.error)
    }

    @Test
    fun recursionThroughNativeCallbacksEndsWithAnError() = runTest {
        // Every level goes JS -> native (sort comparator, JSON reviver) -> JS, which uses more native stack per frame.
        assertContains(assertNotNull(run("function f() { [2, 1].sort(() => { f(); return 0; }); } f();").error), "stack overflow")
        assertContains(assertNotNull(run("function g() { JSON.parse('[1]', () => g()); } g();").error), "stack overflow")
    }

    @Test
    fun allocatingPastTheMemoryLimitEndsWithAnError() = runTest {
        assertContains(assertNotNull(run("const a = []; while (true) a.push(new Array(100000).fill(1));").error), "out of memory")
    }

    @Test
    fun theEngineStillWorksAfterRunawayScripts() = runTest {
        run("function f(n) { return f(n + 1) + 1; } f(0);")
        run("const a = []; while (true) a.push(new Array(100000).fill(1));")

        val result = run("const w = new ListWidget(); w.addText('ok'); Script.setWidget(w);")

        assertNull(result.error)
        assertNotNull(result.widget)
    }
}
