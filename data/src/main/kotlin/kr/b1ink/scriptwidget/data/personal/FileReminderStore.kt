package kr.b1ink.scriptwidget.data.personal

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.data.engine.CalendarInfo
import kr.b1ink.scriptwidget.data.engine.NotificationSpec
import kr.b1ink.scriptwidget.data.engine.Notifier
import kr.b1ink.scriptwidget.data.engine.ReminderData
import kr.b1ink.scriptwidget.data.engine.ReminderStore
import java.io.File
import java.util.UUID

/**
 * Reminders kept in a JSON file (Android has no system reminders). Incomplete reminders with a
 * future due date get a notification at that time.
 */
class FileReminderStore(
    private val file: File,
    private val notifier: Notifier,
    private val now: () -> Long = System::currentTimeMillis,
) : ReminderStore {

    @Serializable
    private data class Snapshot(val lists: List<CalendarInfo> = emptyList(), val reminders: List<ReminderData> = emptyList())

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun read(): Snapshot =
        runCatching { json.decodeFromString(Snapshot.serializer(), file.readText()) }.getOrDefault(Snapshot())

    private fun write(snapshot: Snapshot) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(Snapshot.serializer(), snapshot))
    }

    /** Reads the file, guaranteeing the default list exists. */
    private fun load(): Snapshot {
        val snapshot = read()
        if (snapshot.lists.any { it.identifier == DEFAULT_LIST_ID }) return snapshot
        return snapshot.copy(lists = listOf(CalendarInfo(DEFAULT_LIST_ID, "Reminders", "#FF007AFF")) + snapshot.lists)
            .also(::write)
    }

    @Synchronized
    override fun lists(): List<CalendarInfo> = load().lists

    @Synchronized
    override fun defaultList(): CalendarInfo = load().lists.first { it.identifier == DEFAULT_LIST_ID }

    @Synchronized
    override fun createList(title: String): CalendarInfo {
        val list = CalendarInfo(UUID.randomUUID().toString(), title, "#FF34C759")
        val snapshot = load()
        write(snapshot.copy(lists = snapshot.lists + list))
        return list
    }

    @Synchronized
    override fun renameList(identifier: String, title: String) {
        val snapshot = load()
        write(snapshot.copy(lists = snapshot.lists.map { if (it.identifier == identifier) it.copy(title = title) else it }))
    }

    @Synchronized
    override fun removeList(identifier: String) {
        require(identifier != DEFAULT_LIST_ID) { "The default reminder list can't be removed" }
        val snapshot = load()
        snapshot.reminders.filter { it.listId == identifier }.forEach { cancelNotification(it.identifier!!) }
        write(Snapshot(snapshot.lists.filter { it.identifier != identifier }, snapshot.reminders.filter { it.listId != identifier }))
    }

    @Synchronized
    override fun reminders(listIds: List<String>?): List<ReminderData> =
        load().reminders.filter { listIds == null || it.listId in listIds }

    @Synchronized
    override fun save(reminder: ReminderData): String {
        val snapshot = load()
        val id = reminder.identifier ?: UUID.randomUUID().toString()
        val previous = snapshot.reminders.firstOrNull { it.identifier == id }
        val saved = reminder.copy(
            identifier = id,
            listId = reminder.listId ?: previous?.listId ?: DEFAULT_LIST_ID,
            creationDate = reminder.creationDate ?: previous?.creationDate ?: now(),
            completionDate = when {
                !reminder.isCompleted -> null
                else -> reminder.completionDate ?: previous?.completionDate ?: now()
            },
        )
        write(snapshot.copy(reminders = snapshot.reminders.filter { it.identifier != id } + saved))
        val due = saved.dueDate
        if (!saved.isCompleted && due != null && due > now()) {
            // The due alert is a bonus: without notification permission the reminder is still saved.
            runCatching {
                notifier.schedule(NotificationSpec(identifier = notificationId(id), title = saved.title, body = saved.notes.orEmpty(), triggerAt = due))
            }
        } else {
            cancelNotification(id)
        }
        return id
    }

    @Synchronized
    override fun remove(identifier: String) {
        val snapshot = load()
        write(snapshot.copy(reminders = snapshot.reminders.filter { it.identifier != identifier }))
        cancelNotification(identifier)
    }

    private fun cancelNotification(id: String) = runCatching { notifier.remove(listOf(notificationId(id))) }

    private fun notificationId(id: String) = "reminder-$id"

    private companion object {
        const val DEFAULT_LIST_ID = "default"
    }
}
