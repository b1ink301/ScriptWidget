package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository

/** Runs a saved script without a screen, e.g. for an automation app. */
class RunScriptByNameUseCase(
    private val scripts: ScriptRepository,
    private val runScript: RunScriptUseCase,
) {
    suspend operator fun invoke(name: String, source: RunSource, parameter: String? = null, trigger: String? = null): ScriptRunResult {
        val code = scripts.read(name) ?: return ScriptRunResult(error = "Script not found: $name")
        return runScript(
            script = code,
            env = ScriptEnvironment(runsInWidget = false, cacheKey = CACHE_KEY, source = source, shortcutParameter = parameter, trigger = trigger),
            scriptName = name,
        )
    }

    private companion object {
        const val CACHE_KEY = "automation"
    }
}
