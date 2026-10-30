package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.DesignHeader
import kr.b1ink.scriptwidget.domain.model.DesignRead
import kr.b1ink.scriptwidget.domain.model.ScriptCommandResult
import kr.b1ink.scriptwidget.domain.model.WidgetDesign
import kr.b1ink.scriptwidget.domain.repository.DesignScriptGenerator
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository

/** Creates `design_N` from [WidgetDesign] (usually a template) and returns its name. */
class CreateDesignedScriptUseCase(
    private val scripts: ScriptRepository,
    private val generator: DesignScriptGenerator,
) {
    suspend operator fun invoke(design: WidgetDesign): String {
        val existing = scripts.list().toSet()
        val name = generateSequence(1) { it + 1 }.map { "design_$it" }.first { it !in existing }
        scripts.write(name, DesignHeader.attach(design, generator.generate(design)))
        return name
    }
}

/** Regenerates the script from [WidgetDesign], keeping its other header comments (icon). */
class SaveDesignUseCase(
    private val saveScript: SaveScriptUseCase,
    private val scripts: ScriptRepository,
    private val generator: DesignScriptGenerator,
) {
    suspend operator fun invoke(name: String, design: WidgetDesign): ScriptCommandResult =
        saveScript(name, DesignHeader.attach(design, generator.generate(design), previous = scripts.read(name)))
}

class OpenDesignUseCase(private val scripts: ScriptRepository) {
    suspend operator fun invoke(name: String): DesignRead = scripts.read(name)?.let(DesignHeader::read) ?: DesignRead.Plain
}
