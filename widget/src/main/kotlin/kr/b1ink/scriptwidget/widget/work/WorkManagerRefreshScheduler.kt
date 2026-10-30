package kr.b1ink.scriptwidget.widget.work

import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kr.b1ink.scriptwidget.domain.model.RefreshTrigger
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler
import kr.b1ink.scriptwidget.domain.time.Clock
import java.util.concurrent.TimeUnit

class WorkManagerRefreshScheduler(
    private val workManager: WorkManager,
    private val clock: Clock = Clock.System,
) : WidgetRefreshScheduler {

    override fun refreshNow(appWidgetId: Int) {
        val request = OneTimeWorkRequestBuilder<ScriptUpdateWorker>()
            .setInputData(input(appWidgetId, RefreshTrigger.Manual))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(oneTimeName(appWidgetId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun schedulePeriodic(appWidgetId: Int) {
        val request = PeriodicWorkRequestBuilder<ScriptUpdateWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            // Binding already triggers refreshNow(); don't run the script twice right away.
            .setInitialDelay(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setInputData(input(appWidgetId, RefreshTrigger.Periodic))
            .addTag(TAG)
            .build()
        workManager.enqueueUniquePeriodicWork(periodicName(appWidgetId), ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    override fun refreshAt(appWidgetId: Int, epochMillis: Long, wakeUp: Boolean) {
        val request = OneTimeWorkRequestBuilder<ScriptUpdateWorker>()
            .setInitialDelay((epochMillis - clock.now()).coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(input(appWidgetId, RefreshTrigger.Scheduled))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(atName(appWidgetId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun runAction(appWidgetId: Int, action: String) {
        val request = OneTimeWorkRequestBuilder<ScriptUpdateWorker>()
            .setInputData(
                workDataOf(
                    ScriptUpdateWorker.KEY_APP_WIDGET_ID to appWidgetId,
                    ScriptUpdateWorker.KEY_TRIGGER to RefreshTrigger.Manual.name,
                    ScriptUpdateWorker.KEY_ACTION to action,
                )
            )
            .addTag(TAG)
            .build()
        // Appended, not replaced: every tap runs, in order (e.g. a counter tapped three times).
        workManager.enqueueUniqueWork(actionName(appWidgetId), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancel(appWidgetId: Int) {
        workManager.cancelUniqueWork(periodicName(appWidgetId))
        workManager.cancelUniqueWork(oneTimeName(appWidgetId))
        workManager.cancelUniqueWork(atName(appWidgetId))
        workManager.cancelUniqueWork(actionName(appWidgetId))
    }

    private fun input(appWidgetId: Int, trigger: RefreshTrigger): Data = workDataOf(
        ScriptUpdateWorker.KEY_APP_WIDGET_ID to appWidgetId,
        ScriptUpdateWorker.KEY_TRIGGER to trigger.name,
    )

    companion object {
        const val TAG = "ScriptWidget"
        /** Safety net (e.g. after a reboot cleared the alarms); regular refreshes come from alarms. */
        const val INTERVAL_MINUTES = 60L

        fun periodicName(appWidgetId: Int) = "script-widget-periodic-$appWidgetId"
        fun oneTimeName(appWidgetId: Int) = "script-widget-now-$appWidgetId"
        fun atName(appWidgetId: Int) = "script-widget-at-$appWidgetId"
        fun actionName(appWidgetId: Int) = "script-widget-action-$appWidgetId"
    }
}
