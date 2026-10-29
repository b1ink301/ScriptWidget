package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.repository.WidgetBindingRepository
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler

class BindWidgetUseCase(
    private val bindings: WidgetBindingRepository,
    private val scheduler: WidgetRefreshScheduler,
) {
    suspend operator fun invoke(appWidgetId: Int, scriptName: String, parameter: String?) {
        bindings.set(WidgetBinding(appWidgetId, scriptName, parameter?.takeIf { it.isNotBlank() }))
        scheduler.refreshNow(appWidgetId)
        scheduler.schedulePeriodic(appWidgetId)
    }
}

class UnbindWidgetsUseCase(
    private val bindings: WidgetBindingRepository,
    private val scheduler: WidgetRefreshScheduler,
    private val cache: kr.b1ink.scriptwidget.domain.repository.WidgetCache = kr.b1ink.scriptwidget.domain.repository.WidgetCache.None,
) {
    suspend operator fun invoke(appWidgetIds: List<Int>) {
        appWidgetIds.forEach {
            scheduler.cancel(it)
            bindings.remove(it)
            cache.clear(it)
        }
    }
}

class GetWidgetBindingUseCase(private val bindings: WidgetBindingRepository) {
    suspend operator fun invoke(appWidgetId: Int): WidgetBinding? = bindings.get(appWidgetId)
}

/**
 * Re-establishes periodic refresh (e.g. on launcher `onUpdate`) for widgets that have a script.
 * Unbound widgets are skipped: they are being configured and get scheduled by [BindWidgetUseCase].
 */
class ScheduleBoundWidgetsUseCase(
    private val bindings: WidgetBindingRepository,
    private val scheduler: WidgetRefreshScheduler,
) {
    suspend operator fun invoke(appWidgetIds: List<Int>) {
        appWidgetIds.filter { bindings.get(it) != null }.forEach(scheduler::schedulePeriodic)
    }
}

/**
 * A widget was resized. Within the same family the drawn tree still fits, so it is only redrawn;
 * a new family (small ↔ medium ↔ large) needs a new run, since scripts lay out per family.
 */
class ResizeWidgetUseCase(
    private val renderStates: kr.b1ink.scriptwidget.domain.repository.RenderStateRepository,
    private val scheduler: WidgetRefreshScheduler,
) {
    suspend operator fun invoke(appWidgetId: Int, family: kr.b1ink.scriptwidget.domain.model.WidgetFamily) {
        val current = renderStates.get(appWidgetId)
        if (current != null && current.family == family) renderStates.publish(appWidgetId, current)
        else scheduler.refreshNow(appWidgetId)
    }
}
