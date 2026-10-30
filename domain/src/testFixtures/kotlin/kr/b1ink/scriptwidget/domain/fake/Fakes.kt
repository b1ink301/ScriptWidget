package kr.b1ink.scriptwidget.domain.fake

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kr.b1ink.scriptwidget.domain.engine.ScriptEngine
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptFile
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.repository.RenderStateRepository
import kr.b1ink.scriptwidget.domain.repository.ScriptDocuments
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository
import kr.b1ink.scriptwidget.domain.repository.WidgetBindingRepository
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler
import kr.b1ink.scriptwidget.domain.time.Clock

class FakeScriptRepository(initial: Map<String, String> = emptyMap()) : ScriptRepository {
    val files = initial.toMutableMap()
    private val names = MutableStateFlow(files.keys.sorted())

    override val scriptNames: StateFlow<List<String>> = names

    private fun publish() {
        names.value = files.keys.sorted()
    }

    override suspend fun list(): List<String> = files.keys.sorted()
    override suspend fun read(name: String): String? = files[name]
    override suspend fun exists(name: String): Boolean = name in files

    override suspend fun write(name: String, content: String) {
        files[name] = content
        publish()
    }

    override suspend fun rename(from: String, to: String): Boolean {
        val content = files.remove(from) ?: return false
        files[to] = content
        publish()
        return true
    }

    override suspend fun delete(name: String) {
        files.remove(name)
        publish()
    }
}

class FakeWidgetBindingRepository(vararg initial: WidgetBinding) : WidgetBindingRepository {
    val bindings = initial.associateBy { it.appWidgetId }.toMutableMap()

    override suspend fun get(appWidgetId: Int): WidgetBinding? = bindings[appWidgetId]

    override suspend fun set(binding: WidgetBinding) {
        bindings[binding.appWidgetId] = binding
    }

    override suspend fun remove(appWidgetId: Int) {
        bindings.remove(appWidgetId)
    }

    override suspend fun widgetsUsing(scriptName: String): List<Int> =
        bindings.values.filter { it.scriptName == scriptName }.map { it.appWidgetId }.sorted()
}

class FakeRenderStateRepository : RenderStateRepository {
    val states = mutableMapOf<Int, RenderState>()
    val published = mutableListOf<Pair<Int, RenderState>>()

    override suspend fun get(appWidgetId: Int): RenderState? = states[appWidgetId]

    override suspend fun publish(appWidgetId: Int, state: RenderState) {
        states[appWidgetId] = state
        published += appWidgetId to state
    }
}

/** Returns [result] (or the result of [handler]) and records every call. */
class FakeScriptEngine(
    var result: ScriptRunResult = ScriptRunResult(),
    var handler: ((script: String, env: ScriptEnvironment) -> ScriptRunResult)? = null,
) : ScriptEngine {
    data class Call(val script: String, val env: ScriptEnvironment, val scriptName: String)

    val calls = mutableListOf<Call>()

    override suspend fun execute(script: String, env: ScriptEnvironment, scriptName: String): ScriptRunResult {
        calls += Call(script, env, scriptName)
        return handler?.invoke(script, env) ?: result
    }
}

class FakeRefreshScheduler : WidgetRefreshScheduler {
    val refreshed = mutableListOf<Int>()
    val scheduled = mutableListOf<Int>()
    val cancelled = mutableListOf<Int>()
    val refreshAt = mutableListOf<Pair<Int, Long>>()
    val actions = mutableListOf<Pair<Int, String>>()

    override fun runAction(appWidgetId: Int, action: String) {
        actions += appWidgetId to action
    }

    override fun refreshNow(appWidgetId: Int) {
        refreshed += appWidgetId
    }

    /** Whether each [refreshAt] request may wake the device. */
    val wakeUps = mutableListOf<Boolean>()

    override fun refreshAt(appWidgetId: Int, epochMillis: Long, wakeUp: Boolean) {
        refreshAt += appWidgetId to epochMillis
        wakeUps += wakeUp
    }

    override fun schedulePeriodic(appWidgetId: Int) {
        scheduled += appWidgetId
    }

    override fun cancel(appWidgetId: Int) {
        cancelled += appWidgetId
    }
}

class FixedClock(var millis: Long = 1_000L) : Clock {
    override fun now(): Long = millis
}

/** Documents addressed by URI string; reading a missing URI fails like an unreadable file. */
class FakeScriptDocuments(vararg initial: Pair<String, ScriptFile>) :
    ScriptDocuments {
    val files: MutableMap<String, ScriptFile> = initial.toMap().toMutableMap()
    val written = mutableMapOf<String, String>()

    override suspend fun read(uri: String): ScriptFile =
        files[uri] ?: throw java.io.IOException("Can't open $uri")

    override suspend fun write(uri: String, content: String) {
        written[uri] = content
    }

    val bytes = mutableMapOf<String, ByteArray>()

    override suspend fun readBytes(uri: String): ByteArray = bytes[uri] ?: throw java.io.IOException("Can't open $uri")

    override suspend fun writeBytes(uri: String, content: ByteArray) {
        bytes[uri] = content
    }
}

/** Stores the archive as plain text so tests can read it. */
class FakeArchiveCodec : kr.b1ink.scriptwidget.domain.repository.ScriptArchiveCodec {
    override fun encode(files: Map<String, String>): ByteArray =
        files.entries.joinToString("\u0000") { "${it.key}\u0001${it.value}" }.toByteArray()

    override fun decode(bytes: ByteArray): Map<String, String> {
        val text = String(bytes)
        if (text.isEmpty()) return emptyMap()
        if (!text.contains('\u0001')) throw java.io.IOException("Not an archive")
        return text.split('\u0000').associate { it.substringBefore('\u0001') to it.substringAfter('\u0001') }
    }
}

class FakeRunHistory : kr.b1ink.scriptwidget.domain.repository.RunHistoryRepository {
    val records = MutableStateFlow<List<kr.b1ink.scriptwidget.domain.model.RunRecord>>(emptyList())

    override val history: kotlinx.coroutines.flow.Flow<List<kr.b1ink.scriptwidget.domain.model.RunRecord>> = records

    override suspend fun add(record: kr.b1ink.scriptwidget.domain.model.RunRecord) {
        records.value = listOf(record) + records.value
    }

    override suspend fun clear() {
        records.value = emptyList()
    }
}

class FakeFileBookmarks : kr.b1ink.scriptwidget.domain.repository.FileBookmarkRepository {
    val files = MutableStateFlow<Map<String, ByteArray>>(emptyMap())

    override val bookmarks: kotlinx.coroutines.flow.Flow<List<kr.b1ink.scriptwidget.domain.model.FileBookmark>> =
        files.map { all -> all.entries.sortedBy { it.key }.map { kr.b1ink.scriptwidget.domain.model.FileBookmark(it.key, it.value.size.toLong()) } }

    override suspend fun save(name: String, bytes: ByteArray) {
        files.value = files.value + (name to bytes)
    }

    override suspend fun remove(name: String) {
        files.value = files.value - name
    }
}

class FakeScreenState(var on: Boolean = true) : kr.b1ink.scriptwidget.domain.scheduler.ScreenState {
    override fun isInteractive(): Boolean = on
}

class FakeNetworkState(var connected: Boolean = true) : kr.b1ink.scriptwidget.domain.scheduler.NetworkState {
    override fun isConnected(): Boolean = connected
}

class FakeWidgetCache : kr.b1ink.scriptwidget.domain.repository.WidgetCache {
    val cleared = mutableListOf<Int>()

    override suspend fun clear(appWidgetId: Int) {
        cleared += appWidgetId
    }
}

class FakeCatalogSource(
    override val id: String,
    var title: String,
    var entries: List<kr.b1ink.scriptwidget.domain.model.CatalogEntry> = emptyList(),
    var code: Map<String, String> = emptyMap(),
    var failure: String? = null,
) : kr.b1ink.scriptwidget.domain.repository.CatalogSource {
    override suspend fun catalog(): kr.b1ink.scriptwidget.domain.model.Catalog {
        failure?.let { throw java.io.IOException(it) }
        return kr.b1ink.scriptwidget.domain.model.Catalog(title, entries)
    }

    override suspend fun code(entry: kr.b1ink.scriptwidget.domain.model.CatalogEntry): String =
        code[entry.id] ?: throw java.io.IOException("no code for ${entry.id}")
}

class FakeInstalledScripts : kr.b1ink.scriptwidget.domain.repository.InstalledScriptRepository {
    val records = mutableListOf<kr.b1ink.scriptwidget.domain.model.InstalledScript>()

    override suspend fun all(): List<kr.b1ink.scriptwidget.domain.model.InstalledScript> = records.toList()

    override suspend fun save(record: kr.b1ink.scriptwidget.domain.model.InstalledScript) {
        records.removeAll { it.scriptName == record.scriptName }
        records += record
    }
}

class FakeCatalogUrls : kr.b1ink.scriptwidget.domain.repository.CatalogUrlRepository {
    private val state = MutableStateFlow<List<String>>(emptyList())
    override val urls: kotlinx.coroutines.flow.Flow<List<String>> = state

    override suspend fun add(url: String) {
        state.value = state.value + url
    }

    override suspend fun remove(url: String) {
        state.value = state.value - url
    }
}

class FakeRefreshSettings(var whileScreenOff: Boolean = false) : kr.b1ink.scriptwidget.domain.scheduler.RefreshSettings {
    override fun refreshWhileScreenOff(): Boolean = whileScreenOff
}
