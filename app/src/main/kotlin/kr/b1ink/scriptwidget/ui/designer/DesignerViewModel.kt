package kr.b1ink.scriptwidget.ui.designer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.BookmarkResult
import kr.b1ink.scriptwidget.domain.model.DesignElement
import kr.b1ink.scriptwidget.domain.model.DesignImage
import kr.b1ink.scriptwidget.domain.model.DesignRead
import kr.b1ink.scriptwidget.domain.model.ScriptCommandResult
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.WidgetDesign
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.repository.DesignScriptGenerator
import kr.b1ink.scriptwidget.domain.repository.FileBookmarkRepository
import kr.b1ink.scriptwidget.domain.usecase.AddFileBookmarkUseCase
import kr.b1ink.scriptwidget.domain.usecase.OpenDesignUseCase
import kr.b1ink.scriptwidget.domain.usecase.RunScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.SaveDesignUseCase

data class DesignerUiState(
    val name: String,
    val loaded: Boolean = false,
    val design: WidgetDesign = WidgetDesign(),
    /** Path of the selected element; null = the widget itself. */
    val selected: List<Int>? = null,
    val family: WidgetFamily = WidgetFamily.Small,
    val preview: ScriptRunResult? = null,
    val running: Boolean = false,
    val dirty: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    /** The code was changed by hand after it was generated; saving here replaces those changes. */
    val editedByHand: Boolean = false,
    /** Not made in the designer at all. */
    val notDesigned: Boolean = false,
    /** File bookmark names, for pictures. */
    val bookmarks: List<String> = emptyList(),
)

/** Where a newly picked picture goes. */
enum class PictureTarget { SelectedImage, Background }

/**
 * Edits a [WidgetDesign]: every change regenerates the script and runs it in the real engine, so the
 * preview is exactly what the widget will show (data blocks included).
 */
class DesignerViewModel(
    name: String,
    private val openDesign: OpenDesignUseCase,
    private val saveDesign: SaveDesignUseCase,
    private val generator: DesignScriptGenerator,
    private val runScript: RunScriptUseCase,
    private val previewDelayMs: Long = 400,
    bookmarks: FileBookmarkRepository? = null,
    private val addBookmark: AddFileBookmarkUseCase? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(DesignerUiState(name = name))
    val state: StateFlow<DesignerUiState> = _state.asStateFlow()

    private val undoStack = ArrayDeque<WidgetDesign>()
    private val redoStack = ArrayDeque<WidgetDesign>()
    private var previewJob: Job? = null

    /** The element being typed into, so its keystrokes share one undo step. */
    private var lastCoalesceKey: List<Int>? = null

    init {
        viewModelScope.launch {
            when (val read = openDesign(name)) {
                is DesignRead.Designed -> _state.update { it.copy(design = read.design, loaded = true) }
                is DesignRead.EditedByHand -> _state.update { it.copy(design = read.design, loaded = true, editedByHand = true) }
                DesignRead.Plain -> _state.update { it.copy(loaded = true, notDesigned = true) }
            }
            refreshPreview(immediately = true)
        }
        bookmarks?.let { repo ->
            viewModelScope.launch {
                repo.bookmarks.collect { all -> _state.update { it.copy(bookmarks = all.map { b -> b.name }) } }
            }
        }
    }

    /** Saves the picked picture ([uri]) as a new file bookmark and shows it at [target]. */
    fun addPicture(uri: String, target: PictureTarget) {
        val add = addBookmark ?: return
        viewModelScope.launch {
            val saved = add.underFreeName(PICTURE_NAME, uri) as? BookmarkResult.Saved ?: return@launch
            val before = _state.value.design
            when (target) {
                PictureTarget.SelectedImage -> updateSelected { (it as? DesignImage)?.copy(bookmark = saved.name) ?: it }
                PictureTarget.Background -> updateDesign { it.copy(background = it.background.copy(image = saved.name)) }
            }
            // The design may already name this bookmark (a template's "photo"): only the file is new.
            if (_state.value.design == before) refreshPreview(immediately = true)
        }
    }

    fun select(path: List<Int>?) = _state.update { it.copy(selected = path?.takeIf { p -> it.design.elementAt(p) != null }) }

    fun selectFamily(family: WidgetFamily) {
        _state.update { it.copy(family = family) }
        refreshPreview(immediately = true)
    }

    fun add(element: DesignElement) {
        val (design, path) = _state.value.design.add(element, _state.value.selected)
        edit(design, selected = path)
    }

    /** With [coalesce], consecutive changes to the same element (typing) are undone together. */
    fun updateSelected(coalesce: Boolean = false, change: (DesignElement) -> DesignElement) {
        val path = _state.value.selected ?: return
        edit(_state.value.design.update(path, change), selected = path, coalesceKey = if (coalesce) path else null)
    }

    fun updateDesign(change: (WidgetDesign) -> WidgetDesign) = edit(change(_state.value.design), _state.value.selected)

    fun moveSelected(by: Int) {
        val path = _state.value.selected ?: return
        val (design, moved) = _state.value.design.move(path, by)
        edit(design, selected = moved)
    }

    /** Drag and drop in the outline: see [WidgetDesign.moveTo]. The moved element ends up selected. */
    fun moveTo(from: List<Int>, toParent: List<Int>, index: Int) {
        val (design, moved) = _state.value.design.moveTo(from, toParent, index)
        edit(design, selected = moved)
    }

    fun wrapSelected(dir: String) {
        val path = _state.value.selected ?: return
        val (design, wrapped) = _state.value.design.wrapInStack(path, dir)
        edit(design, selected = wrapped)
    }

    fun removeSelected() {
        val path = _state.value.selected ?: return
        edit(_state.value.design.remove(path), selected = null)
    }

    fun undo() {
        lastCoalesceKey = null
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_state.value.design)
        apply(previous)
    }

    fun redo() {
        lastCoalesceKey = null
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_state.value.design)
        apply(next)
    }

    fun save() {
        viewModelScope.launch { saveIfDirty() }
    }

    /** Saves pending edits; called when the designer closes as well. */
    suspend fun saveIfDirty() {
        val state = _state.value
        if (!state.dirty || !state.loaded) return
        if (saveDesign(state.name, state.design) is ScriptCommandResult.Success) {
            _state.update { if (it.design == state.design) it.copy(dirty = false, editedByHand = false, notDesigned = false) else it }
        }
    }

    private fun edit(design: WidgetDesign, selected: List<Int>?, coalesceKey: List<Int>? = null) {
        if (design == _state.value.design) {
            _state.update { it.copy(selected = selected) }
            return
        }
        if (coalesceKey == null || coalesceKey != lastCoalesceKey) {
            undoStack.addLast(_state.value.design)
            if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        }
        lastCoalesceKey = coalesceKey
        redoStack.clear()
        _state.update { it.copy(design = design, selected = selected, dirty = true, canUndo = true, canRedo = false) }
        refreshPreview()
    }

    private fun apply(design: WidgetDesign) {
        _state.update {
            it.copy(
                design = design,
                selected = it.selected?.takeIf { p -> design.elementAt(p) != null },
                dirty = true,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        refreshPreview()
    }

    private fun refreshPreview(immediately: Boolean = false) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (!immediately && previewDelayMs > 0) delay(previewDelayMs)
            val state = _state.value
            _state.update { it.copy(running = true) }
            val result = runScript(
                script = generator.generate(state.design),
                env = ScriptEnvironment(runsInWidget = true, widgetFamily = state.family, cacheKey = PREVIEW_CACHE_KEY),
                scriptName = state.name,
            )
            _state.update { it.copy(running = false, preview = result) }
        }
    }

    companion object {
        const val PREVIEW_CACHE_KEY = "designer-preview"
        private const val PICTURE_NAME = "photo"
        private const val MAX_UNDO = 100
    }
}
