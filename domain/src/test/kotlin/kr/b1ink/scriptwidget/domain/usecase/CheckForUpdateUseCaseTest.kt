package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.model.AppRelease
import kr.b1ink.scriptwidget.domain.model.AppVersion
import kr.b1ink.scriptwidget.domain.model.UpdateStatus
import kr.b1ink.scriptwidget.domain.repository.ReleaseSource
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckForUpdateUseCaseTest {
    private fun release(version: String) =
        AppRelease(version = version, pageUrl = "https://example.com/$version", apkUrl = "https://example.com/$version.apk", notes = "notes")

    private fun source(vararg results: () -> AppRelease) = object : ReleaseSource {
        var calls = 0
        override suspend fun latest(): AppRelease = results[calls++]()
    }

    @Test
    fun `a newer release is offered`() = runTest {
        val status = CheckForUpdateUseCase(source({ release("v0.2.0") }), currentVersion = "0.1.0")()

        assertEquals(UpdateStatus.Available(current = "0.1.0", release = release("v0.2.0")), status)
    }

    @Test
    fun `the same or an older release means up to date`() = runTest {
        assertEquals(UpdateStatus.UpToDate("0.2.0"), CheckForUpdateUseCase(source({ release("v0.2.0") }), "0.2.0")())
        assertEquals(UpdateStatus.UpToDate("0.3.0"), CheckForUpdateUseCase(source({ release("v0.2.0") }), "0.3.0")())
    }

    @Test
    fun `network failures are reported, not thrown`() = runTest {
        val status = CheckForUpdateUseCase(source({ throw IOException("offline") }), "0.1.0")()

        assertIs<UpdateStatus.Failed>(status)
        assertTrue("offline" in status.message)
    }

    @Test
    fun `versions compare numerically and pre-releases come before the release`() {
        fun v(s: String) = AppVersion.parse(s)!!
        assertTrue(v("0.10.0") > v("0.9.9"))
        assertTrue(v("v1.0") == v("1.0.0"))
        assertTrue(v("0.1.0-dev") < v("0.1.0"))
        assertTrue(v("0.1.1-dev") > v("0.1.0"))
        assertEquals(null, AppVersion.parse("nightly"))
    }

    @Test
    fun `an unreadable version is a failure`() = runTest {
        assertIs<UpdateStatus.Failed>(CheckForUpdateUseCase(source({ release("nightly") }), "0.1.0")())
    }
}
