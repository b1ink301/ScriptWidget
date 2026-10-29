package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.engine.ScriptEngine
import kr.b1ink.scriptwidget.domain.model.LogLine
import kr.b1ink.scriptwidget.domain.model.RunRecord
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.repository.RunHistoryRepository
import kr.b1ink.scriptwidget.domain.time.Clock

/** Runs a script (app, widget, automation, …) and records the run in [history]. */
class RunScriptUseCase(
    private val engine: ScriptEngine,
    private val history: RunHistoryRepository? = null,
    private val clock: Clock = Clock.System,
) {

    suspend operator fun invoke(script: String, env: ScriptEnvironment, scriptName: String): ScriptRunResult {
        val startedAt = clock.now()
        val raw = engine.execute(script, env, scriptName)
        val result = if (env.runsInWidget && raw.isSuccess && raw.widget == null) {
            raw.copy(error = MISSING_WIDGET_ERROR)
        } else {
            raw
        }
        history?.add(
            RunRecord(
                scriptName = scriptName,
                source = env.source,
                startedAt = startedAt,
                durationMs = clock.now() - startedAt,
                error = result.error,
                logs = result.logs.take(RunRecord.MAX_LOG_LINES).map { LogLine(it.level, it.message.take(RunRecord.MAX_LOG_LENGTH)) },
                widgetId = env.cacheKey.removePrefix("widget-").takeIf { env.cacheKey.startsWith("widget-") }?.toIntOrNull(),
            )
        )
        return result
    }

    companion object {
        const val MISSING_WIDGET_ERROR = "Script finished without calling Script.setWidget()"
    }
}
