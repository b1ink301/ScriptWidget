package kr.b1ink.scriptwidget.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.data.engine.HttpGateway
import kr.b1ink.scriptwidget.data.engine.HttpRequestSpec
import kr.b1ink.scriptwidget.domain.model.AppRelease
import kr.b1ink.scriptwidget.domain.repository.ReleaseSource
import java.io.IOException

/** The latest GitHub release of this app, with the APK that fits [supportedAbis] (best first). */
class GitHubReleaseSource(
    private val http: HttpGateway,
    private val supportedAbis: List<String>,
    private val url: String = LATEST_URL,
) : ReleaseSource {

    override suspend fun latest(): AppRelease {
        val response = http.loadString(
            HttpRequestSpec(url = url, headers = mapOf("Accept" to "application/vnd.github+json"), timeout = 15.0)
        )
        if (response.statusCode !in 200..299) throw IOException("HTTP ${response.statusCode}: $url")
        val release = json.decodeFromString(Release.serializer(), response.body)
        val apks = release.assets.filter { it.name.endsWith(".apk") }
        val apk = supportedAbis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.name.endsWith("-$abi.apk") } }
            ?: apks.firstOrNull { it.name.endsWith("-universal.apk") }
        return AppRelease(version = release.tagName, pageUrl = release.htmlUrl, apkUrl = apk?.downloadUrl, notes = release.body.orEmpty())
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val body: String? = null,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val downloadUrl: String)

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/b1ink301/ScriptWidget/releases/latest"
        private val json = Json { ignoreUnknownKeys = true }
    }
}
