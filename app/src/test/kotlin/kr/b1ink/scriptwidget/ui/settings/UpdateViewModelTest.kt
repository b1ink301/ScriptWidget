package kr.b1ink.scriptwidget.ui.settings

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.model.AppRelease
import kr.b1ink.scriptwidget.domain.model.UpdateStatus
import kr.b1ink.scriptwidget.ui.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val release = AppRelease("v0.2.0", "https://page", "https://apk", "notes")

    @Test
    fun `starts idle and shows the result of a check`() = runTest {
        val vm = UpdateViewModel { UpdateStatus.Available("0.1.0", release) }
        assertEquals(UpdateUiState.Idle, vm.state.value)

        vm.check()

        assertEquals(UpdateUiState.Done(UpdateStatus.Available("0.1.0", release)), vm.state.value)
    }

    @Test
    fun `a check in progress is not started twice`() = runTest {
        val answer = CompletableDeferred<UpdateStatus>()
        var calls = 0
        val vm = UpdateViewModel { calls++; answer.await() }

        vm.check()
        vm.check()
        assertEquals(UpdateUiState.Checking, vm.state.value)

        answer.complete(UpdateStatus.UpToDate("0.2.0"))
        assertEquals(UpdateUiState.Done(UpdateStatus.UpToDate("0.2.0")), vm.state.value)
        assertEquals(1, calls)
    }

    @Test
    fun `checkOnce only checks the first time`() = runTest {
        var calls = 0
        val vm = UpdateViewModel { calls++; UpdateStatus.UpToDate("0.2.0") }

        vm.checkOnce()
        vm.checkOnce()
        vm.check()

        assertEquals(2, calls)
    }
}
