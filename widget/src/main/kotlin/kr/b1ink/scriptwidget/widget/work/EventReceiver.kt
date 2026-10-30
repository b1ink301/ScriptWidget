package kr.b1ink.scriptwidget.widget.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import kr.b1ink.scriptwidget.widget.WidgetDependencies

/** The midnight alarm, and time zone / clock changes (broadcasts apps may still receive in the manifest). */
class EventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = when (intent.action) {
            ACTION_NEW_DAY -> TriggerEvent.DayChanged
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> TriggerEvent.TimeZoneChanged
            else -> return
        }
        val dependencies = WidgetDependencies.from(context)
        val monitor = AndroidEventMonitor(context)
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                dependencies.handleEvent(event)
                // A new day, or "midnight" moved with the clock: aim the alarm at the next one.
                if (event == TriggerEvent.DayChanged || monitor.isDayAlarmSet()) monitor.rearm(TriggerEvent.DayChanged)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_NEW_DAY = "kr.b1ink.scriptwidget.action.NEW_DAY"
    }
}
