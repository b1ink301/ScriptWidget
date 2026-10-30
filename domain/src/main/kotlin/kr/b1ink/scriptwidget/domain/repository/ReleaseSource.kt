package kr.b1ink.scriptwidget.domain.repository

import kr.b1ink.scriptwidget.domain.model.AppRelease

/** Where new app versions are published. */
interface ReleaseSource {
    /** @throws Exception when the latest release can't be loaded. */
    suspend fun latest(): AppRelease
}
