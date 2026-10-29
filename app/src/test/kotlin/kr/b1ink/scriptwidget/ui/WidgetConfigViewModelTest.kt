package kr.b1ink.scriptwidget.ui

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.repository.SampleScriptSource
import kr.b1ink.scriptwidget.domain.usecase.BindWidgetUseCase
import kr.b1ink.scriptwidget.domain.usecase.GetWidgetBindingUseCase
import kr.b1ink.scriptwidget.domain.usecase.ObserveScriptNamesUseCase
import kr.b1ink.scriptwidget.domain.usecase.SeedSampleScriptsUseCase
import kr.b1ink.scriptwidget.ui.config.WidgetConfigViewModel
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WidgetConfigViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val scripts = FakeScriptRepository(mapOf("a" to "", "b" to ""))
    private val bindings = FakeWidgetBindingRepository(WidgetBinding(5, "b", "KRW"))
    private val scheduler = FakeRefreshScheduler()

    private fun viewModel(appWidgetId: Int) = WidgetConfigViewModel(
        appWidgetId = appWidgetId,
        observeScriptNames = ObserveScriptNamesUseCase(scripts),
        seedSamples = SeedSampleScriptsUseCase(scripts, SampleScriptSource { emptyMap() })::invoke,
        getBinding = GetWidgetBindingUseCase(bindings),
        bindWidget = BindWidgetUseCase(bindings, scheduler),
    )

    @Test
    fun `new widget starts without a selection`() = runTest {
        val state = viewModel(9).uiState.value

        assertEquals(listOf("a", "b"), state.names)
        assertNull(state.selected)
        assertEquals("", state.parameter)
        assertFalse(state.canConfirm)
    }

    @Test
    fun `reconfiguring preselects the current binding`() = runTest {
        val state = viewModel(5).uiState.value

        assertEquals("b", state.selected)
        assertEquals("KRW", state.parameter)
    }

    @Test
    fun `confirm binds the widget and finishes`() = runTest {
        val vm = viewModel(9)
        vm.select("a")
        vm.onParameterChange("x")

        vm.confirm()

        assertEquals(WidgetBinding(9, "a", "x"), bindings.bindings[9])
        assertEquals(listOf(9), scheduler.refreshed)
        assertEquals(listOf(9), scheduler.scheduled)
        assertTrue(vm.uiState.value.done)
    }

    @Test
    fun `confirm without a selection does nothing`() = runTest {
        val vm = viewModel(9)

        vm.confirm()

        assertNull(bindings.bindings[9])
        assertFalse(vm.uiState.value.done)
    }
}
