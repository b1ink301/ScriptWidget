package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.flow.StateFlow
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository

class ObserveScriptNamesUseCase(private val scripts: ScriptRepository) {
    operator fun invoke(): StateFlow<List<String>> = scripts.scriptNames
}
