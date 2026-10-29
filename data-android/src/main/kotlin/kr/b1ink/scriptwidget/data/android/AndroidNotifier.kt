package kr.b1ink.scriptwidget.data.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.data.engine.NotificationSpec
import kr.b1ink.scriptwidget.data.engine.Notifier
import java.util.concurrent.TimeUnit

/**
 * Posts script notifications; future ones are delivered by a delayed [NotificationWorker].
 * [workManager] is resolved on demand: WorkManager's on-demand initialization may itself depend
 * on objects that own this notifier.
 */
class AndroidNotifier(
    context: Context,
    private val workManager: () -> WorkManager,
    private val hasPermission: () -> Boolean = { canPost(context) },
    private val now: () -> Long = System::currentTimeMillis,
) : Notifier {
    private val context = context.applicationContext

    override fun schedule(spec: NotificationSpec) {
        if (!hasPermission()) throw SecurityException("Notification permission not granted")
        val delay = (spec.triggerAt ?: 0L) - now()
        if (delay <= 0) {
            pendingStore(context).edit().remove(spec.identifier).apply()
            post(context, spec)
            return
        }
        // WorkManager can't hand back a request's input, so remember what is scheduled for allPending().
        pendingStore(context).edit().putString(spec.identifier, Json.encodeToString(NotificationSpec.serializer(), spec)).apply()
        val request = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(NotificationWorker.KEY_SPEC to Json.encodeToString(NotificationSpec.serializer(), spec)))
            .build()
        workManager().enqueueUniqueWork(workName(spec.identifier), ExistingWorkPolicy.REPLACE, request)
    }

    override fun remove(identifiers: List<String>) {
        val manager = NotificationManagerCompat.from(context)
        val work = workManager()
        val pending = pendingStore(context).edit()
        identifiers.forEach {
            work.cancelUniqueWork(workName(it))
            manager.cancel(TAG, it.hashCode())
            pending.remove(it)
        }
        pending.apply()
    }

    override fun pending(): List<NotificationSpec> {
        val store = pendingStore(context)
        val specs = store.all.values.mapNotNull { (it as? String)?.let(::decode) }
        // Entries whose time has passed were delivered (or dropped by the system).
        val (future, past) = specs.partition { (it.triggerAt ?: 0L) > now() }
        if (past.isNotEmpty()) store.edit().apply { past.forEach { remove(it.identifier) } }.apply()
        return future.sortedBy { it.triggerAt }
    }

    override fun delivered(): List<NotificationSpec> =
        context.getSystemService(NotificationManager::class.java).activeNotifications
            .filter { it.tag == TAG }
            .mapNotNull { it.notification.extras.getString(EXTRA_SPEC)?.let(::decode) }

    companion object {
        private const val CHANNEL_ID = "scripts"
        private const val EXTRA_SPEC = "kr.b1ink.scriptwidget.notification.spec"

        private fun pendingStore(context: Context) =
            context.getSharedPreferences("script_notifications_pending", Context.MODE_PRIVATE)

        private fun decode(json: String): NotificationSpec? =
            runCatching { Json.decodeFromString(NotificationSpec.serializer(), json) }.getOrNull()
        private const val TAG = "script"

        fun workName(identifier: String) = "script-notification-$identifier"

        fun canPost(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        /**
         * What tapping the notification does: open its `openURL`, otherwise run the script that
         * scheduled it (with `args.notification`), otherwise just open the app.
         */
        fun tapIntent(context: Context, spec: NotificationSpec, deliveredAt: Long): Intent {
            spec.openURL?.let { return Intent(Intent.ACTION_VIEW, Uri.parse(it)) }
            val script = spec.scriptName
            if (script != null) return NotificationTap.intent(context, script, spec, deliveredAt)
            return context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent().setPackage(context.packageName)
        }

        /** The delayed worker's entry point: no longer pending, now shown. */
        internal fun delivered(context: Context, spec: NotificationSpec) {
            pendingStore(context).edit().remove(spec.identifier).apply()
            post(context, spec)
        }

        @SuppressLint("MissingPermission") // Checked by schedule(); the worker re-checks below.
        internal fun post(context: Context, spec: NotificationSpec) {
            if (!canPost(context)) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Script notifications", NotificationManager.IMPORTANCE_DEFAULT)
            )
            val contentIntent = PendingIntent.getActivity(
                context, spec.identifier.hashCode(), tapIntent(context, spec, deliveredAt = System.currentTimeMillis()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_script_notification)
                .setContentTitle(spec.title)
                .setContentText(spec.body)
                .setSubText(spec.subtitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(spec.body))
                .setGroup(spec.threadIdentifier)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .addExtras(android.os.Bundle().apply { putString(EXTRA_SPEC, Json.encodeToString(NotificationSpec.serializer(), spec)) })
            spec.actions.forEachIndexed { index, action ->
                val open = PendingIntent.getActivity(
                    context, (spec.identifier + "#" + index).hashCode(), Intent(Intent.ACTION_VIEW, Uri.parse(action.url)),
                    PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, action.title, open)
            }
            val notification = builder.build()
            NotificationManagerCompat.from(context).notify(TAG, spec.identifier.hashCode(), notification)
        }
    }
}

/** Delivers a notification whose trigger date has come. */
class NotificationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val spec = inputData.getString(KEY_SPEC)
            ?.let { runCatching { Json.decodeFromString(NotificationSpec.serializer(), it) }.getOrNull() }
            ?: return Result.failure()
        AndroidNotifier.delivered(applicationContext, spec)
        return Result.success()
    }

    companion object {
        const val KEY_SPEC = "spec"
    }
}
