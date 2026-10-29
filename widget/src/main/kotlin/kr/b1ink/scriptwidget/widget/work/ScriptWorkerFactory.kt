package kr.b1ink.scriptwidget.widget.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import kr.b1ink.scriptwidget.domain.usecase.RefreshWidgetUseCase

/**
 * Injects dependencies into [ScriptUpdateWorker]; returns null for other workers (default factory).
 *
 * The factory is created while WorkManager initializes, so dependencies are resolved only when a
 * worker is created: resolving them eagerly could need WorkManager itself (e.g. the scheduler).
 */
class ScriptWorkerFactory(
    private val refreshWidget: () -> RefreshWidgetUseCase,
    private val families: () -> WidgetFamilyResolver,
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        ScriptUpdateWorker::class.java.name -> ScriptUpdateWorker(appContext, workerParameters, refreshWidget(), families())
        else -> null
    }
}
