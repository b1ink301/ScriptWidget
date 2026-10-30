package kr.b1ink.scriptwidget

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.work.Configuration
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.widget.WidgetDependencies

class ScriptWidgetApp : Application(), WidgetDependencies.Provider, Configuration.Provider {

    val container: AppContainer by lazy { AppContainer(this) }

    override val widgetDependencies: WidgetDependencies get() = container

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Alarms are cleared by reboots and app updates; the event triggers hook in again.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { container.syncEventMonitor() } }
    }
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as ScriptWidgetApp).container
