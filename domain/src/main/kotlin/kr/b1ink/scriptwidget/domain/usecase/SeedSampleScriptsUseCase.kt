package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.repository.SampleScriptSource
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository

/** On first launch, fills the empty script list with the bundled samples. */
class SeedSampleScriptsUseCase(
    private val scripts: ScriptRepository,
    private val samples: SampleScriptSource,
) {
    suspend operator fun invoke() {
        if (scripts.list().isNotEmpty()) return
        samples.load().forEach { (name, content) -> scripts.write(name, content) }
    }
}
