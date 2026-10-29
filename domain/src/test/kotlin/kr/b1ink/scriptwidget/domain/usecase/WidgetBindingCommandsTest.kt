package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WidgetBindingCommandsTest {
    private val bindings = FakeWidgetBindingRepository(WidgetBinding(5, "old", null))
    private val scheduler = FakeRefreshScheduler()

    @Test
    fun `bind stores the binding, refreshes now and schedules periodic updates`() = runTest {
        BindWidgetUseCase(bindings, scheduler)(appWidgetId = 9, scriptName = "hello", parameter = "x")

        assertEquals(WidgetBinding(9, "hello", "x"), bindings.bindings[9])
        assertEquals(listOf(9), scheduler.refreshed)
        assertEquals(listOf(9), scheduler.scheduled)
    }

    @Test
    fun `blank parameter is stored as null`() = runTest {
        BindWidgetUseCase(bindings, scheduler)(9, "hello", "   ")

        assertEquals(WidgetBinding(9, "hello", null), bindings.bindings[9])
    }

    @Test
    fun `rebinding replaces the previous script`() = runTest {
        BindWidgetUseCase(bindings, scheduler)(5, "new", null)

        assertEquals("new", bindings.bindings[5]!!.scriptName)
    }

    @Test
    fun `unbind cancels work and forgets the bindings`() = runTest {
        bindings.bindings[6] = WidgetBinding(6, "x", null)

        UnbindWidgetsUseCase(bindings, scheduler)(listOf(5, 6))

        assertTrue(bindings.bindings.isEmpty())
        assertEquals(listOf(5, 6), scheduler.cancelled)
    }

    @Test
    fun `get binding returns the stored binding`() = runTest {
        assertEquals(WidgetBinding(5, "old", null), GetWidgetBindingUseCase(bindings)(5))
    }
}

class ScheduleBoundWidgetsUseCaseTest {
    private val bindings = FakeWidgetBindingRepository(WidgetBinding(1, "a", null), WidgetBinding(3, "b", null))
    private val scheduler = FakeRefreshScheduler()

    @Test
    fun `schedules periodic refresh only for widgets that already have a script`() = runTest {
        // A freshly added widget (2) gets onUpdate before it is bound; running it then would only show an error.
        ScheduleBoundWidgetsUseCase(bindings, scheduler)(listOf(1, 2, 3))

        assertEquals(listOf(1, 3), scheduler.scheduled)
        assertTrue(scheduler.refreshed.isEmpty())
    }

    @Test
    fun `unbinding also deletes the widget's cached files`() = kotlinx.coroutines.test.runTest {
        val cache = kr.b1ink.scriptwidget.domain.fake.FakeWidgetCache()
        val unbind = UnbindWidgetsUseCase(
            kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository(),
            kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler(),
            cache,
        )

        unbind(listOf(4, 5))

        kotlin.test.assertEquals(listOf(4, 5), cache.cleared)
    }
}
