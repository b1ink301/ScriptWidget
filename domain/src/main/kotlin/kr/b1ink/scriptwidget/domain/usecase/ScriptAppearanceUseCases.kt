package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kr.b1ink.scriptwidget.domain.model.ScriptAppearance
import kr.b1ink.scriptwidget.domain.model.ScriptSummary
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository

/** Script names with their icon; [refresh] after changing a script's appearance. */
class ObserveScriptSummariesUseCase(private val scripts: ScriptRepository) {
    private val version = MutableStateFlow(0)

    operator fun invoke(): Flow<List<ScriptSummary>> =
        combine(scripts.scriptNames, version) { names, _ -> names }
            .map { names -> names.map { ScriptSummary(it, appearanceOf(it)) } }

    fun refresh() {
        version.value++
    }

    private suspend fun appearanceOf(name: String) = scripts.read(name)?.let(ScriptAppearance::parse)
}

class SetScriptAppearanceUseCase(private val scripts: ScriptRepository) {
    suspend operator fun invoke(name: String, appearance: ScriptAppearance) {
        val code = scripts.read(name) ?: return
        scripts.write(name, appearance.applyTo(code))
    }
}

/** Per script: how many widgets use it and when it last ran (updated as scripts run). */
class ObserveScriptActivityUseCase(
    private val scripts: ScriptRepository,
    private val bindings: kr.b1ink.scriptwidget.domain.repository.WidgetBindingRepository,
    private val history: kr.b1ink.scriptwidget.domain.repository.RunHistoryRepository,
) {
    operator fun invoke(): Flow<Map<String, kr.b1ink.scriptwidget.domain.model.ScriptActivity>> =
        combine(scripts.scriptNames, history.history) { names, records ->
            val lastRun = records.groupBy { it.scriptName }.mapValues { (_, runs) -> runs.maxOf { it.startedAt } }
            names.associateWith { kr.b1ink.scriptwidget.domain.model.ScriptActivity(bindings.widgetsUsing(it).size, lastRun[it]) }
        }
}
