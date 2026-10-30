package kr.b1ink.scriptwidget.data.update

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.FakeHttpGateway
import kr.b1ink.scriptwidget.data.engine.HttpResponseSpec
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GitHubReleaseSourceTest {
    private val latest = """
        {"tag_name":"v0.2.0","html_url":"https://github.com/o/r/releases/tag/v0.2.0","body":"- 새 기능","draft":false,
         "assets":[
           {"name":"SHA256SUMS.txt","browser_download_url":"https://dl/SHA256SUMS.txt"},
           {"name":"ScriptWidget-0.2.0-armeabi-v7a.apk","browser_download_url":"https://dl/v7a.apk"},
           {"name":"ScriptWidget-0.2.0-arm64-v8a.apk","browser_download_url":"https://dl/arm64.apk"},
           {"name":"ScriptWidget-0.2.0-universal.apk","browser_download_url":"https://dl/universal.apk"}
         ]}
    """.trimIndent()

    private fun gateway(status: Int = 200, body: String = latest) = FakeHttpGateway().apply {
        responses = { HttpResponseSpec(it.url, status, emptyMap(), "application/json", body) }
    }

    @Test
    fun `reads the latest release and picks the APK for this device`() = runTest {
        val http = gateway()
        val release = GitHubReleaseSource(http, supportedAbis = listOf("arm64-v8a", "armeabi-v7a")).latest()

        assertEquals("v0.2.0", release.version)
        assertEquals("https://github.com/o/r/releases/tag/v0.2.0", release.pageUrl)
        assertEquals("https://dl/arm64.apk", release.apkUrl)
        assertEquals("- 새 기능", release.notes)
        assertEquals(GitHubReleaseSource.LATEST_URL, http.requests.single().url)
        assertEquals("application/vnd.github+json", http.requests.single().headers["Accept"])
    }

    @Test
    fun `falls back to the universal APK, then to none`() = runTest {
        assertEquals("https://dl/universal.apk", GitHubReleaseSource(gateway(), supportedAbis = listOf("riscv64")).latest().apkUrl)

        val noApk = """{"tag_name":"v0.2.0","html_url":"https://x","assets":[{"name":"SHA256SUMS.txt","browser_download_url":"https://dl/s"}]}"""
        assertEquals(null, GitHubReleaseSource(gateway(body = noApk), supportedAbis = listOf("arm64-v8a")).latest().apkUrl)
    }

    @Test
    fun `http errors are reported`() = runTest {
        val error = assertFailsWith<IOException> { GitHubReleaseSource(gateway(status = 404, body = "{}"), emptyList()).latest() }
        assertContains(error.message.orEmpty(), "404")
    }
}
