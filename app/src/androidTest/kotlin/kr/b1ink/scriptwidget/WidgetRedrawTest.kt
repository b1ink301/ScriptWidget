package kr.b1ink.scriptwidget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.widget.ScriptWidget
import kr.b1ink.scriptwidget.widget.ScriptWidgetReceiver
import kr.b1ink.scriptwidget.widget.state.GlanceRenderStateRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** Publishing a widget draws it once instead of keeping a Glance session (a WorkManager job) alive. */
@RunWith(AndroidJUnit4::class)
class WidgetRedrawTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = AppWidgetManager.getInstance(context)
    private val host = AppWidgetHost(context, HOST_ID)
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var hostView: AppWidgetHostView

    @Before
    fun bindWidget() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("appwidget grantbind --package ${context.packageName} --user 0")
            .close()
        Thread.sleep(300)
        appWidgetId = host.allocateAppWidgetId()
        val bound = manager.bindAppWidgetIdIfAllowed(appWidgetId, ComponentName(context, ScriptWidgetReceiver::class.java))
        assertTrue(bound, "test widget could not be bound")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            host.startListening()
            hostView = host.createView(context, appWidgetId, manager.getAppWidgetInfo(appWidgetId))
        }
        // Binding alone makes the system send onUpdate; let that settle before measuring.
        Thread.sleep(1_000)
    }

    @After
    fun unbind() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            host.deleteAppWidgetId(appWidgetId)
            host.stopListening()
        }
    }

    @Test
    fun publishingDrawsTheWidgetWithoutStartingAGlanceSession() = runBlocking {
        val repository = GlanceRenderStateRepository(context, ScriptWidget())

        repository.publish(appWidgetId, RenderState(scriptName = "s", tree = WidgetRoot(children = listOf(TextNode("hi")))))

        waitUntil("the host received the drawn widget") { hostView.childCount > 0 }
        assertTrue(activeSessions().isEmpty(), "Glance session still running: ${activeSessions()}")
    }

    @Test
    fun systemUpdatesDrawWithoutStartingAGlanceSession() {
        context.sendBroadcast(
            android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .setComponent(ComponentName(context, ScriptWidgetReceiver::class.java))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId)),
        )

        waitUntil("the host received the drawn widget") { hostView.childCount > 0 }
        Thread.sleep(1_000)
        assertTrue(activeSessions().isEmpty(), "Glance session still running: ${activeSessions()}")
    }

    private fun activeSessions(): List<WorkInfo> =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork("appWidget-$appWidgetId").get()
            .filter { !it.state.isFinished }

    private fun waitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (System.currentTimeMillis() < deadline) {
            var met = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync { met = condition() }
            if (met) return
            Thread.sleep(100)
        }
        throw AssertionError("timed out waiting until $what")
    }

    private companion object {
        const val HOST_ID = 0x5C11
    }
}
