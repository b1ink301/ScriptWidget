package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.model.ScriptAppearance
import kr.b1ink.scriptwidget.domain.model.ScriptSummary
import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptAppearanceUseCaseTest {
    private val scripts = FakeScriptRepository(mapOf("a" to "// icon-color: red; icon-glyph: bolt;\nx", "b" to "y"))

    @Test
    fun `scripts are listed with their appearance`() = runTest {
        assertEquals(
            listOf(ScriptSummary("a", ScriptAppearance("red", "bolt")), ScriptSummary("b", null)),
            ObserveScriptSummariesUseCase(scripts)().first(),
        )
    }

    @Test
    fun `setting the appearance rewrites only the header`() = runTest {
        SetScriptAppearanceUseCase(scripts)("b", ScriptAppearance("teal", "leaf"))

        assertEquals(ScriptAppearance("teal", "leaf"), ScriptAppearance.parse(scripts.files["b"]!!))
        assertEquals("y", scripts.files["b"]!!.lines().last())
        assertEquals(ScriptAppearance("teal", "leaf"), ObserveScriptSummariesUseCase(scripts)().first().last().appearance)
    }
}
