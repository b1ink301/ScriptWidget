package kr.b1ink.scriptwidget.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.UpdateStatus

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data class Done(val status: UpdateStatus) : UpdateUiState
}

/** Checks GitHub for a newer release: once when Settings opens, and again on tap. */
class UpdateViewModel(private val checkForUpdate: suspend () -> UpdateStatus) : ViewModel() {
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state

    private var checkedOnce = false

    fun checkOnce() {
        if (checkedOnce) return
        check()
    }

    fun check() {
        if (_state.value == UpdateUiState.Checking) return
        checkedOnce = true
        _state.value = UpdateUiState.Checking
        viewModelScope.launch { _state.value = UpdateUiState.Done(checkForUpdate()) }
    }
}
