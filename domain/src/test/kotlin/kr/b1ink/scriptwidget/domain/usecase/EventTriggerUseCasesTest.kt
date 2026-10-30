package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeEventMonitor
import kr.b1ink.scriptwidget.domain.fake.FakeEventTriggerRepository
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeScriptEngine
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.EventTrigger
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.domain.model.TriggerAction
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EventTriggerUseCasesTest {
    private val triggers = FakeEventTriggerRepository()
    private val monitor = FakeEventMonitor()
    private val scripts = FakeScriptRepository(mapOf("오늘 일정" to "events code", "기록" to "log code"))
    private val bindings = FakeWidgetBindingRepository(WidgetBinding(1, "오늘 일정", null), WidgetBinding(2, "오늘 일정", null), WidgetBinding(3, "기록", null))
    private val scheduler = FakeRefreshScheduler()
    private val engine = FakeScriptEngine()
    private var ids = 0

    private val add = AddEventTriggerUseCase(triggers, monitor) { "t${++ids}" }
    private val remove = RemoveEventTriggerUseCase(triggers, monitor)
    private val setEnabled = SetEventTriggerEnabledUseCase(triggers, monitor)
    private val handle = HandleEventUseCase(triggers, bindings, scheduler, RunScriptByNameUseCase(scripts, RunScriptUseCase(engine)))

    @Test
    fun `adding a trigger saves it and starts watching its event`() = runTest {
        val trigger = add(TriggerEvent.CalendarChanged, "오늘 일정", TriggerAction.RefreshWidgets)

        assertEquals(EventTrigger("t1", TriggerEvent.CalendarChanged, "오늘 일정", TriggerAction.RefreshWidgets), trigger)
        assertEquals(listOf(trigger), triggers.all())
        assertEquals(setOf(TriggerEvent.CalendarChanged), monitor.watching)
    }

    @Test
    fun `an event refreshes the widgets of its scripts as an event refresh`() = runTest {
        add(TriggerEvent.CalendarChanged, "오늘 일정", TriggerAction.RefreshWidgets)
        add(TriggerEvent.DayChanged, "기록", TriggerAction.RefreshWidgets)

        handle(TriggerEvent.CalendarChanged)

        assertEquals(listOf(1, 2), scheduler.eventRefreshed)
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun `a run-script trigger runs the script in the background and tells it which event happened`() = runTest {
        add(TriggerEvent.Charging, "기록", TriggerAction.RunScript)

        handle(TriggerEvent.Charging)

        val call = engine.calls.single()
        assertEquals("log code", call.script)
        assertEquals(RunSource.Trigger, call.env.source)
        assertEquals("charging", call.env.trigger)
    }

    @Test
    fun `disabled and removed triggers stop and their events are no longer watched`() = runTest {
        val calendar = add(TriggerEvent.CalendarChanged, "오늘 일정", TriggerAction.RefreshWidgets)
        val photos = add(TriggerEvent.NewPhoto, "기록", TriggerAction.RunScript)

        setEnabled(calendar.id, false)
        assertEquals(setOf(TriggerEvent.NewPhoto), monitor.watching)
        handle(TriggerEvent.CalendarChanged)
        assertTrue(scheduler.eventRefreshed.isEmpty())

        remove(photos.id)
        assertEquals(emptySet(), monitor.watching)
        assertEquals(listOf(calendar.copy(enabled = false)), triggers.all())
    }

    @Test
    fun `watching is restored from the saved triggers`() = runTest {
        triggers.save(EventTrigger("x", TriggerEvent.TimeZoneChanged, "기록", TriggerAction.RefreshWidgets))

        SyncEventMonitorUseCase(triggers, monitor)()

        assertEquals(setOf(TriggerEvent.TimeZoneChanged), monitor.watching)
    }

    @Test
    fun `every event has a stable key for scripts`() {
        assertEquals(TriggerEvent.entries.size, TriggerEvent.entries.map { it.key }.toSet().size)
        assertEquals(TriggerEvent.WifiConnected, TriggerEvent.fromKey("wifi"))
    }
}
