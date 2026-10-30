package kr.b1ink.scriptwidget.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.RunRecord
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.domain.repository.RunHistoryRepository

data class RunRow(
    val key: String,
    val scriptName: String,
    val success: Boolean,
    val sourceLabel: String,
    /** First error line, or the last log line. */
    val summary: String,
    val duration: String,
    val startedAt: Long,
    /** Full error and logs, shown when the row is expanded. */
    val details: String,
)

data class RunHistoryUiState(val rows: List<RunRow> = emptyList(), val failuresOnly: Boolean = false)

class RunHistoryViewModel(private val history: RunHistoryRepository) : ViewModel() {
    private val failuresOnly = MutableStateFlow(false)

    val uiState: StateFlow<RunHistoryUiState> =
        combine(history.history, failuresOnly) { records, onlyFailures ->
            RunHistoryUiState(
                rows = records.filter { !onlyFailures || it.error != null }.mapIndexed(::row),
                failuresOnly = onlyFailures,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RunHistoryUiState())

    fun setFailuresOnly(enabled: Boolean) {
        failuresOnly.value = enabled
    }

    fun clear() {
        viewModelScope.launch { history.clear() }
    }

    private fun row(index: Int, record: RunRecord) = RunRow(
        key = "${record.startedAt}-$index",
        scriptName = record.scriptName,
        success = record.error == null,
        sourceLabel = sourceLabel(record),
        summary = record.error?.lineSequence()?.first()?.trim()
            ?: record.logs.lastOrNull()?.message?.lineSequence()?.first()
            ?: "성공",
        duration = if (record.durationMs < 1_000) "${record.durationMs}ms" else "%.1fs".format(record.durationMs / 1_000.0),
        startedAt = record.startedAt,
        details = buildString {
            record.error?.let { appendLine(it) }
            record.logs.forEach { appendLine("[${it.level}] ${it.message}") }
        }.trim(),
    )

    private fun sourceLabel(record: RunRecord) = when (record.source) {
        RunSource.Widget -> record.widgetId?.let { "위젯 #$it" } ?: "위젯"
        RunSource.App -> "앱"
        RunSource.ShareSheet -> "공유"
        RunSource.Notification -> "알림"
        RunSource.HomeScreen -> "홈 화면"
        RunSource.Automation -> "자동화"
        RunSource.Trigger -> "트리거"
    }
}
