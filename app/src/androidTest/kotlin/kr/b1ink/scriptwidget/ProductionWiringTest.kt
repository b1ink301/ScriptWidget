package kr.b1ink.scriptwidget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull

/**
 * Exercises the real Application wiring (no test overrides): WorkManager's on-demand
 * initialization asks the Application for its worker factory, which must not depend on
 * anything that needs WorkManager while it is being built.
 */
@RunWith(AndroidJUnit4::class)
class ProductionWiringTest {
    private val app = ApplicationProvider.getApplicationContext<Context>() as ScriptWidgetApp

    @Test
    fun workManagerConfigurationCanBeCreated() {
        assertNotNull(app.workManagerConfiguration.workerFactory)
    }

    @Test
    fun workManagerInitializesWithTheAppConfiguration() {
        assertNotNull(WorkManager.getInstance(app))
    }

    @Test
    fun widgetDependenciesResolve() {
        val deps = app.widgetDependencies
        assertNotNull(deps.scheduler)
        assertNotNull(deps.unbindWidgets)
        assertNotNull(deps.scheduleBoundWidgets)
        assertNotNull(app.container.refreshWidget)
        assertNotNull(app.container.runScript)
    }
}
