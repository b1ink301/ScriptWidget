package kr.b1ink.scriptwidget.widget

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Bundle
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.widget.state.GlanceWidgetDrawer
import kr.b1ink.scriptwidget.widget.work.AppWidgetManagerFamilyResolver

class ScriptWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = ScriptWidget()

    // Follow-up work after goAsync() finishes runs in a detached scope.
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // onUpdate / onAppWidgetOptionsChanged skip super: Glance would start a session that idles ~45 s
    // per update. The widgets are drawn once from their saved state instead (see GlanceWidgetDrawer).
    @SuppressLint("MissingSuperCall")
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val drawer = GlanceWidgetDrawer(context, glanceAppWidget)
        val scheduleBound = WidgetDependencies.from(context).scheduleBoundWidgets
        val pending = goAsync()
        background.launch {
            try {
                appWidgetIds.forEach { drawer.draw(it) }
            } finally {
                pending.finish()
            }
            scheduleBound(appWidgetIds.toList())
        }
    }

    @SuppressLint("MissingSuperCall")
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        // Redraw at the new size right away; only a new family (small ↔ medium ↔ large) runs the script again.
        val drawer = GlanceWidgetDrawer(context, glanceAppWidget)
        val resize = WidgetDependencies.from(context).resizeWidget
        val family = AppWidgetManagerFamilyResolver(appWidgetManager).familyOf(appWidgetId)
        val pending = goAsync()
        background.launch {
            try {
                drawer.draw(appWidgetId)
            } finally {
                pending.finish()
            }
            resize(appWidgetId, family)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val unbind = WidgetDependencies.from(context).unbindWidgets
        background.launch { unbind(appWidgetIds.toList()) }
    }
}
