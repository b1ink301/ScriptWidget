package kr.b1ink.scriptwidget.widget

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FixedClock
import kr.b1ink.scriptwidget.widget.work.ExactAlarmRefreshScheduler
import kr.b1ink.scriptwidget.widget.work.RefreshAlarmReceiver
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ExactAlarmRefreshSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val delegate = FakeRefreshScheduler()
    private val clock = FixedClock(System.currentTimeMillis())

    private fun scheduler(exactAllowed: Boolean = true) =
        ExactAlarmRefreshScheduler(context, delegate, alarms, clock, canScheduleExact = { exactAllowed })

    @After
    fun tearDown() {
        listOf(31, 32, 33, 34, 35, 36).forEach { scheduler().cancel(it) }
    }

    @Test
    fun futureRefreshesSetAnExactAlarmAndKeepTheWorkManagerFallback() {
        scheduler().refreshAt(31, clock.millis + 60_000)

        assertTrue(RefreshAlarmReceiver.isScheduled(context, 31))
        assertEquals(listOf(31 to clock.millis + 60_000), delegate.refreshAt)
    }

    /** The alarm type of a widget's refresh alarm, read from `dumpsys alarm`. */
    private fun alarmType(appWidgetId: Int): String? {
        val pfd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("dumpsys alarm")
        val dump = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
        val lines = dump.lines()
        val tag = lines.indexOfFirst { it.trim().endsWith("REFRESH_AT.$appWidgetId") && it.contains("tag=") }
        if (tag < 0) return null
        return Regex("""type=(\w+)""").find(lines[tag + 1])?.groupValues?.get(1)
    }

    @Test
    fun wakeUpAlarmsMayWakeTheDevice() {
        scheduler().refreshAt(35, clock.millis + 60_000, wakeUp = true)

        assertEquals("RTC_WAKEUP", alarmType(35))
    }

    @Test
    fun otherAlarmsWaitUntilTheDeviceIsAwake() {
        scheduler().refreshAt(36, clock.millis + 60_000, wakeUp = false)

        assertEquals("RTC", alarmType(36))
        // No WorkManager fallback: a job could run (and wake the device) while the screen is off.
        assertEquals(emptyList(), delegate.refreshAt)
    }

    @Test
    fun withoutExactAlarmPermissionNonWakingRefreshesStillUseWorkManager() {
        scheduler(exactAllowed = false).refreshAt(36, clock.millis + 60_000, wakeUp = false)

        assertEquals(listOf(36 to clock.millis + 60_000), delegate.refreshAt)
    }

    @Test
    fun withoutExactAlarmPermissionOnlyWorkManagerIsUsed() {
        scheduler(exactAllowed = false).refreshAt(32, clock.millis + 60_000)

        assertFalse(RefreshAlarmReceiver.isScheduled(context, 32))
        assertEquals(1, delegate.refreshAt.size)
    }

    @Test
    fun timesInThePastDontSetAnAlarm() {
        scheduler().refreshAt(33, clock.millis)

        assertFalse(RefreshAlarmReceiver.isScheduled(context, 33))
        assertEquals(listOf(33 to clock.millis), delegate.refreshAt)
    }

    @Test
    fun cancelRemovesTheAlarmToo() {
        scheduler().refreshAt(34, clock.millis + 60_000)

        scheduler().cancel(34)

        assertFalse(RefreshAlarmReceiver.isScheduled(context, 34))
        assertEquals(listOf(34), delegate.cancelled.distinct())
    }

    @Test
    fun otherOperationsAreDelegated() {
        scheduler().refreshNow(1)
        scheduler().schedulePeriodic(2)

        assertEquals(listOf(1), delegate.refreshed)
        assertEquals(listOf(2), delegate.scheduled)
    }

    @Test
    fun theAlarmRunsTheWidgetRightAway() {
        val intent = Intent().putExtra(RefreshAlarmReceiver.EXTRA_APP_WIDGET_ID, 7)

        RefreshAlarmReceiver.handle(intent, delegate, clock)

        assertEquals(listOf(7 to clock.millis), delegate.refreshAt)
    }

    @Test
    fun alarmsWithoutAWidgetIdAreIgnored() {
        RefreshAlarmReceiver.handle(Intent(), delegate, clock)

        assertTrue(delegate.refreshAt.isEmpty())
    }
}
