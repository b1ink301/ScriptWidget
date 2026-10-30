package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRunHistory
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.RunRecord
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.domain.model.ScriptActivity
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptActivityUseCaseTest {
    @Test
    fun `each script shows how many widgets use it and when it last ran`() = runTest {
        val scripts = FakeScriptRepository(mapOf("clock" to "", "news" to "", "memo" to ""))
        val bindings = FakeWidgetBindingRepository(WidgetBinding(1, "clock", null), WidgetBinding(2, "clock", null), WidgetBinding(3, "news", null))
        val history = FakeRunHistory().apply {
            records.value = listOf(
                RunRecord("clock", RunSource.Widget, startedAt = 300, durationMs = 1),
                RunRecord("news", RunSource.App, startedAt = 200, durationMs = 1),
                RunRecord("clock", RunSource.Widget, startedAt = 100, durationMs = 1),
            )
        }

        val activity = ObserveScriptActivityUseCase(scripts, bindings, history)().first()

        assertEquals(
            mapOf(
                "clock" to ScriptActivity(widgets = 2, lastRunAt = 300),
                "memo" to ScriptActivity(widgets = 0, lastRunAt = null),
                "news" to ScriptActivity(widgets = 1, lastRunAt = 200),
            ),
            activity,
        )
    }
}
