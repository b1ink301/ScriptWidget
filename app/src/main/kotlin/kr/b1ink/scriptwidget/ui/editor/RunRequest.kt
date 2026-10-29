package kr.b1ink.scriptwidget.ui.editor

import kr.b1ink.scriptwidget.domain.model.NotificationPayload
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.domain.model.ShareInput

/** Why and with what a script is run from the app (link, notification, share sheet, …). */
data class RunRequest(
    val queryParameters: Map<String, String> = emptyMap(),
    val source: RunSource = RunSource.App,
    val shareInput: ShareInput = ShareInput(),
    val notification: NotificationPayload? = null,
    val shortcutParameter: String? = null,
)
