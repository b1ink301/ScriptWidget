package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.EventTrigger
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.domain.model.TriggerAction
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import kr.b1ink.scriptwidget.domain.repository.EventTriggerRepository
import kr.b1ink.scriptwidget.domain.repository.WidgetBindingRepository
import kr.b1ink.scriptwidget.domain.scheduler.EventMonitor
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler

/** Watches exactly the events of the enabled triggers. Called after every change and on app start/boot. */
class SyncEventMonitorUseCase(private val triggers: EventTriggerRepository, private val monitor: EventMonitor) {
    suspend operator fun invoke() = monitor.watch(triggers.all().filter { it.enabled }.map { it.event }.toSet())
}

class AddEventTriggerUseCase(
    private val triggers: EventTriggerRepository,
    private val monitor: EventMonitor,
    private val newId: () -> String = { java.util.UUID.randomUUID().toString() },
) {
    suspend operator fun invoke(event: TriggerEvent, scriptName: String, action: TriggerAction): EventTrigger {
        val trigger = EventTrigger(newId(), event, scriptName, action)
        triggers.save(trigger)
        SyncEventMonitorUseCase(triggers, monitor)()
        return trigger
    }
}

class RemoveEventTriggerUseCase(private val triggers: EventTriggerRepository, private val monitor: EventMonitor) {
    suspend operator fun invoke(id: String) {
        triggers.remove(id)
        SyncEventMonitorUseCase(triggers, monitor)()
    }
}

class SetEventTriggerEnabledUseCase(private val triggers: EventTriggerRepository, private val monitor: EventMonitor) {
    suspend operator fun invoke(id: String, enabled: Boolean) {
        val trigger = triggers.all().firstOrNull { it.id == id } ?: return
        triggers.save(trigger.copy(enabled = enabled))
        SyncEventMonitorUseCase(triggers, monitor)()
    }
}

/** An event happened: refresh or run what its triggers name. */
class HandleEventUseCase(
    private val triggers: EventTriggerRepository,
    private val bindings: WidgetBindingRepository,
    private val scheduler: WidgetRefreshScheduler,
    private val runScript: RunScriptByNameUseCase,
) {
    suspend operator fun invoke(event: TriggerEvent) {
        val matching = triggers.all().filter { it.enabled && it.event == event }
        matching.filter { it.action == TriggerAction.RefreshWidgets }
            .flatMap { bindings.widgetsUsing(it.scriptName) }
            .distinct()
            .forEach(scheduler::refreshForEvent)
        matching.filter { it.action == TriggerAction.RunScript }
            .map { it.scriptName }
            .distinct()
            .forEach { runScript(it, RunSource.Trigger, trigger = event.key) }
    }
}
