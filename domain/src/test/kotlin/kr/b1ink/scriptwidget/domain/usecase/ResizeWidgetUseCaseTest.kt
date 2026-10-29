package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeRenderStateRepository
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResizeWidgetUseCaseTest {
    private val renderStates = FakeRenderStateRepository()
    private val scheduler = FakeRefreshScheduler()
    private val resize = ResizeWidgetUseCase(renderStates, scheduler)

    @Test
    fun `resizing within the same family redraws without running the script`() = runTest {
        val state = RenderState(scriptName = "clock", family = WidgetFamily.Medium, updatedAt = 1)
        renderStates.states[3] = state

        resize(3, WidgetFamily.Medium)

        assertEquals(listOf(3 to state), renderStates.published)
        assertTrue(scheduler.refreshed.isEmpty())
    }

    @Test
    fun `a new family runs the script again`() = runTest {
        renderStates.states[3] = RenderState(scriptName = "clock", family = WidgetFamily.Medium, updatedAt = 1)

        resize(3, WidgetFamily.Large)

        assertEquals(listOf(3), scheduler.refreshed)
        assertTrue(renderStates.published.isEmpty())
    }

    @Test
    fun `a widget without a drawn state runs the script`() = runTest {
        resize(3, WidgetFamily.Small)

        assertEquals(listOf(3), scheduler.refreshed)
    }
}
