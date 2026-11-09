package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticsReportTest {
    private val now = 1_793_600_000_000L // 2026-11-02T06:13:20Z

    private val info = DiagnosticsInfo(
        appVersion = "1.4.0",
        device = "samsung SM-S921N",
        android = "16 (API 36)",
        launcher = "com.sec.android.app.launcher",
        settings = mapOf("Refresh while screen off" to "off"),
        permissions = mapOf("BatteryOptimization" to "Denied", "Location" to "Granted"),
        widgets = 3,
        scripts = 12,
    )

    @Test
    fun `starts with the app, device and setup`() {
        val text = DiagnosticsReport.text(info, now)

        assertTrue(text.startsWith("# ScriptWidget diagnostics"))
        assertContains(text, "Created: 2026-11-02T06:13:20Z")
        assertContains(text, "App: 1.4.0")
        assertContains(text, "Device: samsung SM-S921N")
        assertContains(text, "Android: 16 (API 36)")
        assertContains(text, "Launcher: com.sec.android.app.launcher")
        assertContains(text, "Widgets: 3, scripts: 12")
        assertContains(text, "- Refresh while screen off: off")
        assertContains(text, "- BatteryOptimization: Denied")
    }

    @Test
    fun `lists crashes newest first with their stack traces`() {
        val crashes = listOf(
            CrashReport(time = now - 60_000, kind = "crash", thread = "main", message = "IllegalStateException: old", stackTrace = "at a.B.c(B.kt:1)"),
            CrashReport(time = now - 1_000, kind = "native crash", message = "signal 11 (SIGSEGV)"),
        )

        val text = DiagnosticsReport.text(info.copy(crashes = crashes), now)

        val newer = text.indexOf("native crash")
        val older = text.indexOf("IllegalStateException: old")
        assertTrue(newer in 0 until older, text)
        assertContains(text, "at a.B.c(B.kt:1)")
        assertContains(text, "thread main")
    }

    @Test
    fun `says so when nothing crashed or failed`() {
        val text = DiagnosticsReport.text(info, now)

        assertContains(text, "## Crashes\nNone")
        assertContains(text, "## Recent failed runs\nNone")
    }

    @Test
    fun `failed runs keep the error and leave out script output`() {
        val runs = listOf(
            RunRecord("weather", RunSource.Widget, startedAt = now - 5_000, durationMs = 1200, error = "TypeError: x is undefined", logs = listOf(LogLine("log", "my home address"))),
            RunRecord("clock", RunSource.App, startedAt = now - 4_000, durationMs = 3),
        )

        val text = DiagnosticsReport.text(info.copy(recentRuns = runs), now)

        assertContains(text, "weather (Widget, 1200 ms): TypeError: x is undefined")
        assertFalse("clock" in text, "successful runs aren't listed")
        assertFalse("my home address" in text, "logs aren't exported")
    }

    @Test
    fun `query strings are hidden, since they often carry API keys`() {
        val runs = listOf(RunRecord("s", RunSource.Widget, now, 1, error = "HTTP 403 for https://api.example.com/v1/data?key=SECRET123&city=Seoul"))

        val text = DiagnosticsReport.text(info.copy(recentRuns = runs), now)

        assertContains(text, "https://api.example.com/v1/data?…")
        assertFalse("SECRET123" in text)
    }

    @Test
    fun `keeps the report short`() {
        val crashes = List(30) { CrashReport(time = now - it, kind = "crash", message = "E$it", stackTrace = List(200) { "at x.Y.z($it)" }.joinToString("\n")) }
        val runs = List(100) { RunRecord("s$it", RunSource.Widget, now - it, 1, error = "boom") }

        val text = DiagnosticsReport.text(info.copy(crashes = crashes, recentRuns = runs), now)

        assertEquals(DiagnosticsReport.MAX_CRASHES, Regex("""^### """, RegexOption.MULTILINE).findAll(text).count())
        assertEquals(DiagnosticsReport.MAX_FAILED_RUNS, text.lines().count { it.startsWith("- ") && "boom" in it })
        assertTrue(text.lines().count { it.startsWith("at x.Y.z") } <= DiagnosticsReport.MAX_CRASHES * DiagnosticsReport.MAX_STACK_LINES)
    }
}
