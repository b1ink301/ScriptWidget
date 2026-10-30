package kr.b1ink.scriptwidget.data.trigger

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.model.EventTrigger
import kr.b1ink.scriptwidget.domain.model.TriggerAction
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals

class FileEventTriggerRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `triggers are kept across instances, replaced by id and removed`() = runTest {
        val file = tmp.root.resolve("triggers/triggers.json")
        val a = EventTrigger("a", TriggerEvent.CalendarChanged, "오늘 일정")
        val b = EventTrigger("b", TriggerEvent.Charging, "기록", TriggerAction.RunScript)
        FileEventTriggerRepository(file).save(a)
        FileEventTriggerRepository(file).save(b)
        FileEventTriggerRepository(file).save(a.copy(enabled = false))

        assertEquals(listOf(a.copy(enabled = false), b), FileEventTriggerRepository(file).all())

        FileEventTriggerRepository(file).remove("a")
        assertEquals(listOf(b), FileEventTriggerRepository(file).triggers.first())
    }

    @Test
    fun `a missing or broken file means no triggers`() = runTest {
        val file = tmp.root.resolve("broken.json").apply { writeText("{not json") }

        assertEquals(emptyList(), FileEventTriggerRepository(file).all())
        assertEquals(emptyList(), FileEventTriggerRepository(tmp.root.resolve("none.json")).all())
    }
}
