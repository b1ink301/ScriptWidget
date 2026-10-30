package kr.b1ink.scriptwidget.ui.triggers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeEventMonitor
import kr.b1ink.scriptwidget.domain.fake.FakeEventTriggerRepository
import kr.b1ink.scriptwidget.domain.model.EventTrigger
import kr.b1ink.scriptwidget.domain.model.TriggerAction
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import kr.b1ink.scriptwidget.ui.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class TriggersViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val repository = FakeEventTriggerRepository()
    private val monitor = FakeEventMonitor()
    private val names = MutableStateFlow(listOf("오늘 일정", "기록"))
    private val vm by lazy { TriggersViewModel(repository, monitor, names, newId = { "id-${repository.triggers.value.size}" }) }

    @Test
    fun `lists the triggers and the scripts to choose from`() = runTest {
        repository.save(EventTrigger("a", TriggerEvent.DayChanged, "기록"))

        assertEquals(listOf(EventTrigger("a", TriggerEvent.DayChanged, "기록")), vm.state.value.triggers)
        assertEquals(listOf("오늘 일정", "기록"), vm.state.value.scripts)
    }

    @Test
    fun `adding, switching off and deleting update the list and what is watched`() = runTest {
        vm.add(TriggerEvent.CalendarChanged, "오늘 일정", TriggerAction.RefreshWidgets)
        val added = vm.state.value.triggers.single()
        assertEquals(setOf(TriggerEvent.CalendarChanged), monitor.watching)

        vm.setEnabled(added.id, false)
        assertEquals(false, vm.state.value.triggers.single().enabled)
        assertEquals(emptySet(), monitor.watching)

        vm.remove(added.id)
        assertEquals(emptyList(), vm.state.value.triggers)
    }
}
