package kr.b1ink.scriptwidget.widget.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler
import kr.b1ink.scriptwidget.domain.time.Clock
import kr.b1ink.scriptwidget.widget.WidgetDependencies

/**
 * Makes `refreshAfterDate` refreshes happen on time with exact alarms (USE_EXACT_ALARM, allowed
 * because the app isn't on Google Play). WorkManager's delayed work stays scheduled as a fallback
 * (e.g. after a reboot, which clears alarms); when the alarm fires it replaces that work with an
 * immediate run, so the script runs once.
 */
class ExactAlarmRefreshScheduler(
    context: Context,
    private val delegate: WidgetRefreshScheduler,
    private val alarms: AlarmManager,
    private val clock: Clock = Clock.System,
    private val canScheduleExact: () -> Boolean = {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()
    },
) : WidgetRefreshScheduler by delegate {
    private val context = context.applicationContext

    override fun refreshAt(appWidgetId: Int, epochMillis: Long, wakeUp: Boolean) {
        val exact = canScheduleExact()
        // A non-waking refresh relies on the alarm alone: a WorkManager job could run (and wake the
        // device) while the screen is off. Without exact alarms, WorkManager is the only option.
        if (wakeUp || !exact || epochMillis <= clock.now()) delegate.refreshAt(appWidgetId, epochMillis, wakeUp)
        if (epochMillis <= clock.now() || !exact) return
        try {
            val operation = RefreshAlarmReceiver.pendingIntent(context, appWidgetId)
            // A non-wakeup alarm doesn't turn the CPU on: it fires once the device is awake (e.g. screen on).
            if (wakeUp) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epochMillis, operation)
            else alarms.setExact(AlarmManager.RTC, epochMillis, operation)
        } catch (e: SecurityException) {
            Log.w(WorkManagerRefreshScheduler.TAG, "exact alarm not allowed; relying on WorkManager", e)
        }
    }

    override fun cancel(appWidgetId: Int) {
        delegate.cancel(appWidgetId)
        RefreshAlarmReceiver.existingPendingIntent(context, appWidgetId)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }
}

/** Fires at a widget's `refreshAfterDate` and runs it immediately. */
class RefreshAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        handle(intent, WidgetDependencies.from(context).scheduler, Clock.System)
    }

    companion object {
        const val EXTRA_APP_WIDGET_ID = "appWidgetId"
        private const val ACTION = "kr.b1ink.scriptwidget.widget.REFRESH_AT"

        /** A refresh "at now" replaces the pending WorkManager fallback with an immediate run. */
        fun handle(intent: Intent, scheduler: WidgetRefreshScheduler, clock: Clock) {
            val appWidgetId = intent.getIntExtra(EXTRA_APP_WIDGET_ID, -1)
            if (appWidgetId < 0) return
            scheduler.refreshAt(appWidgetId, clock.now())
        }

        fun pendingIntent(context: Context, appWidgetId: Int): PendingIntent =
            PendingIntent.getBroadcast(
                context, appWidgetId, intent(context, appWidgetId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun existingPendingIntent(context: Context, appWidgetId: Int): PendingIntent? =
            PendingIntent.getBroadcast(
                context, appWidgetId, intent(context, appWidgetId),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )

        fun isScheduled(context: Context, appWidgetId: Int): Boolean = existingPendingIntent(context, appWidgetId) != null

        private fun intent(context: Context, appWidgetId: Int) =
            Intent(context, RefreshAlarmReceiver::class.java)
                .setAction("$ACTION.$appWidgetId")
                .putExtra(EXTRA_APP_WIDGET_ID, appWidgetId)
    }
}
