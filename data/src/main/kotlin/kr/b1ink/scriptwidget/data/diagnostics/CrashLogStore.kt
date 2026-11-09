package kr.b1ink.scriptwidget.data.diagnostics

import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.domain.model.CrashReport
import java.io.File

/**
 * Crashes written when they happen (the process is about to die, so it's a plain synchronous
 * file write) and read back for the diagnostics report. Only the latest [max] are kept.
 */
class CrashLogStore(private val dir: File, private val max: Int = MAX_CRASHES) {

    @Synchronized
    fun record(report: CrashReport) {
        dir.mkdirs()
        File(dir, "${report.time}.json").writeText(json.encodeToString(CrashReport.serializer(), report))
        files().drop(max).forEach { it.delete() }
    }

    fun all(): List<CrashReport> =
        files().mapNotNull { file -> runCatching { json.decodeFromString(CrashReport.serializer(), file.readText()) }.getOrNull() }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Newest first; names are the crash time. */
    private fun files(): List<File> =
        dir.listFiles { f -> f.extension == "json" }.orEmpty().sortedByDescending { it.nameWithoutExtension.toLongOrNull() ?: 0 }

    companion object {
        const val MAX_CRASHES = 10
        private val json = Json { ignoreUnknownKeys = true }

        fun reportOf(thread: Thread, error: Throwable, now: Long): CrashReport = CrashReport(
            time = now,
            kind = "crash",
            thread = thread.name,
            message = "${error::class.java.simpleName}: ${error.message}",
            stackTrace = error.stackTraceToString().lines().drop(1).joinToString("\n") { it.trim() },
        )
    }
}
