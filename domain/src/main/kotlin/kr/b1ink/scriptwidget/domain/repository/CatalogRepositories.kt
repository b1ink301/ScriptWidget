package kr.b1ink.scriptwidget.domain.repository

import kotlinx.coroutines.flow.Flow
import kr.b1ink.scriptwidget.domain.model.Catalog
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kr.b1ink.scriptwidget.domain.model.InstalledScript

/** A gallery catalog: built into the app, or an `index.json` at a URL. */
interface CatalogSource {
    /** "builtin", or the catalog URL. */
    val id: String

    /** @throws Exception when the catalog can't be loaded. */
    suspend fun catalog(): Catalog

    /** @throws Exception when the code can't be loaded. */
    suspend fun code(entry: CatalogEntry): String
}

interface InstalledScriptRepository {
    suspend fun all(): List<InstalledScript>

    /** Replaces the record of the same script name. */
    suspend fun save(record: InstalledScript)
}

/** Catalog URLs the user added, in the order added. */
interface CatalogUrlRepository {
    val urls: Flow<List<String>>

    suspend fun add(url: String)

    suspend fun remove(url: String)
}
