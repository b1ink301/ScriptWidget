package kr.b1ink.scriptwidget.ui.triggers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.EventTrigger
import kr.b1ink.scriptwidget.domain.model.TriggerAction
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import kr.b1ink.scriptwidget.domain.repository.EventTriggerRepository
import kr.b1ink.scriptwidget.domain.scheduler.EventMonitor
import kr.b1ink.scriptwidget.domain.usecase.AddEventTriggerUseCase
import kr.b1ink.scriptwidget.domain.usecase.RemoveEventTriggerUseCase
import kr.b1ink.scriptwidget.domain.usecase.SetEventTriggerEnabledUseCase

data class TriggersUiState(val triggers: List<EventTrigger> = emptyList(), val scripts: List<String> = emptyList())

class TriggersViewModel(
    repository: EventTriggerRepository,
    monitor: EventMonitor,
    scriptNames: Flow<List<String>>,
    newId: () -> String = { java.util.UUID.randomUUID().toString() },
) : ViewModel() {
    private val addTrigger = AddEventTriggerUseCase(repository, monitor, newId)
    private val removeTrigger = RemoveEventTriggerUseCase(repository, monitor)
    private val setTriggerEnabled = SetEventTriggerEnabledUseCase(repository, monitor)

    val state: StateFlow<TriggersUiState> = combine(repository.triggers, scriptNames, ::TriggersUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, TriggersUiState())

    fun add(event: TriggerEvent, scriptName: String, action: TriggerAction) {
        viewModelScope.launch { addTrigger(event, scriptName, action) }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { setTriggerEnabled(id, enabled) }
    }

    fun remove(id: String) {
        viewModelScope.launch { removeTrigger(id) }
    }
}
