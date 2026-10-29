package kr.b1ink.scriptwidget.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.AddUrlResult
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kr.b1ink.scriptwidget.domain.model.GallerySection
import kr.b1ink.scriptwidget.domain.model.UpdateResult
import kr.b1ink.scriptwidget.domain.repository.CatalogSource
import kr.b1ink.scriptwidget.domain.repository.CatalogUrlRepository
import kr.b1ink.scriptwidget.domain.repository.InstalledScriptRepository
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository
import kr.b1ink.scriptwidget.domain.usecase.AddCatalogUrlUseCase
import kr.b1ink.scriptwidget.domain.usecase.BrowseGalleryUseCase
import kr.b1ink.scriptwidget.domain.usecase.InstallCatalogEntryUseCase
import kr.b1ink.scriptwidget.domain.usecase.RemoveCatalogUrlUseCase
import kr.b1ink.scriptwidget.domain.usecase.UpdateCatalogEntryUseCase
import kotlin.coroutines.cancellation.CancellationException

/** A downloaded script waiting for the user to confirm, after seeing its code and permissions. */
data class InstallPreview(val sourceId: String, val entry: CatalogEntry, val code: String)

data class GalleryUiState(
    val loading: Boolean = true,
    val sections: List<GallerySection> = emptyList(),
    val category: String? = null,
    val preview: InstallPreview? = null,
    val busy: Boolean = false,
    val message: String? = null,
) {
    val categories: List<String> get() = sections.flatMap { s -> s.items.map { it.entry.category } }.distinct()

    val visibleSections: List<GallerySection>
        get() = if (category == null) sections
        else sections.map { s -> s.copy(items = s.items.filter { it.entry.category == category }) }
}

class GalleryViewModel(
    private val builtin: CatalogSource,
    private val remote: (url: String) -> CatalogSource,
    urls: CatalogUrlRepository,
    installed: InstalledScriptRepository,
    scripts: ScriptRepository,
) : ViewModel() {
    private val browse = BrowseGalleryUseCase(builtin, urls, remote, installed, scripts)
    private val installEntry = InstallCatalogEntryUseCase(scripts, installed)
    private val updateEntry = UpdateCatalogEntryUseCase(scripts, installed)
    private val addCatalogUrl = AddCatalogUrlUseCase(urls)
    private val removeCatalogUrl = RemoveCatalogUrlUseCase(urls)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { reload() }
    }

    fun selectCategory(category: String?) = _uiState.update { it.copy(category = category) }

    /** Built-in scripts install right away; downloaded ones are shown first ([InstallPreview]). */
    fun install(sourceId: String, entryId: String) {
        val entry = entry(sourceId, entryId) ?: return
        val source = sourceFor(sourceId)
        task {
            if (source === builtin) {
                val name = installEntry(source, entry)
                done("'$name'을 설치했습니다")
            } else {
                _uiState.update { it.copy(preview = InstallPreview(sourceId, entry, source.code(entry))) }
            }
        }
    }

    fun confirmInstall() {
        val preview = _uiState.value.preview ?: return
        _uiState.update { it.copy(preview = null) }
        task {
            val name = installEntry(PreviewedSource(sourceFor(preview.sourceId), preview), preview.entry)
            done("'$name'을 설치했습니다")
        }
    }

    fun cancelPreview() = _uiState.update { it.copy(preview = null) }

    fun update(sourceId: String, entryId: String) {
        val entry = entry(sourceId, entryId) ?: return
        task {
            when (val result = updateEntry(sourceFor(sourceId), entry)) {
                is UpdateResult.Updated -> done("'${result.scriptName}'을 업데이트했습니다")
                is UpdateResult.InstalledAsCopy -> done("고친 스크립트는 그대로 두고 '${result.scriptName}'으로 설치했습니다")
            }
        }
    }

    fun addUrl(url: String) = task {
        when (val result = addCatalogUrl(url)) {
            AddUrlResult.Added -> done("카탈로그를 추가했습니다")
            AddUrlResult.AlreadyAdded -> _uiState.update { it.copy(message = "이미 추가된 주소입니다") }
            is AddUrlResult.Invalid -> _uiState.update { it.copy(message = result.reason) }
        }
    }

    fun removeUrl(url: String) = task {
        removeCatalogUrl(url)
        done("카탈로그를 삭제했습니다. 설치한 스크립트는 그대로 남아 있습니다")
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun entry(sourceId: String, entryId: String): CatalogEntry? =
        _uiState.value.sections.firstOrNull { it.sourceId == sourceId }?.items?.firstOrNull { it.entry.id == entryId }?.entry

    private fun sourceFor(id: String): CatalogSource = if (id == builtin.id) builtin else remote(id)

    private suspend fun reload() {
        val sections = browse()
        _uiState.update { it.copy(loading = false, sections = sections) }
    }

    private suspend fun done(message: String) {
        reload()
        _uiState.update { it.copy(message = message) }
    }

    private fun task(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(message = "실패했습니다: ${e.message ?: e::class.simpleName}") }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    /** Installs exactly the code the user looked at, not a fresh download. */
    private class PreviewedSource(private val source: CatalogSource, private val preview: InstallPreview) : CatalogSource by source {
        override suspend fun code(entry: CatalogEntry): String = if (entry.id == preview.entry.id) preview.code else source.code(entry)
    }
}
