package kr.b1ink.scriptwidget.data.engine

import kr.b1ink.scriptwidget.domain.model.LogLine

/**
 * Keeps the newest [maxLines] log lines of a run, each cut to [maxChars], so a script that logs
 * in a loop can't fill the heap or the run history. Dropped lines are announced at the top.
 */
internal class LogBuffer(private val maxLines: Int, private val maxChars: Int) {
    private val lines = ArrayDeque<LogLine>()
    private var dropped = 0

    @Synchronized
    fun add(level: String, message: String) {
        val text = if (message.length > maxChars) {
            message.take(maxChars) + "… (${message.length - maxChars} more characters)"
        } else {
            message
        }
        if (lines.size == maxLines) {
            lines.removeFirst()
            dropped++
        }
        lines.addLast(LogLine(level, text))
    }

    @Synchronized
    fun lines(): List<LogLine> =
        if (dropped == 0) lines.toList() else listOf(LogLine("warn", "$dropped earlier log lines omitted")) + lines
}
