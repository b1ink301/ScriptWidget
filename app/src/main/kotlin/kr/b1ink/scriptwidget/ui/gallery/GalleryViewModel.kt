package kr.b1ink.scriptwidget.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.R
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
import kr.b1ink.scriptwidget.ui.text.UiText
import kotlin.coroutines.cancellation.CancellationException

/** A downloaded script waiting for the user to confirm, after seeing its code and permissions. */
data class InstallPreview(val sourceId: String, val entry: CatalogEntry, val code: String) {
    /** What the code uses that the catalog doesn't show: a remote catalog can under-declare. */
    val undeclaredPermissions: Set<String>
        get() = kr.b1ink.scriptwidget.domain.model.CatalogValidator.usedPermissions(code) - entry.permissions.toSet()
}

data class GalleryUiState(
    val loading: Boolean = true,
    val sections: List<GallerySection> = emptyList(),
    val category: String? = null,
    val preview: InstallPreview? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
    /** "ko" / "en": entries show their title, description and category in this language. */
    val language: String = "ko",
) {
    /** Categories in [language]; a blank one is shown as "Other". */
    val categories: List<String> get() = sections.flatMap { s -> s.items.map { categoryOf(it.entry) } }.distinct()

    val visibleSections: List<GallerySection>
        get() = if (category == null) sections
        else sections.map { s -> s.copy(items = s.items.filter { categoryOf(it.entry) == category }) }

    fun categoryOf(entry: CatalogEntry): String = entry.textFor(language).category
}

class GalleryViewModel(
    private val builtin: CatalogSource,
    private val remote: (url: String) -> CatalogSource,
    urls: CatalogUrlRepository,
    installed: InstalledScriptRepository,
    scripts: ScriptRepository,
    /** "ko" / "en", from the app's current language. */
    language: String = "ko",
) : ViewModel() {
    private val browse = BrowseGalleryUseCase(builtin, urls, remote, installed, scripts)
    private val installEntry = InstallCatalogEntryUseCase(scripts, installed)
    private val updateEntry = UpdateCatalogEntryUseCase(scripts, installed)
    private val addCatalogUrl = AddCatalogUrlUseCase(urls)
    private val removeCatalogUrl = RemoveCatalogUrlUseCase(urls)

    private val _uiState = MutableStateFlow(GalleryUiState(language = language))
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
                done(UiText.res(R.string.gallery_message_installed, name))
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
            done(UiText.res(R.string.gallery_message_installed, name))
        }
    }

    fun cancelPreview() = _uiState.update { it.copy(preview = null) }

    fun update(sourceId: String, entryId: String) {
        val entry = entry(sourceId, entryId) ?: return
        task {
            when (val result = updateEntry(sourceFor(sourceId), entry)) {
                is UpdateResult.Updated -> done(UiText.res(R.string.gallery_message_updated, result.scriptName))
                is UpdateResult.InstalledAsCopy -> done(UiText.res(R.string.gallery_message_installed_as_copy, result.scriptName))
            }
        }
    }

    fun addUrl(url: String) = task {
        when (val result = addCatalogUrl(url)) {
            AddUrlResult.Added -> done(UiText.res(R.string.gallery_message_catalog_added))
            AddUrlResult.AlreadyAdded -> _uiState.update { it.copy(message = UiText.res(R.string.gallery_message_already_added)) }
            AddUrlResult.Invalid -> _uiState.update { it.copy(message = UiText.res(R.string.gallery_message_invalid_url)) }
        }
    }

    fun removeUrl(url: String) = task {
        removeCatalogUrl(url)
        done(UiText.res(R.string.gallery_message_catalog_removed))
    }

    fun messageShown() = _uiState.update { it.copy(message = null) }

    private fun entry(sourceId: String, entryId: String): CatalogEntry? =
        _uiState.value.sections.firstOrNull { it.sourceId == sourceId }?.items?.firstOrNull { it.entry.id == entryId }?.entry

    private fun sourceFor(id: String): CatalogSource = if (id == builtin.id) builtin else remote(id)

    private suspend fun reload() {
        val sections = browse()
        _uiState.update { it.copy(loading = false, sections = sections) }
    }

    private suspend fun done(message: UiText) {
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
                _uiState.update { it.copy(message = UiText.res(R.string.gallery_message_failed, e.message ?: e::class.simpleName.orEmpty())) }
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
