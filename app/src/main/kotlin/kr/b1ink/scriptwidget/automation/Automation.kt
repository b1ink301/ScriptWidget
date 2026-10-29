package kr.b1ink.scriptwidget.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kr.b1ink.scriptwidget.container
import kr.b1ink.scriptwidget.domain.model.RunSource
import java.util.concurrent.TimeUnit

/** Whether automation apps may run scripts in the background. Off until the user turns it on. */
class AutomationSettings(private val prefs: SharedPreferences) {
    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    private companion object {
        const val KEY_ENABLED = "automation_enabled"
    }
}

/**
 * Background runs for automation apps (Tasker, MacroDroid, …):
 * send the broadcast [ACTION_RUN] with [EXTRA_SCRIPT] (and optionally [EXTRA_PARAMETER], seen as
 * `args.shortcutParameter`). When the run ends, [ACTION_RESULT] is broadcast with the script name,
 * the `Script.setShortcutOutput()` JSON as [EXTRA_OUTPUT] and any [EXTRA_ERROR].
 */
object Automation {
    const val ACTION_RUN = "kr.b1ink.scriptwidget.action.RUN_SCRIPT"
    const val ACTION_RESULT = "kr.b1ink.scriptwidget.action.SCRIPT_RESULT"
    const val EXTRA_SCRIPT = "scriptName"
    const val EXTRA_PARAMETER = "parameter"
    const val EXTRA_OUTPUT = "output"
    const val EXTRA_ERROR = "error"
    const val TAG = "automation"

    /** @return true when a run was queued. [startNow] = false delays it (tests inspect the queued work). */
    fun handle(
        context: Context,
        intent: Intent,
        settings: AutomationSettings,
        workManager: WorkManager,
        startNow: Boolean = true,
    ): Boolean {
        if (intent.action != ACTION_RUN || !settings.enabled) return false
        val script = intent.getStringExtra(EXTRA_SCRIPT)?.takeIf { it.isNotBlank() } ?: return false
        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInputData(workDataOf(EXTRA_SCRIPT to script, EXTRA_PARAMETER to intent.getStringExtra(EXTRA_PARAMETER)))
            .addTag(TAG)
            .addTag("script:$script")
            .apply { if (!startNow) setInitialDelay(1, TimeUnit.DAYS) }
            .build()
        workManager.enqueue(request)
        return true
    }
}

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Automation.handle(context, intent, context.container.automationSettings, WorkManager.getInstance(context))
    }
}

/** Runs one script headless and broadcasts the result back to the automation app. */
class AutomationWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val script = inputData.getString(Automation.EXTRA_SCRIPT) ?: return Result.failure()
        val result = applicationContext.container.runScriptByName(
            script, RunSource.Automation, inputData.getString(Automation.EXTRA_PARAMETER),
        )
        applicationContext.sendBroadcast(
            Intent(Automation.ACTION_RESULT)
                .putExtra(Automation.EXTRA_SCRIPT, script)
                .putExtra(Automation.EXTRA_OUTPUT, result.shortcutOutput)
                .putExtra(Automation.EXTRA_ERROR, result.error)
        )
        return Result.success()
    }
}
