package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.content.Intent
import kr.b1ink.scriptwidget.data.engine.NotificationSpec
import kr.b1ink.scriptwidget.domain.model.NotificationPayload

/** The intent a tapped script notification sends to the app, which runs [EXTRA_SCRIPT] with `args.notification`. */
object NotificationTap {
    const val ACTION = "kr.b1ink.scriptwidget.action.NOTIFICATION_TAPPED"
    const val EXTRA_SCRIPT = "scriptName"
    private const val EXTRA_ID = "identifier"
    private const val EXTRA_TITLE = "title"
    private const val EXTRA_SUBTITLE = "subtitle"
    private const val EXTRA_BODY = "body"
    private const val EXTRA_THREAD = "threadIdentifier"
    private const val EXTRA_USER_INFO = "userInfo"
    private const val EXTRA_DELIVERED_AT = "deliveredAt"

    fun intent(context: Context, scriptName: String, spec: NotificationSpec, deliveredAt: Long): Intent =
        Intent(ACTION)
            .setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SCRIPT, scriptName)
            .putExtra(EXTRA_ID, spec.identifier)
            .putExtra(EXTRA_TITLE, spec.title)
            .putExtra(EXTRA_SUBTITLE, spec.subtitle)
            .putExtra(EXTRA_BODY, spec.body)
            .putExtra(EXTRA_THREAD, spec.threadIdentifier)
            .putExtra(EXTRA_USER_INFO, spec.userInfo)
            .putExtra(EXTRA_DELIVERED_AT, deliveredAt)

    /** The script to run and the notification it gets, or null for any other intent. */
    fun parse(intent: Intent?): Pair<String, NotificationPayload>? {
        if (intent?.action != ACTION) return null
        val script = intent.getStringExtra(EXTRA_SCRIPT) ?: return null
        return script to NotificationPayload(
            identifier = intent.getStringExtra(EXTRA_ID).orEmpty(),
            title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            subtitle = intent.getStringExtra(EXTRA_SUBTITLE),
            body = intent.getStringExtra(EXTRA_BODY).orEmpty(),
            threadIdentifier = intent.getStringExtra(EXTRA_THREAD),
            userInfo = intent.getStringExtra(EXTRA_USER_INFO),
            deliveredAt = intent.getLongExtra(EXTRA_DELIVERED_AT, 0L).takeIf { it > 0 },
        )
    }
}
