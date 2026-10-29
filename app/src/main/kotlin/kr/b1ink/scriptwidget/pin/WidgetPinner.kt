package kr.b1ink.scriptwidget.pin

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.container
import kr.b1ink.scriptwidget.widget.ScriptWidgetReceiver

/** Pins a widget for a script straight from the editor (`AppWidgetManager.requestPinAppWidget`). */
object WidgetPinner {
    const val EXTRA_SCRIPT = "script"
    const val EXTRA_PARAMETER = "parameter"

    fun isSupported(context: Context): Boolean =
        AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

    fun pin(context: Context, scriptName: String, parameter: String?): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val callback = Intent(context, PinnedWidgetReceiver::class.java)
            .putExtra(EXTRA_SCRIPT, scriptName)
            .putExtra(EXTRA_PARAMETER, parameter)
        // Mutable: the launcher adds EXTRA_APPWIDGET_ID to this intent.
        val pending = PendingIntent.getBroadcast(
            context,
            scriptName.hashCode(),
            callback,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        return manager.requestPinAppWidget(ComponentName(context, ScriptWidgetReceiver::class.java), null, pending)
    }
}

/** Receives the id of the newly pinned widget and binds it to the script. */
class PinnedWidgetReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val script = intent.getStringExtra(WidgetPinner.EXTRA_SCRIPT)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID || script == null) return
        val parameter = intent.getStringExtra(WidgetPinner.EXTRA_PARAMETER)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                context.container.bindWidget(appWidgetId, script, parameter)
            } finally {
                pending.finish()
            }
        }
    }
}
