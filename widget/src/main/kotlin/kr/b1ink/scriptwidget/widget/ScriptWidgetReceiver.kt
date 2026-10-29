package kr.b1ink.scriptwidget.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.widget.work.AppWidgetManagerFamilyResolver

class ScriptWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ScriptWidget()

    // GlanceAppWidgetReceiver already uses goAsync(), so short follow-up work runs in a detached scope.
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val scheduleBound = WidgetDependencies.from(context).scheduleBoundWidgets
        background.launch { scheduleBound(appWidgetIds.toList()) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // Only a new family (small ↔ medium ↔ large) needs the script to run again; otherwise just redraw.
        val resize = WidgetDependencies.from(context).resizeWidget
        val family = AppWidgetManagerFamilyResolver(appWidgetManager).familyOf(appWidgetId)
        background.launch { resize(appWidgetId, family) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val unbind = WidgetDependencies.from(context).unbindWidgets
        background.launch { unbind(appWidgetIds.toList()) }
    }
}
