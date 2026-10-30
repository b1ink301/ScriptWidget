package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.Serializable

/** What a script sees as `config` and `args`. */
data class ScriptEnvironment(
    val runsInWidget: Boolean,
    val widgetFamily: WidgetFamily = WidgetFamily.Small,
    val widgetParameter: String? = null,
    /** Namespace for files the run produces (e.g. downloaded images): "widget-<id>" or "preview". */
    val cacheKey: String = "preview",
    /** Query parameters of the `scriptwidget://run` link that started the script (`args.queryParameters`). */
    val queryParameters: Map<String, String> = emptyMap(),
    /** The `tapAction` of the widget element that was tapped (`args.widgetAction`), if any. */
    val widgetAction: String? = null,
    /** What started the run; drives `config.runs*`. */
    val source: RunSource = if (runsInWidget) RunSource.Widget else RunSource.App,
    /** Content shared to the script from another app (`args.plainTexts`, `args.urls`, …). */
    val shareInput: ShareInput = ShareInput(),
    /** The notification the user tapped to start the run (`args.notification`). */
    val notification: NotificationPayload? = null,
    /** Text an automation app passed along (`args.shortcutParameter`). */
    val shortcutParameter: String? = null,
    /** The event that started the run (`args.trigger`), e.g. "calendar"; see TriggerEvent.key. */
    val trigger: String? = null,
)

enum class RunSource { App, Widget, ShareSheet, Notification, HomeScreen, Automation, Trigger }

/** Shared content. Files and images are paths inside the script sandbox. */
data class ShareInput(
    val plainTexts: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val fileURLs: List<String> = emptyList(),
    val images: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = plainTexts.isEmpty() && urls.isEmpty() && fileURLs.isEmpty() && images.isEmpty()
}

/** A delivered notification as the script that scheduled it described it. */
data class NotificationPayload(
    val identifier: String,
    val title: String = "",
    val subtitle: String? = null,
    val body: String = "",
    val threadIdentifier: String? = null,
    /** JSON object string. */
    val userInfo: String? = null,
    val deliveredAt: Long? = null,
)

@Serializable
data class LogLine(val level: String, val message: String)

@Serializable
data class RunTimings(
    val createMs: Long = 0,
    val preludeMs: Long = 0,
    val scriptMs: Long = 0,
    val totalMs: Long = 0,
)

data class ScriptRunResult(
    val widget: WidgetRoot? = null,
    val logs: List<LogLine> = emptyList(),
    val error: String? = null,
    val timings: RunTimings = RunTimings(),
    /** JSON value passed to `Script.setShortcutOutput()`, returned to automation apps. */
    val shortcutOutput: String? = null,
) {
    val isSuccess: Boolean get() = error == null
}

/** Which script (and parameter) a home screen widget instance runs. */
data class WidgetBinding(
    val appWidgetId: Int,
    val scriptName: String,
    val parameter: String?,
)

/**
 * What a widget instance displays. On failure the last good [tree] is kept and [error] is set.
 */
@Serializable
data class RenderState(
    val scriptName: String? = null,
    val tree: WidgetRoot? = null,
    val error: String? = null,
    val updatedAt: Long = 0,
    val timings: RunTimings? = null,
    /** The family the tree was drawn for; a resize within it doesn't need a new run. */
    val family: WidgetFamily? = null,
)

sealed interface ScriptCommandResult {
    data class Success(val name: String) : ScriptCommandResult
    data object InvalidName : ScriptCommandResult
    data object AlreadyExists : ScriptCommandResult
    data object NotFound : ScriptCommandResult
}

/** Why a widget is being refreshed. Only [Periodic] refreshes honor `refreshAfterDate`. */
enum class RefreshTrigger {
    /** Tap, edit, (re)binding or size change. */
    Manual,

    /** The regular background schedule. */
    Periodic,

    /** The one-time refresh requested by `refreshAfterDate`. */
    Scheduled,

    /** A device event (calendar changed, ...): runs even shortly after the last run, but waits for the screen. */
    Event,
}
