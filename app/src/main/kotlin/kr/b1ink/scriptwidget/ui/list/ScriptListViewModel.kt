package kr.b1ink.scriptwidget.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.ImportOutcome
import kr.b1ink.scriptwidget.domain.usecase.CreateScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.DeleteScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ExportScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ImportScriptsUseCase
import kr.b1ink.scriptwidget.domain.model.ScriptActivity
import kr.b1ink.scriptwidget.domain.model.ScriptAppearance
import kr.b1ink.scriptwidget.domain.model.ScriptSummary
import kr.b1ink.scriptwidget.domain.usecase.BackupScriptsUseCase
import kr.b1ink.scriptwidget.domain.usecase.ObserveScriptSummariesUseCase
import kr.b1ink.scriptwidget.domain.usecase.RestoreScriptsUseCase
import kr.b1ink.scriptwidget.domain.usecase.SetScriptAppearanceUseCase
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.ui.text.UiText

/** A list row: the script and "2 widgets · ran 3 min ago". */
data class ScriptRow(val summary: ScriptSummary, val subtitle: UiText)

data class ScriptListUiState(
    val scripts: List<ScriptSummary> = emptyList(),
    val pendingDelete: String? = null,
    val query: String = "",
    val activity: Map<String, ScriptActivity> = emptyMap(),
    val now: Long = 0,
) {
    val names: List<String> get() = scripts.map { it.name }

    val visible: List<ScriptRow>
        get() = scripts
            .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
            .map { ScriptRow(it, subtitle(activity[it.name] ?: ScriptActivity())) }

    private fun subtitle(a: ScriptActivity): UiText {
        val widgets = when (a.widgets) {
            0 -> UiText.res(R.string.list_widgets_none)
            else -> UiText.plural(R.plurals.list_widgets, a.widgets)
        }
        val ran = a.lastRunAt?.let { UiText.res(R.string.list_ran, ago(now - it)) } ?: return widgets
        return UiText.res(R.string.list_subtitle_joined, widgets, ran)
    }

    private fun ago(ms: Long): UiText {
        val minutes = (ms / 60_000).coerceAtLeast(0).toInt()
        return when {
            minutes < 1 -> UiText.res(R.string.list_ago_just_now)
            minutes < 60 -> UiText.res(R.string.list_ago_minutes, minutes)
            minutes < 24 * 60 -> UiText.res(R.string.list_ago_hours, minutes / 60)
            else -> UiText.res(R.string.list_ago_days, minutes / (24 * 60))
        }
    }
}

sealed interface ScriptListEvent {
    data class OpenEditor(val name: String) : ScriptListEvent
    /** A message of one or more [lines], shown one per line. */
    data class ShowMessage(val lines: List<UiText>) : ScriptListEvent {
        constructor(line: UiText) : this(listOf(line))
    }

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
    activity: Flow<Map<String, ScriptActivity>> = kotlinx.coroutines.flow.flowOf(emptyMap()),
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val pendingDelete = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val _events = Channel<ScriptListEvent>(Channel.BUFFERED)
    val events: Flow<ScriptListEvent> = _events.receiveAsFlow()

    val uiState: StateFlow<ScriptListUiState> =
        combine(summaries(), pendingDelete, query, activity.onStart { emit(emptyMap()) }) { scripts, pending, q, act ->
            ScriptListUiState(scripts, pending, q, act, now())
        }
            .stateIn(viewModelScope, SharingStarted.Eagerly, ScriptListUiState())

    init {
        viewModelScope.launch { seedSamples() }
    }

    fun search(text: String) {
        query.value = text
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
                if (imported.isNotEmpty()) add(UiText.res(R.string.list_imported, imported.size, imported.joinToString { it.name }))
                rejected.forEach { add(UiText.res(R.string.list_import_failed, it.source, it.reason.label())) }
            }
            _events.send(ScriptListEvent.ShowMessage(lines))
        }
    }

    fun requestExport(name: String) {
        viewModelScope.launch { _events.send(ScriptListEvent.ChooseExportTarget(name, ExportScriptUseCase.fileNameFor(name))) }
    }

    fun exportTo(name: String, uri: String) {
        viewModelScope.launch {
            val text = try {
                if (exportScript(name, uri)) UiText.res(R.string.list_exported, name) else UiText.res(R.string.list_script_not_found, name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                UiText.res(R.string.list_export_failed, e.message.toString())
            }
            _events.send(ScriptListEvent.ShowMessage(text))
        }
    }

    private fun ImportOutcome.Reason.label() = when (this) {
        ImportOutcome.Reason.NotAScript -> UiText.res(R.string.list_reason_not_a_script)
        ImportOutcome.Reason.Unreadable -> UiText.res(R.string.list_reason_unreadable)
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
                UiText.plural(R.plurals.list_backed_up, backupScripts(uri))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                UiText.res(R.string.list_backup_failed, e.message.toString())
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
                    add(
                        if (result.added.isEmpty()) {
                            UiText.res(R.string.list_restored_none)
                        } else {
                            UiText.res(R.string.list_restored, result.added.size, result.added.joinToString())
                        },
                    )
                    if (result.unchanged.isNotEmpty()) add(UiText.plural(R.plurals.list_restore_skipped, result.unchanged.size))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                listOf(UiText.res(R.string.list_restore_failed, e.message.toString()))
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
