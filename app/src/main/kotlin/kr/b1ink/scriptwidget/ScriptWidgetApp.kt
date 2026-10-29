package kr.b1ink.scriptwidget

import android.app.Application
import androidx.work.Configuration
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.widget.WidgetDependencies

class ScriptWidgetApp : Application(), WidgetDependencies.Provider, Configuration.Provider {

    val container: AppContainer by lazy { AppContainer(this) }

    override val widgetDependencies: WidgetDependencies get() = container

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()
}

val android.content.Context.container: AppContainer
    get() = (applicationContext as ScriptWidgetApp).container
