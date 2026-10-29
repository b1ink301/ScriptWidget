package kr.b1ink.scriptwidget.ui.api

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.data.api.ApiEntry

data class ApiReferenceUiState(
    val query: String = "",
    val entries: List<ApiEntry> = emptyList(),
    val selected: ApiEntry? = null,
    val loading: Boolean = true,
)

/** The in-app API reference: searchable list of the script API, with each class's members. */
class ApiReferenceViewModel(load: suspend () -> List<ApiEntry>) : ViewModel() {
    private var all: List<ApiEntry> = emptyList()
    private val _uiState = MutableStateFlow(ApiReferenceUiState())
    val uiState: StateFlow<ApiReferenceUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            all = load().sortedBy { it.name.lowercase() }
            _uiState.update { it.copy(entries = filter(it.query), loading = false) }
        }
    }

    fun search(query: String) = _uiState.update { it.copy(query = query, entries = filter(query)) }

    fun select(name: String?) = _uiState.update { state -> state.copy(selected = all.firstOrNull { it.name == name }) }

    private fun filter(query: String): List<ApiEntry> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter { entry ->
            entry.name.contains(q, ignoreCase = true) ||
                entry.summary.contains(q, ignoreCase = true) ||
                entry.members.any { it.name.contains(q, ignoreCase = true) }
        }
    }
}
