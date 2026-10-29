package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.Serializable

/** One script run, kept for the run history screen. */
@Serializable
data class RunRecord(
    val scriptName: String,
    val source: RunSource,
    val startedAt: Long,
    val durationMs: Long,
    val error: String? = null,
    /** The first [MAX_LOG_LINES] lines, each cut to [MAX_LOG_LENGTH] characters. */
    val logs: List<LogLine> = emptyList(),
    /** Set for widget refreshes. */
    val widgetId: Int? = null,
) {
    companion object {
        const val MAX_LOG_LINES = 50
        const val MAX_LOG_LENGTH = 500
    }
}
