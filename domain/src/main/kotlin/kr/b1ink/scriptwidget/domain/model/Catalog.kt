package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.Serializable

/**
 * One script offered by a gallery catalog (`index.json`). [name] is the script name it installs
 * as; [script] is the code's path, relative to the catalog.
 */
@Serializable
data class CatalogEntry(
    val id: String,
    val name: String,
    val title: String = name,
    val category: String = "기타",
    val description: String = "",
    val script: String,
    val version: Int = 1,
    /** "small" | "medium" | "large" */
    val sizes: List<String> = emptyList(),
    /** What the script asks for, e.g. "calendar", "location", "network". */
    val permissions: List<String> = emptyList(),
    val author: String? = null,
    /** Title, description and category in other languages, by language code ("en"). */
    val translations: Map<String, CatalogText> = emptyMap(),
    /** The script in other languages, by language code: `{"en": "designs/clock.en.js"}`. */
    val localizedScripts: Map<String, String> = emptyMap(),
    /** Installed with the other samples on first launch. */
    val seed: Boolean = true,
) {
    /** The script path for [language], else [script]. */
    fun scriptFor(language: String?): String = language?.let(localizedScripts::get) ?: script

    fun allScripts(): List<String> = listOf(script) + localizedScripts.values

    /** What to show for [language] ("en", "ko", ...): its translation, else the catalog's own text. */
    fun textFor(language: String): CatalogText =
        translations[language] ?: CatalogText(title = title, description = description, category = category)
}

@Serializable
data class CatalogText(val title: String, val description: String = "", val category: String = "")

@Serializable
data class Catalog(
    val title: String,
    val entries: List<CatalogEntry>,
    /** [title] in other languages, by language code ("en"). */
    val titles: Map<String, String> = emptyMap(),
) {
    fun titleFor(language: String): String = titles[language] ?: title
}

/** Where an installed script came from, to offer updates without touching the user's edits. */
@Serializable
data class InstalledScript(
    val scriptName: String,
    val sourceId: String,
    val entryId: String,
    val version: Int,
    /** Fingerprint of the installed code: an unchanged script can be updated in place. */
    val codeHash: String,
)

sealed interface EntryStatus {
    data object NotInstalled : EntryStatus
    data class Installed(val scriptName: String) : EntryStatus
    data class UpdateAvailable(val scriptName: String) : EntryStatus
}

data class GalleryItem(val entry: CatalogEntry, val status: EntryStatus)

data class GallerySection(
    val sourceId: String,
    val title: String,
    val items: List<GalleryItem>,
    val error: String? = null,
    /** Built-in catalogs can't be removed; added URLs can. */
    val removable: Boolean = false,
    /** [title] in other languages, from the catalog. */
    val titles: Map<String, String> = emptyMap(),
)

sealed interface UpdateResult {
    data class Updated(val scriptName: String) : UpdateResult
    /** The user had edited the script: it stays, the new version is installed next to it. */
    data class InstalledAsCopy(val scriptName: String) : UpdateResult
}

sealed interface AddUrlResult {
    data object Added : AddUrlResult
    data object AlreadyAdded : AddUrlResult
    /** Not an https:// address. */
    data object Invalid : AddUrlResult
}

object CodeHash {
    fun of(code: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(code.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
