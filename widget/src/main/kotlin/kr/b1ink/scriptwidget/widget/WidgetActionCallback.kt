package kr.b1ink.scriptwidget.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback

/** Tap on an element with `tapAction`: re-run the script with `args.widgetAction`. */
class WidgetActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ACTION] ?: return
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        WidgetDependencies.from(context).scheduler.runAction(appWidgetId, action)
    }

    companion object {
        val ACTION = ActionParameters.Key<String>("widgetAction")
    }
}
