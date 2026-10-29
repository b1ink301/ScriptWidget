package kr.b1ink.scriptwidget.widget.work

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kr.b1ink.scriptwidget.domain.model.RefreshTrigger
import kr.b1ink.scriptwidget.domain.usecase.RefreshWidgetUseCase

/** Refreshes one widget. Created by [ScriptWorkerFactory]. */
class ScriptUpdateWorker(
    context: Context,
    params: WorkerParameters,
    private val refreshWidget: RefreshWidgetUseCase,
    private val families: WidgetFamilyResolver,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val appWidgetId = inputData.getInt(KEY_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return Result.failure()

        val trigger = inputData.getString(KEY_TRIGGER)
            ?.let { name -> RefreshTrigger.entries.firstOrNull { it.name == name } }
            ?: RefreshTrigger.Manual
        val action = inputData.getString(KEY_ACTION)
        val state = refreshWidget(appWidgetId, families.familyOf(appWidgetId), trigger, action)
        Log.i(
            WorkManagerRefreshScheduler.TAG,
            "widget=$appWidgetId trigger=$trigger action=$action script=${state.scriptName} ok=${state.error == null} timings=${state.timings}" +
                (state.error?.let { " error=$it" } ?: ""),
        )
        // Script errors are shown on the widget itself; the work always succeeds.
        return Result.success()
    }

    companion object {
        const val KEY_APP_WIDGET_ID = "appWidgetId"
        const val KEY_TRIGGER = "trigger"
        const val KEY_ACTION = "action"
    }
}
