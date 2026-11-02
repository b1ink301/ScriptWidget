package kr.b1ink.scriptwidget.widget.state

import android.appwidget.AppWidgetManager
import android.content.Context
import android.util.Log
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Draws a widget once from its saved state and hands the RemoteViews to the launcher.
 *
 * `GlanceAppWidget.update()` instead starts a session (a WorkManager job) that stays alive for
 * about 45 seconds after drawing, waiting for lambda clicks. These widgets only use
 * PendingIntent-based actions (`actionRunCallback`, `actionStartActivity`), so that wait is wasted.
 */
class GlanceWidgetDrawer(context: Context, private val widget: GlanceAppWidget) {
    private val context = context.applicationContext
    private val manager = AppWidgetManager.getInstance(this.context)

    suspend fun draw(appWidgetId: Int) {
        // The widget may have been removed meanwhile.
        if (manager.getAppWidgetInfo(appWidgetId) == null) return
        // Glance allows only one composition per widget at a time.
        locks.getOrPut(appWidgetId) { Mutex() }.withLock {
            val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
            val views = withContext(composeDispatcher) {
                widget.compose(context, glanceId, options = manager.getAppWidgetOptions(appWidgetId))
            }
            manager.updateAppWidget(appWidgetId, views)
        }
    }

    /** Draws each widget; one that fails is logged and doesn't keep the others from being drawn. */
    suspend fun drawAll(appWidgetIds: Iterable<Int>) {
        drawEach(appWidgetIds, ::draw).forEach { (id, error) -> Log.w(TAG, "drawing widget $id failed", error) }
    }

    private companion object {
        const val TAG = "ScriptWidget"
        val locks = ConcurrentHashMap<Int, Mutex>()

        /**
         * Glance runs a composition on one thread (its own workers use Dispatchers.Main): it copies the
         * tree while the recomposer may still be applying changes. On a multi-threaded dispatcher that
         * copy can race with a recomposition — a ConcurrentModificationException, or a half-built tree
         * drawn as a blank widget.
         */
        val composeDispatcher = Dispatchers.Default.limitedParallelism(1)
    }
}

/** Runs [draw] for every id, even after a failure; returns the failures. Cancellation still propagates. */
internal suspend fun drawEach(appWidgetIds: Iterable<Int>, draw: suspend (Int) -> Unit): Map<Int, Exception> {
    val failures = linkedMapOf<Int, Exception>()
    for (id in appWidgetIds) {
        try {
            draw(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures[id] = e
        }
    }
    return failures
}
