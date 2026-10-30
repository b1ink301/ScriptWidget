package kr.b1ink.scriptwidget.widget

import android.content.Context
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kr.b1ink.scriptwidget.domain.model.TriggerEvent
import kr.b1ink.scriptwidget.widget.work.AndroidEventMonitor
import kr.b1ink.scriptwidget.widget.work.EventWorker
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidEventMonitorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var workManager: WorkManager
    private lateinit var monitor: AndroidEventMonitor
    private val handled = mutableListOf<TriggerEvent>()

    @Before
    fun setUp() {
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    EventWorker(appContext, workerParameters, handle = { handled += it }, rearm = { monitor.rearm(it) })
            })
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        monitor = AndroidEventMonitor(context, workManager)
    }

    @After
    fun tearDown() = monitor.watch(emptySet())

    private fun active(event: TriggerEvent): WorkInfo? =
        workManager.getWorkInfosForUniqueWork(AndroidEventMonitor.workName(event)).get().singleOrNull { !it.state.isFinished }

    @Test
    fun contentEventsWaitForChangesToTheirProvider() {
        monitor.watch(setOf(TriggerEvent.CalendarChanged, TriggerEvent.ContactsChanged, TriggerEvent.NewPhoto))

        assertEquals(CalendarContract.Events.CONTENT_URI, active(TriggerEvent.CalendarChanged)!!.constraints.contentUriTriggers.single().uri)
        assertEquals(ContactsContract.Contacts.CONTENT_URI, active(TriggerEvent.ContactsChanged)!!.constraints.contentUriTriggers.single().uri)
        assertEquals(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, active(TriggerEvent.NewPhoto)!!.constraints.contentUriTriggers.single().uri)
    }

    @Test
    fun chargingAndWifiWaitForTheirCondition() {
        monitor.watch(setOf(TriggerEvent.Charging, TriggerEvent.WifiConnected))

        assertTrue(active(TriggerEvent.Charging)!!.constraints.requiresCharging())
        assertEquals(NetworkType.UNMETERED, active(TriggerEvent.WifiConnected)!!.constraints.requiredNetworkType)
    }

    @Test
    fun eventsNoLongerWatchedAreReleased() {
        monitor.watch(setOf(TriggerEvent.CalendarChanged, TriggerEvent.DayChanged))
        assertTrue(monitor.isDayAlarmSet())

        monitor.watch(setOf(TriggerEvent.Charging))

        assertEquals(null, active(TriggerEvent.CalendarChanged))
        assertFalse(monitor.isDayAlarmSet())
        assertTrue(active(TriggerEvent.Charging) != null)
    }

    @Test
    fun watchingAgainKeepsWaitingWorkInsteadOfRestartingIt() {
        monitor.watch(setOf(TriggerEvent.CalendarChanged))
        val first = active(TriggerEvent.CalendarChanged)!!.id

        monitor.watch(setOf(TriggerEvent.CalendarChanged))

        assertEquals(first, active(TriggerEvent.CalendarChanged)!!.id)
    }

    @Test
    fun aFiredEventIsHandledAndWatchedAgain() {
        monitor.watch(setOf(TriggerEvent.CalendarChanged))
        val first = active(TriggerEvent.CalendarChanged)!!.id

        WorkManagerTestInitHelper.getTestDriver(context)!!.setAllConstraintsMet(first)

        assertEquals(listOf(TriggerEvent.CalendarChanged), handled)
        val next = active(TriggerEvent.CalendarChanged)!!
        assertTrue(next.id != first)
    }

    @Test
    fun conditionEventsWaitAnHourBeforeFiringAgain() {
        monitor.rearm(TriggerEvent.Charging)

        assertEquals(60 * 60_000L, active(TriggerEvent.Charging)!!.initialDelayMillis)
    }
}
