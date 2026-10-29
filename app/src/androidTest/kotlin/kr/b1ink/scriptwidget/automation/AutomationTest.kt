package kr.b1ink.scriptwidget.automation

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.ui.editor.RunRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AutomationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val settings = AutomationSettings(context.getSharedPreferences("automation-test", Context.MODE_PRIVATE))
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        settings.enabled = false
    }

    private fun runIntent(script: String?, parameter: String? = null) = Intent(Automation.ACTION_RUN)
        .apply { script?.let { putExtra(Automation.EXTRA_SCRIPT, it) } }
        .apply { parameter?.let { putExtra(Automation.EXTRA_PARAMETER, it) } }

    @Test
    fun ignoredUntilTheUserAllowsAutomation() {
        assertFalse(settings.enabled)
        assertFalse(Automation.handle(context, runIntent("lights"), settings, workManager))
    }

    @Test
    fun queuesABackgroundRunForTheScript() {
        settings.enabled = true

        assertTrue(Automation.handle(context, runIntent("lights", "on"), settings, workManager, startNow = false))

        val work = workManager.getWorkInfosByTag(Automation.TAG).get().last()
        assertTrue("script:lights" in work.tags)
    }

    @Test
    fun requestsWithoutAScriptAreIgnored() {
        settings.enabled = true
        assertFalse(Automation.handle(context, runIntent(null), settings, workManager))
    }

    @Test
    fun homeScreenShortcutsRunTheScriptInTheApp() {
        val intent = ScriptShortcuts.intent(context, "Weather")

        assertEquals("Weather" to RunRequest(source = RunSource.HomeScreen), ScriptShortcuts.parse(intent))
        assertNull(ScriptShortcuts.parse(Intent(Intent.ACTION_MAIN)))
    }

    @Test
    fun theWorkerBroadcastsTheResultBack() {
        val received = java.util.concurrent.LinkedBlockingQueue<Intent>()
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                received += intent
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver, android.content.IntentFilter(Automation.ACTION_RESULT), androidx.core.content.ContextCompat.RECEIVER_EXPORTED,
        )
        try {
            val worker = androidx.work.testing.TestListenableWorkerBuilder<AutomationWorker>(context)
                .setInputData(androidx.work.workDataOf(Automation.EXTRA_SCRIPT to "no such script for automation test"))
                .build()
            kotlinx.coroutines.runBlocking { worker.doWork() }

            val result = received.poll(5, java.util.concurrent.TimeUnit.SECONDS)!!
            assertEquals("no such script for automation test", result.getStringExtra(Automation.EXTRA_SCRIPT))
            assertEquals("Script not found: no such script for automation test", result.getStringExtra(Automation.EXTRA_ERROR))
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
}
