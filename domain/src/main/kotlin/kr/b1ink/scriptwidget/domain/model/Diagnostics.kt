package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.Serializable
import java.time.Instant

/** The app went down: an uncaught exception, or what Android recorded (native crash, ANR, ...). */
@Serializable
data class CrashReport(
    /** Epoch millis. */
    val time: Long,
    /** "crash", "native crash", "ANR", ... */
    val kind: String,
    val thread: String? = null,
    val message: String,
    val stackTrace: String = "",
)

/** What a bug report needs about this install; collected by the app, written out by [DiagnosticsReport]. */
data class DiagnosticsInfo(
    val appVersion: String,
    val device: String,
    val android: String,
    /** The home app's package; widgets look and refresh differently per launcher. */
    val launcher: String?,
    val settings: Map<String, String> = emptyMap(),
    val permissions: Map<String, String> = emptyMap(),
    val widgets: Int = 0,
    val scripts: Int = 0,
    val crashes: List<CrashReport> = emptyList(),
    val recentRuns: List<RunRecord> = emptyList(),
)

/**
 * A plain-text report to attach to a GitHub issue. It leaves out what may be personal: script
 * code and logs aren't included, and URL query strings (often API keys) are hidden.
 */
object DiagnosticsReport {
    const val MAX_CRASHES = 10
    const val MAX_STACK_LINES = 40
    const val MAX_FAILED_RUNS = 20

    fun text(info: DiagnosticsInfo, now: Long): String = buildString {
        appendLine("# ScriptWidget diagnostics")
        appendLine("Created: ${time(now)}")
        appendLine("App: ${info.appVersion}")
        appendLine("Device: ${info.device}")
        appendLine("Android: ${info.android}")
        appendLine("Launcher: ${info.launcher ?: "unknown"}")
        appendLine("Widgets: ${info.widgets}, scripts: ${info.scripts}")

        appendLine()
        appendLine("## Settings")
        info.settings.forEach { (name, value) -> appendLine("- $name: $value") }
        appendLine()
        appendLine("## Permissions")
        info.permissions.forEach { (name, value) -> appendLine("- $name: $value") }

        appendLine()
        appendLine("## Crashes")
        val crashes = info.crashes.sortedByDescending { it.time }.take(MAX_CRASHES)
        if (crashes.isEmpty()) appendLine("None")
        crashes.forEach { crash ->
            appendLine("### ${time(crash.time)} ${crash.kind}" + (crash.thread?.let { " (thread $it)" } ?: ""))
            appendLine(redact(crash.message))
            crash.stackTrace.lineSequence().filter { it.isNotBlank() }.take(MAX_STACK_LINES).forEach { appendLine(redact(it.trim())) }
        }

        appendLine()
        appendLine("## Recent failed runs")
        val failed = info.recentRuns.filter { it.error != null }.sortedByDescending { it.startedAt }.take(MAX_FAILED_RUNS)
        if (failed.isEmpty()) appendLine("None")
        failed.forEach { run ->
            val error = redact(run.error.orEmpty().lineSequence().first())
            appendLine("- ${time(run.startedAt)} ${run.scriptName} (${run.source}, ${run.durationMs} ms): $error")
        }
    }

    private fun time(millis: Long) = Instant.ofEpochMilli(millis).toString()

    private val QUERY = Regex("""(https?://[^\s?#]+)\?\S*""")

    /** Hides URL query strings, which often carry API keys or locations. */
    fun redact(text: String): String = text.replace(QUERY) { "${it.groupValues[1]}?…" }
}
