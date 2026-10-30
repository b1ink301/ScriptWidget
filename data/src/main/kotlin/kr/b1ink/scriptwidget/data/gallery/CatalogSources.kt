package kr.b1ink.scriptwidget.data.gallery

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.data.engine.HttpGateway
import kr.b1ink.scriptwidget.data.engine.HttpRequestSpec
import kr.b1ink.scriptwidget.domain.model.Catalog
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kr.b1ink.scriptwidget.domain.model.InstalledScript
import kr.b1ink.scriptwidget.domain.repository.CatalogSource
import kr.b1ink.scriptwidget.domain.repository.CatalogUrlRepository
import kr.b1ink.scriptwidget.domain.repository.InstalledScriptRepository
import kr.b1ink.scriptwidget.domain.repository.SampleScriptSource
import java.io.File
import java.io.IOException
import java.net.URI

internal val CatalogJson = Json { ignoreUnknownKeys = true }

/** The gallery bundled with the app: `/samples/catalog.json` and the scripts next to it. */
class BuiltinCatalogSource : CatalogSource, SampleScriptSource {
    override val id: String = ID

    override suspend fun catalog(): Catalog = catalogNow()

    override suspend fun code(entry: CatalogEntry): String = resource(entry.script)

    /** First launch installs every bundled sample under its script name. */
    override fun load(): Map<String, String> = catalogNow().entries.associate { it.name to resource(it.script) }

    private fun catalogNow(): Catalog = CatalogJson.decodeFromString(Catalog.serializer(), resource("catalog.json"))

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResourceAsStream("/samples/$path")) { "Missing sample resource: $path" }
            .bufferedReader().use { it.readText() }

    companion object {
        const val ID = "builtin"
    }
}

/**
 * A catalog published as `index.json` (e.g. in a GitHub repository); entry scripts are resolved
 * relative to it. Only https is used.
 */
class RemoteCatalogSource(
    private val url: String,
    private val http: HttpGateway,
    private val timeoutSeconds: Double = 20.0,
) : CatalogSource {
    override val id: String = url

    override suspend fun catalog(): Catalog =
        CatalogJson.decodeFromString(Catalog.serializer(), fetch(url))

    override suspend fun code(entry: CatalogEntry): String = fetch(URI(url).resolve(entry.script).toString())

    private suspend fun fetch(target: String): String {
        if (!target.startsWith("https://")) throw IOException("https 주소만 사용할 수 있습니다: $target")
        val response = http.loadString(HttpRequestSpec(url = target, timeout = timeoutSeconds))
        if (response.statusCode !in 200..299) throw IOException("HTTP ${response.statusCode}: $target")
        return response.body
    }
}

/** Where gallery scripts came from, in a small JSON file. */
class FileInstalledScriptRepository(private val file: File) : InstalledScriptRepository {
    private val lock = Mutex()
    private val serializer = ListSerializer(InstalledScript.serializer())

    override suspend fun all(): List<InstalledScript> = lock.withLock { read() }

    override suspend fun save(record: InstalledScript) = lock.withLock {
        write(read().filterNot { it.scriptName == record.scriptName } + record)
    }

    private suspend fun read(): List<InstalledScript> = withContext(Dispatchers.IO) {
        runCatching { CatalogJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }

    private suspend fun write(records: List<InstalledScript>) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        file.writeText(CatalogJson.encodeToString(serializer, records))
    }
}

/** Catalog URLs the user added, in a small JSON file. */
class FileCatalogUrlRepository(private val file: File) : CatalogUrlRepository {
    private val lock = Mutex()
    private val serializer = ListSerializer(String.serializer())
    private val state = MutableStateFlow<List<String>?>(null)

    override val urls: Flow<List<String>> = state.onStart { if (state.value == null) lock.withLock { loaded() } }.filterNotNull()

    override suspend fun add(url: String) = lock.withLock { save(loaded() + url) }

    override suspend fun remove(url: String) = lock.withLock { save(loaded() - url) }

    private suspend fun loaded(): List<String> = state.value ?: withContext(Dispatchers.IO) {
        runCatching { CatalogJson.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }.also { state.value = it }

    private suspend fun save(list: List<String>) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(CatalogJson.encodeToString(serializer, list))
        }
        state.value = list
    }
}

/**
 * The official gallery: the samples catalog in the GitHub repository, so fixed and new samples
 * reach users without an app update. Offline (or when a download fails) the copy bundled with the
 * app is used. It keeps the built-in id, so scripts installed from either stay one gallery entry.
 */
class OfficialCatalogSource(
    private val online: CatalogSource,
    private val bundled: CatalogSource,
) : CatalogSource {
    override val id: String = bundled.id

    override suspend fun catalog(): Catalog = try {
        online.catalog()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        bundled.catalog()
    }

    override suspend fun code(entry: CatalogEntry): String = try {
        online.code(entry)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        val local = runCatching { bundled.catalog().entries.firstOrNull { it.id == entry.id } }.getOrNull() ?: throw e
        bundled.code(local)
    }

    companion object {
        const val URL = "https://raw.githubusercontent.com/b1ink301/ScriptWidget/main/data/src/main/resources/samples/catalog.json"
    }
}
