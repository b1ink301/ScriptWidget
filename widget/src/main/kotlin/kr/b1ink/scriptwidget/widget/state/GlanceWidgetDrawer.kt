package kr.b1ink.scriptwidget.widget.state

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.compose
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
            val views = widget.compose(context, glanceId, options = manager.getAppWidgetOptions(appWidgetId))
            manager.updateAppWidget(appWidgetId, views)
        }
    }

    private companion object {
        val locks = ConcurrentHashMap<Int, Mutex>()
    }
}
