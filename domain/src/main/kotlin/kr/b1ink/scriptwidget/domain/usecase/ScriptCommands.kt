package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.ScriptCommandResult
import kr.b1ink.scriptwidget.domain.model.ScriptName
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository
import kr.b1ink.scriptwidget.domain.repository.WidgetBindingRepository
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler

class CreateScriptUseCase(private val scripts: ScriptRepository) {

    suspend operator fun invoke(): String {
        val existing = scripts.list().toSet()
        val name = generateSequence(1) { it + 1 }.map { "script_$it" }.first { it !in existing }
        scripts.write(name, TEMPLATE)
        return name
    }

    companion object {
        val TEMPLATE = """
            const w = new ListWidget();
            w.backgroundColor = new Color("#1C1C1E");

            const t = w.addText("New script");
            t.font = Font.boldSystemFont(18);
            t.textColor = Color.white();

            Script.setWidget(w);
        """.trimIndent() + "\n"
    }
}

class GetScriptUseCase(private val scripts: ScriptRepository) {
    suspend operator fun invoke(name: String): String? = scripts.read(name)
}

/** Saves a script and re-runs every widget that uses it. */
class SaveScriptUseCase(
    private val scripts: ScriptRepository,
    private val bindings: WidgetBindingRepository,
    private val scheduler: WidgetRefreshScheduler,
) {
    suspend operator fun invoke(name: String, content: String): ScriptCommandResult {
        if (!ScriptName.isValid(name)) return ScriptCommandResult.InvalidName
        scripts.write(name, content)
        bindings.widgetsUsing(name).forEach(scheduler::refreshNow)
        return ScriptCommandResult.Success(name)
    }
}

/** Renames a script and moves its widgets along with it. */
class RenameScriptUseCase(
    private val scripts: ScriptRepository,
    private val bindings: WidgetBindingRepository,
    private val scheduler: WidgetRefreshScheduler,
) {
    suspend operator fun invoke(from: String, to: String): ScriptCommandResult {
        if (!ScriptName.isValid(to)) return ScriptCommandResult.InvalidName
        if (!scripts.exists(from)) return ScriptCommandResult.NotFound
        if (from == to) return ScriptCommandResult.Success(to)
        if (scripts.exists(to)) return ScriptCommandResult.AlreadyExists
        if (!scripts.rename(from, to)) return ScriptCommandResult.AlreadyExists

        bindings.widgetsUsing(from).forEach { id ->
            bindings.get(id)?.let { bindings.set(it.copy(scriptName = to)) }
            scheduler.refreshNow(id)
        }
        return ScriptCommandResult.Success(to)
    }
}

/** Deletes a script; widgets using it are refreshed so they show the missing-script error. */
class DeleteScriptUseCase(
    private val scripts: ScriptRepository,
    private val bindings: WidgetBindingRepository,
    private val scheduler: WidgetRefreshScheduler,
) {
    suspend operator fun invoke(name: String) {
        scripts.delete(name)
        bindings.widgetsUsing(name).forEach(scheduler::refreshNow)
    }
}
