package kr.b1ink.scriptwidget.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.usecase.BindWidgetUseCase
import kr.b1ink.scriptwidget.domain.usecase.GetWidgetBindingUseCase
import kr.b1ink.scriptwidget.domain.usecase.ObserveScriptNamesUseCase

data class WidgetConfigUiState(
    val names: List<String> = emptyList(),
    val selected: String? = null,
    val parameter: String = "",
    val done: Boolean = false,
) {
    val canConfirm: Boolean get() = selected != null && !done
}

class WidgetConfigViewModel(
    private val appWidgetId: Int,
    observeScriptNames: ObserveScriptNamesUseCase,
    seedSamples: suspend () -> Unit,
    getBinding: GetWidgetBindingUseCase,
    private val bindWidget: BindWidgetUseCase,
) : ViewModel() {

    private val form = MutableStateFlow(WidgetConfigUiState())

    val uiState: StateFlow<WidgetConfigUiState> =
        combine(observeScriptNames(), form) { names, f -> f.copy(names = names) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, WidgetConfigUiState())

    init {
        viewModelScope.launch {
            seedSamples()
            getBinding(appWidgetId)?.let { binding ->
                form.update { it.copy(selected = binding.scriptName, parameter = binding.parameter.orEmpty()) }
            }
        }
    }

    fun select(name: String) = form.update { it.copy(selected = name) }

    fun onParameterChange(parameter: String) = form.update { it.copy(parameter = parameter) }

    fun confirm() {
        val state = form.value
        val script = state.selected ?: return
        if (state.done) return
        viewModelScope.launch {
            bindWidget(appWidgetId, script, state.parameter)
            form.update { it.copy(done = true) }
        }
    }
}
