package kr.b1ink.scriptwidget.widget.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kr.b1ink.scriptwidget.domain.model.TriggerEvent

/** A watched event happened: run its triggers, then watch for the next one. Created by [ScriptWorkerFactory]. */
class EventWorker(
    context: Context,
    params: WorkerParameters,
    private val handle: suspend (TriggerEvent) -> Unit,
    private val rearm: (TriggerEvent) -> Unit,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val event = inputData.getString(KEY_EVENT)?.let(TriggerEvent::fromKey) ?: return Result.failure()
        try {
            Log.i(WorkManagerRefreshScheduler.TAG, "event=${event.key}")
            handle(event)
        } finally {
            rearm(event)
        }
        return Result.success()
    }

    companion object {
        const val KEY_EVENT = "event"
    }
}
