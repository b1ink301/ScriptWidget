package kr.b1ink.scriptwidget.domain.engine

import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult

/** Executes a user script and returns the widget tree it produced. Never throws for script errors. */
interface ScriptEngine {
    suspend fun execute(script: String, env: ScriptEnvironment, scriptName: String): ScriptRunResult
}
