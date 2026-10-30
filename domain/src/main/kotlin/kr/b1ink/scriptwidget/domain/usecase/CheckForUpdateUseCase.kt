package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.CancellationException
import kr.b1ink.scriptwidget.domain.model.AppVersion
import kr.b1ink.scriptwidget.domain.model.UpdateStatus
import kr.b1ink.scriptwidget.domain.repository.ReleaseSource

/** Compares the running version with the latest published release. */
class CheckForUpdateUseCase(
    private val source: ReleaseSource,
    private val currentVersion: String,
) {
    suspend operator fun invoke(): UpdateStatus {
        val release = try {
            source.latest()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return UpdateStatus.Failed(e.message ?: e.toString())
        }
        val latest = AppVersion.parse(release.version)
            ?: return UpdateStatus.Failed("unknown version ${release.version}")
        val current = AppVersion.parse(currentVersion)
        return if (current == null || latest > current) UpdateStatus.Available(currentVersion, release)
        else UpdateStatus.UpToDate(currentVersion)
    }
}
