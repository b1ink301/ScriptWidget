package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveScriptNamesUseCaseTest {

    @Test
    fun `emits the current names and follows changes`() = runTest {
        val scripts = FakeScriptRepository(mapOf("b" to "", "a" to ""))
        val names = ObserveScriptNamesUseCase(scripts)()

        assertEquals(listOf("a", "b"), names.value)

        scripts.write("c", "")
        assertEquals(listOf("a", "b", "c"), names.value)
    }
}
