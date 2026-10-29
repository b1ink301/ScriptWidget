package kr.b1ink.scriptwidget.domain.repository

import kr.b1ink.scriptwidget.domain.model.RenderState

/** Per-widget display state. [publish] also redraws the widget. */
interface RenderStateRepository {
    suspend fun get(appWidgetId: Int): RenderState?
    suspend fun publish(appWidgetId: Int, state: RenderState)
}
