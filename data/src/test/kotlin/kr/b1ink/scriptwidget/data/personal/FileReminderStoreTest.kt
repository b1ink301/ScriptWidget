package kr.b1ink.scriptwidget.data.personal

import kr.b1ink.scriptwidget.data.engine.FakeNotifier
import kr.b1ink.scriptwidget.data.engine.ReminderData
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileReminderStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val notifier = FakeNotifier()
    private val file by lazy { tmp.root.resolve("reminders.json") }
    private fun store(now: Long = 1_000L) = FileReminderStore(file, notifier, now = { now })

    @Test
    fun `a default list exists and lists persist`() {
        val store = store()
        val default = store.defaultList()
        val groceries = store.createList("Groceries")

        assertEquals(listOf(default.title, "Groceries"), store().lists().map { it.title })
        store().renameList(groceries.identifier, "Shopping")
        assertEquals("Shopping", store().lists()[1].title)
        store().removeList(groceries.identifier)
        assertEquals(1, store().lists().size)
    }

    @Test
    fun `reminders are saved, completed and removed`() {
        val store = store(now = 5_000L)
        val id = store.save(ReminderData(title = "Milk", dueDate = 10_000L))
        val saved = store().reminders(null).single()
        assertEquals(id, saved.identifier)
        assertEquals(5_000L, saved.creationDate)
        assertNotNull(saved.listId)

        store(now = 7_000L).save(saved.copy(isCompleted = true))
        assertEquals(7_000L, store().reminders(null).single().completionDate)

        store().remove(id)
        assertTrue(store().reminders(null).isEmpty())
    }

    @Test
    fun `future due dates schedule a notification and completion cancels it`() {
        val id = store(now = 1_000L).save(ReminderData(title = "Call mom", dueDate = 60_000L))
        val spec = notifier.scheduled.single()
        assertEquals("Call mom", spec.title)
        assertEquals(60_000L, spec.triggerAt)

        store().save(store().reminders(null).single().copy(isCompleted = true))
        assertTrue(notifier.removed.flatten().contains("reminder-$id"))
    }

    @Test
    fun `reminders without due dates or in the past are not notified`() {
        store(now = 100_000L).save(ReminderData(title = "No date"))
        store(now = 100_000L).save(ReminderData(title = "Past", dueDate = 50_000L))
        assertTrue(notifier.scheduled.isEmpty())
    }

    @Test
    fun `filtering by list`() {
        val store = store()
        val a = store.createList("A")
        val b = store.createList("B")
        store.save(ReminderData(title = "in A", listId = a.identifier))
        store.save(ReminderData(title = "in B", listId = b.identifier))

        assertEquals(listOf("in A"), store.reminders(listOf(a.identifier)).map { it.title })
        assertNull(store.reminders(listOf("missing")).firstOrNull())
    }

    @Test
    fun `saving works without notification permission`() {
        val denying = object : kr.b1ink.scriptwidget.data.engine.Notifier {
            override fun schedule(spec: kr.b1ink.scriptwidget.data.engine.NotificationSpec) =
                throw SecurityException("Notification permission not granted")
            override fun remove(identifiers: List<String>) = Unit
        }
        val store = FileReminderStore(file, denying, now = { 1_000L })

        val id = store.save(ReminderData(title = "due later", dueDate = 5_000L))

        assertEquals(listOf("due later"), store.reminders(null).filter { it.identifier == id }.map { it.title })
    }
}
