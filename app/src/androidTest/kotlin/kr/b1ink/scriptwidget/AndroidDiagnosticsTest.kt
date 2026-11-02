package kr.b1ink.scriptwidget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.diagnostics.CrashLogStore
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.diagnostics.AndroidDiagnostics
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.model.DiagnosticsReport
import kr.b1ink.scriptwidget.domain.model.RunRecord
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.permissions.AppPermission
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class AndroidDiagnosticsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "diagnostics-it-${System.nanoTime()}")
    private val keychain = "diagnostics-it-keychain-${System.nanoTime()}"
    private val container = AppContainer(context, rootDir = root, schedulerOverride = FakeRefreshScheduler(), keychainName = keychain)

    @After
    fun tearDown() {
        root.deleteRecursively()
        context.deleteSharedPreferences(keychain)
    }

    @Test
    fun collectsTheDeviceSetupAndTheAppsRecords() = runTest {
        container.crashLog.record(CrashLogStore.reportOf(Thread.currentThread(), IllegalStateException("boom"), now = 1_000))
        container.runHistory.add(RunRecord("weather", RunSource.Widget, startedAt = 2_000, durationMs = 5, error = "HTTP 500 for https://a.b/c?key=SECRET"))

        val info = AndroidDiagnostics(context, container).collect("9.9.9")

        assertEquals("9.9.9", info.appVersion)
        assertContains(info.android, "API ${android.os.Build.VERSION.SDK_INT}")
        assertNotNull(info.launcher, "the home app is visible through <queries>")
        assertEquals(AppPermission.entries.map { it.name }.toSet(), info.permissions.keys)
        assertEquals("IllegalStateException: boom", info.crashes.first { it.kind == "crash" }.message)

        val text = DiagnosticsReport.text(info, System.currentTimeMillis())
        assertContains(text, "weather (Widget, 5 ms): HTTP 500 for https://a.b/c?…")
        assertFalse("SECRET" in text)
    }

    @Test
    fun theAppRecordsUncaughtExceptions() {
        // ScriptWidgetApp wraps the platform handler; calling it would kill the test process, so only check it's there.
        assertNotNull(Thread.getDefaultUncaughtExceptionHandler())
        assertContains(Thread.getDefaultUncaughtExceptionHandler()!!::class.java.name, "ScriptWidgetApp")
    }
}
