package kr.b1ink.scriptwidget.widget

import android.content.Context
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler
import kr.b1ink.scriptwidget.domain.usecase.ResizeWidgetUseCase
import kr.b1ink.scriptwidget.domain.usecase.ScheduleBoundWidgetsUseCase
import kr.b1ink.scriptwidget.domain.usecase.UnbindWidgetsUseCase

/**
 * What the framework-instantiated widget classes (receiver, actions) need. The Application
 * implements [Provider]; this module never sees the concrete data layer.
 */
interface WidgetDependencies {
    val scheduler: WidgetRefreshScheduler
    val unbindWidgets: UnbindWidgetsUseCase
    val scheduleBoundWidgets: ScheduleBoundWidgetsUseCase
    val resizeWidget: ResizeWidgetUseCase
    val handleEvent: kr.b1ink.scriptwidget.domain.usecase.HandleEventUseCase

    interface Provider {
        val widgetDependencies: WidgetDependencies
    }

    companion object {
        fun from(context: Context): WidgetDependencies =
            (context.applicationContext as Provider).widgetDependencies
    }
}
