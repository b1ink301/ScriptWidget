package kr.b1ink.scriptwidget.ui.history

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRunHistory
import kr.b1ink.scriptwidget.domain.model.LogLine
import kr.b1ink.scriptwidget.domain.model.RunRecord
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.ui.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunHistoryViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val history = FakeRunHistory().apply {
        records.value = listOf(
            RunRecord("Weather", RunSource.Widget, startedAt = 2_000, durationMs = 900, error = "TypeError: x is undefined\n  at L3", widgetId = 4),
            RunRecord("Hello", RunSource.App, startedAt = 1_000, durationMs = 12, logs = listOf(LogLine("log", "hi"))),
        )
    }

    @Test
    fun `rows show the outcome, where it ran and how long it took`() = runTest {
        val vm = RunHistoryViewModel(history)

        val rows = vm.uiState.value.rows
        assertEquals(listOf("Weather", "Hello"), rows.map { it.scriptName })
        assertEquals(listOf(false, true), rows.map { it.success })
        assertEquals("위젯 #4", rows[0].sourceLabel)
        assertEquals("앱", rows[1].sourceLabel)
        assertEquals("TypeError: x is undefined", rows[0].summary)
        assertEquals("hi", rows[1].summary)
        assertEquals("900ms", rows[0].duration)
    }

    @Test
    fun `failures only filter`() = runTest {
        val vm = RunHistoryViewModel(history)

        vm.setFailuresOnly(true)

        assertEquals(listOf("Weather"), vm.uiState.value.rows.map { it.scriptName })
        assertTrue(vm.uiState.value.failuresOnly)
    }

    @Test
    fun `clear removes everything`() = runTest {
        val vm = RunHistoryViewModel(history)

        vm.clear()

        assertTrue(vm.uiState.value.rows.isEmpty())
    }
}
