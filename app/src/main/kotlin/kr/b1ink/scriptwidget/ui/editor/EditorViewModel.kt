package kr.b1ink.scriptwidget.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.ScriptCommandResult
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.usecase.GetScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.RenameScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.RunScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.SaveScriptUseCase

enum class EditorTab { Code, Result }

/** Runtime permissions a script may need, detected from the APIs it uses. */
enum class ScriptPermission(vararg val usageMarkers: String) {
    Location("Location."),
    Notifications("Notification("),
    Calendar("CalendarEvent.", "Calendar.forEvents", "Calendar.defaultForEvents"),
    Contacts("Contact.", "ContactsContainer.", "ContactsGroup."),
    Photos("Photos.latest"),
}

fun interface PermissionChecker {
    fun isGranted(permission: ScriptPermission): Boolean
}

sealed interface EditorEvent {
    /** The script uses APIs that need these permissions; ask the user before running it. */
    data class RequestPermissions(val permissions: Set<ScriptPermission>) : EditorEvent
}

data class EditorUiState(
    val name: String,
    val code: String? = null,
    val dirty: Boolean = false,
    val running: Boolean = false,
    val family: WidgetFamily = WidgetFamily.Small,
    val parameter: String = "",
    val result: ScriptRunResult? = null,
    val tab: EditorTab = EditorTab.Code,
    val message: String? = null,
)

class EditorViewModel(
    initialName: String,
    private val getScript: GetScriptUseCase,
    private val saveScript: SaveScriptUseCase,
    private val renameScript: RenameScriptUseCase,
    private val runScript: RunScriptUseCase,
    private val permissions: PermissionChecker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState(name = initialName))
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    /** Ask at most once per editor session; a denied permission surfaces as a script error. */
    private var permissionsPrompted = false

    /** The run waiting for the permission prompt, resumed by [onPermissionsResult]. */
    private var pendingRequest: RunRequest? = null

    /** Re-used when the preview size changes. */
    private var lastRequest = RunRequest()

    init {
        viewModelScope.launch {
            val code = getScript(initialName).orEmpty()
            _uiState.update { it.copy(code = code) }
        }
    }

    fun onCodeChange(code: String) = _uiState.update { it.copy(code = code, dirty = true) }

    fun onParameterChange(parameter: String) = _uiState.update { it.copy(parameter = parameter) }

    fun selectTab(tab: EditorTab) = _uiState.update { it.copy(tab = tab) }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun selectFamily(family: WidgetFamily) {
        _uiState.update { it.copy(family = family) }
        if (_uiState.value.result != null) run(lastRequest)
    }

    fun save() {
        viewModelScope.launch { saveIfDirty() }
    }

    /** The permission prompt was answered (either way): continue the run that asked for it. */
    fun onPermissionsResult() {
        permissionsPrompted = true
        pendingRequest?.let {
            pendingRequest = null
            run(it)
        }
    }

    fun run(queryParameters: Map<String, String>) = run(RunRequest(queryParameters = queryParameters))

    fun run(request: RunRequest = RunRequest()) {
        lastRequest = request
        viewModelScope.launch {
            // A run can be requested (scriptwidget://run) before the file has been read.
            _uiState.first { it.code != null }
            saveIfDirty()
            val state = _uiState.value
            val code = state.code ?: return@launch
            val missing = ScriptPermission.entries
                .filter { p -> p.usageMarkers.any { it in code } && !permissions.isGranted(p) }
                .toSet()
            if (missing.isNotEmpty() && !permissionsPrompted) {
                pendingRequest = request
                _events.send(EditorEvent.RequestPermissions(missing))
                return@launch
            }
            _uiState.update { it.copy(running = true, tab = EditorTab.Result) }
            val result = runScript(
                script = code,
                env = ScriptEnvironment(
                    runsInWidget = false,
                    widgetFamily = state.family,
                    widgetParameter = state.parameter.ifBlank { null },
                    cacheKey = PREVIEW_CACHE_KEY,
                    queryParameters = request.queryParameters,
                    source = request.source,
                    shareInput = request.shareInput,
                    notification = request.notification,
                    shortcutParameter = request.shortcutParameter,
                ),
                scriptName = state.name,
            )
            _uiState.update { it.copy(running = false, result = result) }
        }
    }

    fun rename(newName: String) {
        viewModelScope.launch {
            saveIfDirty()
            when (val result = renameScript(_uiState.value.name, newName.trim())) {
                is ScriptCommandResult.Success -> _uiState.update { it.copy(name = result.name, message = null) }
                ScriptCommandResult.AlreadyExists -> _uiState.update { it.copy(message = MESSAGE_NAME_TAKEN) }
                ScriptCommandResult.InvalidName -> _uiState.update { it.copy(message = MESSAGE_NAME_INVALID) }
                ScriptCommandResult.NotFound -> _uiState.update { it.copy(message = MESSAGE_NOT_FOUND) }
            }
        }
    }

    /** Saves pending edits; called on exit as well. */
    suspend fun saveIfDirty() {
        val state = _uiState.value
        val code = state.code
        if (!state.dirty || code == null) return
        if (saveScript(state.name, code) is ScriptCommandResult.Success) {
            _uiState.update { if (it.code == code) it.copy(dirty = false) else it }
        }
    }

    companion object {
        const val PREVIEW_CACHE_KEY = "preview"
        const val MESSAGE_NAME_TAKEN = "같은 이름의 스크립트가 이미 있습니다."
        const val MESSAGE_NAME_INVALID = "사용할 수 없는 이름입니다. (/, \\, 앞쪽 . 불가, 최대 64자)"
        const val MESSAGE_NOT_FOUND = "스크립트를 찾을 수 없습니다."
    }
}
