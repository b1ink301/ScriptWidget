package kr.b1ink.scriptwidget.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.ImportOutcome
import kr.b1ink.scriptwidget.domain.usecase.CreateScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.DeleteScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ExportScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ImportScriptsUseCase
import kr.b1ink.scriptwidget.domain.model.ScriptAppearance
import kr.b1ink.scriptwidget.domain.model.ScriptSummary
import kr.b1ink.scriptwidget.domain.usecase.BackupScriptsUseCase
import kr.b1ink.scriptwidget.domain.usecase.ObserveScriptSummariesUseCase
import kr.b1ink.scriptwidget.domain.usecase.RestoreScriptsUseCase
import kr.b1ink.scriptwidget.domain.usecase.SetScriptAppearanceUseCase

data class ScriptListUiState(
    val scripts: List<ScriptSummary> = emptyList(),
    val pendingDelete: String? = null,
) {
    val names: List<String> get() = scripts.map { it.name }
}

sealed interface ScriptListEvent {
    data class OpenEditor(val name: String) : ScriptListEvent
    data class ShowMessage(val text: String) : ScriptListEvent

    /** Ask the user where to save [scriptName] (system "create document" picker, suggesting [fileName]). */
    data class ChooseExportTarget(val scriptName: String, val fileName: String) : ScriptListEvent

    /** Ask where to save the backup of every script. */
    data class ChooseBackupTarget(val fileName: String) : ScriptListEvent
}

class ScriptListViewModel(
    seedSamples: suspend () -> Unit,
    private val createScript: CreateScriptUseCase,
    private val deleteScript: DeleteScriptUseCase,
    private val importScripts: ImportScriptsUseCase,
    private val exportScript: ExportScriptUseCase,
    private val summaries: ObserveScriptSummariesUseCase,
    private val setAppearance: SetScriptAppearanceUseCase,
    private val backupScripts: BackupScriptsUseCase,
    private val restoreScripts: RestoreScriptsUseCase,
    /** Today as yyyy-MM-dd, for backup file names. */
    private val today: () -> String = { java.time.LocalDate.now().toString() },
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<String?>(null)
    private val _events = Channel<ScriptListEvent>(Channel.BUFFERED)
    val events: Flow<ScriptListEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ScriptListUiState> =
        combine(summaries(), pendingDelete) { scripts, pending -> ScriptListUiState(scripts, pending) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ScriptListUiState())

    init {
        viewModelScope.launch { seedSamples() }
    }

    fun createScript() {
        viewModelScope.launch { _events.send(ScriptListEvent.OpenEditor(createScript.invoke())) }
    }

    fun requestDelete(name: String) {
        pendingDelete.value = name
    }

    fun cancelDelete() {
        pendingDelete.value = null
    }

    /** Imports picked or shared files. A single imported script opens in the editor. */
    fun importFiles(uris: List<String>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val outcomes = importScripts(uris)
            val imported = outcomes.filterIsInstance<ImportOutcome.Imported>()
            val rejected = outcomes.filterIsInstance<ImportOutcome.Rejected>()
            if (imported.size == 1 && rejected.isEmpty()) {
                _events.send(ScriptListEvent.OpenEditor(imported.single().name))
                return@launch
            }
            val lines = buildList {
                if (imported.isNotEmpty()) add("${imported.size}개를 가져왔습니다: ${imported.joinToString { it.name }}")
                rejected.forEach { add("가져오지 못함: ${it.source} (${it.reason.label()})") }
            }
            _events.send(ScriptListEvent.ShowMessage(lines.joinToString("\n")))
        }
    }

    fun requestExport(name: String) {
        viewModelScope.launch { _events.send(ScriptListEvent.ChooseExportTarget(name, ExportScriptUseCase.fileNameFor(name))) }
    }

    fun exportTo(name: String, uri: String) {
        viewModelScope.launch {
            val text = try {
                if (exportScript(name, uri)) "'$name'을(를) 내보냈습니다." else "'$name' 스크립트를 찾을 수 없습니다."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "내보내기 실패: ${e.message}"
            }
            _events.send(ScriptListEvent.ShowMessage(text))
        }
    }

    private fun ImportOutcome.Reason.label() = when (this) {
        ImportOutcome.Reason.NotAScript -> "스크립트 파일이 아님"
        ImportOutcome.Reason.Unreadable -> "파일을 열 수 없음"
    }

    fun setAppearance(name: String, appearance: ScriptAppearance) {
        viewModelScope.launch {
            setAppearance.invoke(name, appearance)
            summaries.refresh()
        }
    }

    fun requestBackup() {
        viewModelScope.launch { _events.send(ScriptListEvent.ChooseBackupTarget(BackupScriptsUseCase.fileName(today()))) }
    }

    fun backupTo(uri: String) {
        viewModelScope.launch {
            val text = try {
                "${backupScripts(uri)}개 스크립트를 백업했습니다."
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "백업 실패: ${e.message}"
            }
            _events.send(ScriptListEvent.ShowMessage(text))
        }
    }

    fun restoreFrom(uri: String) {
        viewModelScope.launch {
            val text = try {
                val result = restoreScripts(uri)
                summaries.refresh()
                buildList {
                    add("${result.added.size}개를 복원했습니다" + if (result.added.isEmpty()) "." else ": ${result.added.joinToString()}")
                    if (result.unchanged.isNotEmpty()) add("이미 같은 내용이 있어 건너뜀: ${result.unchanged.size}개")
                }.joinToString("\n")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                "복원 실패: ${e.message}"
            }
            _events.send(ScriptListEvent.ShowMessage(text))
        }
    }

    fun confirmDelete() {
        val name = pendingDelete.value ?: return
        pendingDelete.value = null
        viewModelScope.launch { deleteScript(name) }
    }
}
