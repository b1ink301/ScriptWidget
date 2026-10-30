package kr.b1ink.scriptwidget.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeRenderStateRepository
import kr.b1ink.scriptwidget.domain.fake.FakeScriptEngine
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.fake.FixedClock
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetBinding
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.domain.usecase.RefreshWidgetUseCase
import kr.b1ink.scriptwidget.domain.usecase.RunScriptUseCase
import kr.b1ink.scriptwidget.widget.work.ScriptUpdateWorker
import kr.b1ink.scriptwidget.widget.work.ScriptWorkerFactory
import kr.b1ink.scriptwidget.widget.work.WidgetFamilyResolver
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ScriptUpdateWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val engine = FakeScriptEngine(ScriptRunResult(widget = WidgetRoot(children = listOf(TextNode("ok")))))
    private val renderStates = FakeRenderStateRepository()
    private val clock = FixedClock(1_000L)
    private val refresh = RefreshWidgetUseCase(
        bindings = FakeWidgetBindingRepository(WidgetBinding(11, "s", "p")),
        scripts = FakeScriptRepository(mapOf("s" to "code")),
        runScript = RunScriptUseCase(engine),
        renderStates = renderStates,
        scheduler = FakeRefreshScheduler(),
        clock = clock,
    )
    private val factory = ScriptWorkerFactory({ refresh }, { WidgetFamilyResolver { WidgetFamily.Medium } }, { error("no events in this test") })

    private fun worker(inputId: Int?, trigger: String? = null, action: String? = null) = TestListenableWorkerBuilder<ScriptUpdateWorker>(context)
        .setWorkerFactory(factory)
        .apply {
            if (inputId != null) {
                setInputData(
                    workDataOf(
                        ScriptUpdateWorker.KEY_APP_WIDGET_ID to inputId,
                        ScriptUpdateWorker.KEY_TRIGGER to trigger,
                        ScriptUpdateWorker.KEY_ACTION to action,
                    )
                )
            }
        }
        .build()

    @Test
    fun refreshesTheWidgetWithItsFamily() = runTest {
        val result = worker(11).doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(WidgetFamily.Medium, engine.calls.single().env.widgetFamily)
        assertEquals("ok", (renderStates.states[11]!!.tree!!.children.single() as TextNode).text)
    }

    @Test
    fun scriptErrorsStillSucceedBecauseTheErrorIsShownOnTheWidget() = runTest {
        engine.result = ScriptRunResult(error = "boom")

        assertEquals(ListenableWorker.Result.success(), worker(11).doWork())
        assertEquals("boom", renderStates.states[11]!!.error)
    }

    @Test
    fun missingWidgetIdFails() = runTest {
        assertEquals(ListenableWorker.Result.failure(), worker(null).doWork())
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun periodicRunsHonorRefreshAfterDate() = runTest {
        renderStates.states[11] = RenderState(scriptName = "s", tree = WidgetRoot(refreshAfter = 5_000L), updatedAt = 1)

        assertEquals(ListenableWorker.Result.success(), worker(11, trigger = "Periodic").doWork())
        assertTrue(engine.calls.isEmpty())

        // The alarm for refreshAfter fires once the minimum interval since the last run has passed.
        clock.millis = 1 + 5 * 60_000L
        worker(11, trigger = "Scheduled").doWork()
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun widgetActionsReachTheScript() = runTest {
        worker(11, trigger = "Manual", action = "increment").doWork()

        assertEquals("increment", engine.calls.single().env.widgetAction)
    }

    @Test
    fun unknownTriggerCountsAsManual() = runTest {
        renderStates.states[11] = RenderState(scriptName = "s", tree = WidgetRoot(refreshAfter = 5_000L), updatedAt = 1)

        worker(11, trigger = null).doWork()

        assertEquals(1, engine.calls.size)
    }

    @Test
    fun factoryIgnoresOtherWorkers() {
        var params: WorkerParameters? = null
        TestListenableWorkerBuilder<ScriptUpdateWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters) =
                    factory.createWorker(appContext, workerClassName, workerParameters).also { params = workerParameters }
            })
            .build()

        // Other worker classes must fall back to WorkManager's default factory.
        assertNull(factory.createWorker(context, "com.example.Other", params!!))
    }
}
