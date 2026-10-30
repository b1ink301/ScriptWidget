package kr.b1ink.scriptwidget.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kr.b1ink.scriptwidget.widget.work.ScriptUpdateWorker
import kr.b1ink.scriptwidget.widget.work.WorkManagerRefreshScheduler
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class WorkManagerRefreshSchedulerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val ranFor = mutableListOf<Int>()
    private val triggers = mutableListOf<String?>()
    private val actions = mutableListOf<String?>()
    private val clock = kr.b1ink.scriptwidget.domain.fake.FixedClock(1_000L)
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: WorkManagerRefreshScheduler

    /** Records which widget each executed work was for instead of running scripts. */
    private inner class RecordingWorker(ctx: Context, params: WorkerParameters) : androidx.work.Worker(ctx, params) {
        override fun doWork(): Result {
            ranFor += inputData.getInt(ScriptUpdateWorker.KEY_APP_WIDGET_ID, -1)
            triggers += inputData.getString(ScriptUpdateWorker.KEY_TRIGGER)
            actions += inputData.getString(ScriptUpdateWorker.KEY_ACTION)
            return Result.success()
        }
    }

    @Before
    fun setUp() {
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    RecordingWorker(appContext, workerParameters)
            })
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
        scheduler = WorkManagerRefreshScheduler(workManager, clock)
    }

    private fun infos(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    @Test
    fun refreshNowRunsTheWorkerForThatWidget() {
        scheduler.refreshNow(7)

        assertEquals(listOf(7), ranFor)
        assertEquals(listOf<String?>("Manual"), triggers)
        assertEquals(WorkInfo.State.SUCCEEDED, infos(WorkManagerRefreshScheduler.oneTimeName(7)).single().state)
    }

    @Test
    fun schedulePeriodicEnqueuesHourlySafetyNetAndKeepsAnExistingSchedule() {
        scheduler.schedulePeriodic(3)
        val first = infos(WorkManagerRefreshScheduler.periodicName(3)).single()
        scheduler.schedulePeriodic(3)
        val second = infos(WorkManagerRefreshScheduler.periodicName(3)).single()

        assertEquals(first.id, second.id)
        assertEquals(WorkInfo.State.ENQUEUED, second.state)
        assertEquals(TimeUnit.MINUTES.toMillis(60), second.periodicityInfo!!.repeatIntervalMillis)
    }

    @Test
    fun anOlder15MinuteScheduleIsUpdatedToTheHourlySafetyNet() {
        val old = androidx.work.PeriodicWorkRequestBuilder<kr.b1ink.scriptwidget.widget.work.ScriptUpdateWorker>(15, TimeUnit.MINUTES).build()
        workManager.enqueueUniquePeriodicWork(
            WorkManagerRefreshScheduler.periodicName(8), androidx.work.ExistingPeriodicWorkPolicy.KEEP, old,
        ).result.get()

        scheduler.schedulePeriodic(8)

        assertEquals(TimeUnit.MINUTES.toMillis(60), infos(WorkManagerRefreshScheduler.periodicName(8)).single().periodicityInfo!!.repeatIntervalMillis)
    }

    @Test
    fun periodicWorkDoesNotRunImmediatelyBecauseBindingAlreadyRefreshesNow() {
        scheduler.schedulePeriodic(6)

        val info = infos(WorkManagerRefreshScheduler.periodicName(6)).single()
        assertEquals(TimeUnit.MINUTES.toMillis(60), info.initialDelayMillis)
        assertEquals(emptyList(), ranFor)
    }

    @Test
    fun cancelStopsThePeriodicWork() {
        scheduler.schedulePeriodic(4)

        scheduler.cancel(4)

        assertEquals(WorkInfo.State.CANCELLED, infos(WorkManagerRefreshScheduler.periodicName(4)).single().state)
    }

    @Test
    fun periodicWorkIsMarkedAsPeriodic() {
        scheduler.schedulePeriodic(8)

        val info = infos(WorkManagerRefreshScheduler.periodicName(8)).single()
        // Input data isn't exposed on WorkInfo; run it through the test driver to observe it.
        WorkManagerTestInitHelper.getTestDriver(context)!!.setPeriodDelayMet(info.id)
        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(info.id)
        assertEquals(listOf<String?>("Periodic"), triggers)
    }

    @Test
    fun refreshAtDelaysTheRunUntilTheRequestedTime() {
        scheduler.refreshAt(5, epochMillis = 61_000L)

        val info = infos(WorkManagerRefreshScheduler.atName(5)).single()
        assertEquals(60_000L, info.initialDelayMillis)
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(emptyList(), ranFor)

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(info.id)
        assertEquals(listOf(5), ranFor)
        assertEquals(listOf<String?>("Scheduled"), triggers)
    }

    @Test
    fun aNewRefreshAtReplacesTheOldOne() {
        scheduler.refreshAt(5, 61_000L)
        scheduler.refreshAt(5, 121_000L)

        val active = infos(WorkManagerRefreshScheduler.atName(5)).filter { it.state == WorkInfo.State.ENQUEUED }
        assertEquals(120_000L, active.single().initialDelayMillis)
    }

    @Test
    fun cancelAlsoCancelsTheScheduledRefresh() {
        scheduler.refreshAt(9, 61_000L)

        scheduler.cancel(9)

        assertEquals(WorkInfo.State.CANCELLED, infos(WorkManagerRefreshScheduler.atName(9)).single().state)
    }

    @Test
    fun actionsRunInOrderWithoutReplacingEachOther() {
        scheduler.runAction(12, "increment")
        scheduler.runAction(12, "increment")
        scheduler.runAction(12, "reset")

        assertEquals(listOf<String?>("increment", "increment", "reset"), actions)
        assertEquals(listOf(12, 12, 12), ranFor)
        assertEquals(3, infos(WorkManagerRefreshScheduler.actionName(12)).count { it.state == WorkInfo.State.SUCCEEDED })
    }
}
