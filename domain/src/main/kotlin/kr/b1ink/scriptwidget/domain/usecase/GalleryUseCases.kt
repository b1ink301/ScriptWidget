package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kr.b1ink.scriptwidget.domain.model.AddUrlResult
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kr.b1ink.scriptwidget.domain.model.CodeHash
import kr.b1ink.scriptwidget.domain.model.EntryStatus
import kr.b1ink.scriptwidget.domain.model.GalleryItem
import kr.b1ink.scriptwidget.domain.model.GallerySection
import kr.b1ink.scriptwidget.domain.model.InstalledScript
import kr.b1ink.scriptwidget.domain.model.UpdateResult
import kr.b1ink.scriptwidget.domain.repository.CatalogSource
import kr.b1ink.scriptwidget.domain.repository.CatalogUrlRepository
import kr.b1ink.scriptwidget.domain.repository.InstalledScriptRepository
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository
import kotlin.coroutines.cancellation.CancellationException

/** The built-in catalog, then every added catalog URL, with each entry's install status. */
class BrowseGalleryUseCase(
    private val builtin: CatalogSource,
    private val urls: CatalogUrlRepository,
    private val remote: (url: String) -> CatalogSource,
    private val installed: InstalledScriptRepository,
    private val scripts: ScriptRepository,
) {
    suspend operator fun invoke(): List<GallerySection> = coroutineScope {
        val records = installed.all()
        val existing = scripts.list().toSet()
        val sources = listOf(builtin) + urls.urls.first().map(remote)
        sources.map { source -> async { section(source, records, existing) } }.map { it.await() }
    }

    private suspend fun section(source: CatalogSource, records: List<InstalledScript>, existing: Set<String>): GallerySection {
        val removable = source !== builtin
        return try {
            val catalog = source.catalog()
            GallerySection(
                sourceId = source.id,
                title = catalog.title,
                items = catalog.entries.map { entry ->
                    val status = status(source.id, entry, records, existing)
                    GalleryItem(entry, if (status == EntryStatus.NotInstalled) adopted(source, entry, existing) else status)
                },
                removable = removable,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            GallerySection(source.id, source.id, emptyList(), error = e.message ?: e::class.simpleName, removable = removable)
        }
    }

    /** A script with the entry's name and exactly its code (e.g. a sample seeded before the gallery). */
    private suspend fun adopted(source: CatalogSource, entry: CatalogEntry, existing: Set<String>): EntryStatus {
        if (entry.name !in existing) return EntryStatus.NotInstalled
        val same = runCatching { scripts.read(entry.name) == source.code(entry) }.getOrDefault(false)
        return if (same) EntryStatus.Installed(entry.name) else EntryStatus.NotInstalled
    }

    private fun status(sourceId: String, entry: CatalogEntry, records: List<InstalledScript>, existing: Set<String>): EntryStatus {
        val record = records
            .filter { it.sourceId == sourceId && it.entryId == entry.id && it.scriptName in existing }
            .maxByOrNull { it.version }
            ?: return EntryStatus.NotInstalled
        return if (entry.version > record.version) EntryStatus.UpdateAvailable(record.scriptName)
        else EntryStatus.Installed(record.scriptName)
    }
}

/** Copies a catalog script into the user's scripts; an existing name is never overwritten. */
class InstallCatalogEntryUseCase(
    private val scripts: ScriptRepository,
    private val installed: InstalledScriptRepository,
) {
    /** @return the installed script's name. */
    suspend operator fun invoke(source: CatalogSource, entry: CatalogEntry): String {
        val code = source.code(entry)
        val name = scripts.freeName(ImportScriptsUseCase.scriptNameFor(entry.name))
        scripts.write(name, code)
        installed.save(InstalledScript(name, source.id, entry.id, entry.version, CodeHash.of(code)))
        return name
    }
}

/**
 * Installs a newer catalog version. A script still exactly as installed is replaced; one the user
 * changed is kept and the new version is installed next to it.
 */
class UpdateCatalogEntryUseCase(
    private val scripts: ScriptRepository,
    private val installed: InstalledScriptRepository,
) {
    suspend operator fun invoke(source: CatalogSource, entry: CatalogEntry): UpdateResult {
        val record = installed.all()
            .filter { it.sourceId == source.id && it.entryId == entry.id }
            .maxByOrNull { it.version }
        val current = record?.let { scripts.read(it.scriptName) }
        if (record == null || current == null || CodeHash.of(current) != record.codeHash) {
            return UpdateResult.InstalledAsCopy(InstallCatalogEntryUseCase(scripts, installed)(source, entry))
        }
        val code = source.code(entry)
        scripts.write(record.scriptName, code)
        installed.save(record.copy(version = entry.version, codeHash = CodeHash.of(code)))
        return UpdateResult.Updated(record.scriptName)
    }
}

class AddCatalogUrlUseCase(private val urls: CatalogUrlRepository) {
    suspend operator fun invoke(input: String): AddUrlResult {
        val url = input.trim()
        val valid = Regex("""^https://[^\s/]+\.[^\s/]+(/\S*)?$""").matches(url)
        if (!valid) return AddUrlResult.Invalid
        if (url in urls.urls.first()) return AddUrlResult.AlreadyAdded
        urls.add(url)
        return AddUrlResult.Added
    }
}

class RemoveCatalogUrlUseCase(private val urls: CatalogUrlRepository) {
    suspend operator fun invoke(url: String) = urls.remove(url)
}

/** On first launch (no scripts yet) installs every entry of the built-in catalog. */
class SeedFromCatalogUseCase(
    private val scripts: ScriptRepository,
    private val catalog: CatalogSource,
    private val install: InstallCatalogEntryUseCase,
) {
    suspend operator fun invoke() {
        if (scripts.list().isNotEmpty()) return
        catalog.catalog().entries.forEach { install(catalog, it) }
    }
}
