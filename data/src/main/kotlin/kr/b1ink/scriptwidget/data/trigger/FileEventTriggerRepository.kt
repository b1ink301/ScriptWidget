package kr.b1ink.scriptwidget.data.trigger

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.domain.model.EventTrigger
import kr.b1ink.scriptwidget.domain.repository.EventTriggerRepository
import java.io.File

/** Event triggers in a small JSON file. */
class FileEventTriggerRepository(private val file: File) : EventTriggerRepository {
    private val lock = Mutex()
    private val serializer = ListSerializer(EventTrigger.serializer())
    private val json = Json { ignoreUnknownKeys = true }
    private val state = MutableStateFlow<List<EventTrigger>?>(null)

    override val triggers: Flow<List<EventTrigger>> = state.onStart { if (state.value == null) lock.withLock { loaded() } }.filterNotNull()

    override suspend fun all(): List<EventTrigger> = lock.withLock { loaded() }

    override suspend fun save(trigger: EventTrigger) = lock.withLock {
        val list = loaded()
        write(if (list.any { it.id == trigger.id }) list.map { if (it.id == trigger.id) trigger else it } else list + trigger)
    }

    override suspend fun remove(id: String) = lock.withLock { write(loaded().filterNot { it.id == id }) }

    private suspend fun loaded(): List<EventTrigger> = state.value ?: withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList())
    }.also { state.value = it }

    private suspend fun write(list: List<EventTrigger>) {
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(serializer, list))
        }
        state.value = list
    }
}
