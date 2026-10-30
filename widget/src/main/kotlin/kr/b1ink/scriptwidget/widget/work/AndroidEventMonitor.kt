package kr.b1ink.scriptwidget.widget.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import kr.b1ink.scriptwidget.domain.scheduler.EventMonitor
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Event triggers without a running service:
 * - calendar, contacts, photos: WorkManager content URI triggers (one-shot, re-armed after each change)
 * - charging, Wi-Fi: WorkManager constraints, then an hour's pause before the next run
 * - a new day: a non-waking alarm at midnight; time zone / clock changes: [EventReceiver]
 */
class AndroidEventMonitor(
    context: Context,
    private val workManager: WorkManager = WorkManager.getInstance(context),
) : EventMonitor {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(AlarmManager::class.java)

    override fun watch(events: Set<TriggerEvent>) {
        for (event in WORK_EVENTS) {
            if (event in events) enqueue(event, ExistingWorkPolicy.KEEP, delayMinutes = 0)
            else workManager.cancelUniqueWork(workName(event))
        }
        if (TriggerEvent.DayChanged in events) setDayAlarm() else cancelDayAlarm()
    }

    /** After an event fired: wait for the next change (content) or pause an hour (conditions). */
    fun rearm(event: TriggerEvent) {
        when (event) {
            in CONTENT_URIS -> enqueue(event, ExistingWorkPolicy.REPLACE, delayMinutes = 0)
            TriggerEvent.Charging, TriggerEvent.WifiConnected -> enqueue(event, ExistingWorkPolicy.REPLACE, delayMinutes = CONDITION_PAUSE_MINUTES)
            TriggerEvent.DayChanged -> setDayAlarm()
            else -> Unit
        }
    }

    fun isDayAlarmSet(): Boolean = dayIntent(PendingIntent.FLAG_NO_CREATE) != null

    private fun enqueue(event: TriggerEvent, policy: ExistingWorkPolicy, delayMinutes: Long) {
        val constraints = Constraints.Builder().apply {
            CONTENT_URIS[event]?.let { uri ->
                addContentUriTrigger(uri, true)
                // Many edits in a row (a calendar sync) become one run.
                setTriggerContentUpdateDelay(5, TimeUnit.SECONDS)
                setTriggerContentMaxDelay(1, TimeUnit.MINUTES)
            }
            if (event == TriggerEvent.Charging) setRequiresCharging(true)
            if (event == TriggerEvent.WifiConnected) setRequiredNetworkType(NetworkType.UNMETERED)
        }.build()
        val request = OneTimeWorkRequestBuilder<EventWorker>()
            .setConstraints(constraints)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(EventWorker.KEY_EVENT to event.key))
            .addTag(WorkManagerRefreshScheduler.TAG)
            .build()
        workManager.enqueueUniqueWork(workName(event), policy, request)
    }

    private fun setDayAlarm() {
        val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // RTC (not RTC_WAKEUP): with the screen off it fires at the next wake-up, which is soon enough.
        alarms.set(AlarmManager.RTC, midnight, dayIntent(PendingIntent.FLAG_UPDATE_CURRENT)!!)
    }

    private fun cancelDayAlarm() {
        dayIntent(PendingIntent.FLAG_NO_CREATE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun dayIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        DAY_REQUEST_CODE,
        Intent(context, EventReceiver::class.java).setAction(EventReceiver.ACTION_NEW_DAY),
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val CONDITION_PAUSE_MINUTES = 60L
        private const val DAY_REQUEST_CODE = 0xDA7

        private val CONTENT_URIS: Map<TriggerEvent, Uri> = mapOf(
            TriggerEvent.CalendarChanged to CalendarContract.Events.CONTENT_URI,
            TriggerEvent.ContactsChanged to ContactsContract.Contacts.CONTENT_URI,
            TriggerEvent.NewPhoto to MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        )
        private val WORK_EVENTS = CONTENT_URIS.keys + TriggerEvent.Charging + TriggerEvent.WifiConnected

        fun workName(event: TriggerEvent) = "script-event-${event.key}"
    }
}
