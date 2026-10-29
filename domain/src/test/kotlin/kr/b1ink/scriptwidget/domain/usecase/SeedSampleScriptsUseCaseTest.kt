package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.repository.SampleScriptSource
import kotlin.test.Test
import kotlin.test.assertEquals

class SeedSampleScriptsUseCaseTest {
    private val samples = SampleScriptSource { mapOf("01_hello" to "hello", "02_api" to "api") }

    @Test
    fun `copies samples into an empty repository`() = runTest {
        val scripts = FakeScriptRepository()

        SeedSampleScriptsUseCase(scripts, samples)()

        assertEquals(mapOf("01_hello" to "hello", "02_api" to "api"), scripts.files)
    }

    @Test
    fun `leaves a repository with user scripts untouched`() = runTest {
        val scripts = FakeScriptRepository(mapOf("mine" to "x"))

        SeedSampleScriptsUseCase(scripts, samples)()

        assertEquals(mapOf("mine" to "x"), scripts.files)
    }
}
