package kr.b1ink.scriptwidget.data.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kr.b1ink.scriptwidget.data.engine.NotificationAction
import kr.b1ink.scriptwidget.data.engine.NotificationSpec
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidNotifierTest {
    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private lateinit var workManager: WorkManager
    private lateinit var notifier: AndroidNotifier

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        notifier = AndroidNotifier(context, { workManager }, now = { 1_000L })
    }

    @After
    fun tearDown() {
        manager.cancelAll()
    }

    private fun isActive(title: String) =
        manager.activeNotifications.any { it.notification.extras.getString(Notification.EXTRA_TITLE) == title }

    /** NotificationManager registers notifications asynchronously; wait briefly for the expected state. */
    private fun posted(title: String, expected: Boolean = true, timeoutMs: Long = 3_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (isActive(title) == expected) return expected
            Thread.sleep(50)
        }
        return isActive(title)
    }

    @Test
    fun immediateNotificationsArePosted() {
        notifier.schedule(NotificationSpec(identifier = "now", title = "Hello", body = "World"))

        assertTrue(posted("Hello"))
    }

    @Test
    fun futureNotificationsWaitForTheirTriggerTime() {
        notifier.schedule(NotificationSpec(identifier = "later", title = "Later", triggerAt = 61_000L))

        val info = workManager.getWorkInfosForUniqueWork(AndroidNotifier.workName("later")).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, info.state)
        assertEquals(60_000L, info.initialDelayMillis)
        assertFalse(isActive("Later"))

        WorkManagerTestInitHelper.getTestDriver(context)!!.setInitialDelayMet(info.id)
        assertTrue(posted("Later"))
    }

    @Test
    fun removeCancelsPendingAndDeliveredNotifications() {
        notifier.schedule(NotificationSpec(identifier = "shown", title = "Shown"))
        notifier.schedule(NotificationSpec(identifier = "pending", title = "Pending", triggerAt = 61_000L))

        notifier.remove(listOf("shown", "pending"))

        assertFalse(posted("Shown", expected = false))
        val pending = workManager.getWorkInfosForUniqueWork(AndroidNotifier.workName("pending")).get().single()
        assertEquals(WorkInfo.State.CANCELLED, pending.state)
    }

    @Test
    fun missingPermissionFails() {
        val denied = AndroidNotifier(context, { workManager }, hasPermission = { false })

        assertFailsWith<SecurityException> { denied.schedule(NotificationSpec(identifier = "x", title = "x")) }
    }

    // --- tapping ---

    @Test
    fun tappingRunsTheScriptThatScheduledIt() {
        val spec = NotificationSpec(
            identifier = "n1", title = "비", subtitle = "서울", body = "3시", threadIdentifier = "w",
            scriptName = "Weather", userInfo = """{"city":"Seoul"}""",
        )

        val intent = AndroidNotifier.tapIntent(context, spec, deliveredAt = 5_000L)

        assertEquals(NotificationTap.ACTION, intent.action)
        assertEquals(context.packageName, intent.`package`)
        val (script, payload) = NotificationTap.parse(intent)!!
        assertEquals("Weather", script)
        assertEquals(
            kr.b1ink.scriptwidget.domain.model.NotificationPayload("n1", "비", "서울", "3시", "w", """{"city":"Seoul"}""", 5_000L),
            payload,
        )
    }

    @Test
    fun openUrlWinsOverRunningTheScript() {
        val intent = AndroidNotifier.tapIntent(context, NotificationSpec(identifier = "n2", openURL = "https://example.com", scriptName = "W"), 0L)

        assertEquals(android.content.Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com", intent.dataString)
        assertEquals(null, NotificationTap.parse(intent))
    }

    @Test
    fun actionsBecomeNotificationButtons() {
        notifier.schedule(
            NotificationSpec(identifier = "btn", title = "buttons", actions = listOf(NotificationAction("지도", "https://maps.example")))
        )
        assertTrue(posted("buttons"))

        val posted = manager.activeNotifications.single { it.notification.extras.getString(Notification.EXTRA_TITLE) == "buttons" }
        assertEquals(listOf("지도"), posted.notification.actions.map { it.title.toString() })
    }

    // --- listing ---

    @Test
    fun futureNotificationsAreListedAsPendingUntilRemoved() {
        notifier.schedule(NotificationSpec(identifier = "later", title = "나중에", body = "b", triggerAt = 60_000L, userInfo = """{"k":1}"""))

        val pending = notifier.pending().single { it.identifier == "later" }
        assertEquals("나중에", pending.title)
        assertEquals(60_000L, pending.triggerAt)
        assertEquals("""{"k":1}""", pending.userInfo)

        notifier.remove(listOf("later"))
        assertTrue(notifier.pending().none { it.identifier == "later" })
    }

    @Test
    fun shownNotificationsAreListedAsDelivered() {
        notifier.schedule(NotificationSpec(identifier = "now", title = "delivered-title", body = "x"))
        assertTrue(posted("delivered-title"))

        assertEquals("delivered-title", notifier.delivered().single { it.identifier == "now" }.title)
        assertTrue(notifier.pending().none { it.identifier == "now" })
    }
}
