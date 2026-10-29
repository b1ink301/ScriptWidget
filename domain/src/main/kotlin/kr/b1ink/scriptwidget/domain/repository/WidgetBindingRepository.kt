package kr.b1ink.scriptwidget.domain.repository

import kr.b1ink.scriptwidget.domain.model.WidgetBinding

interface WidgetBindingRepository {
    suspend fun get(appWidgetId: Int): WidgetBinding?
    suspend fun set(binding: WidgetBinding)
    suspend fun remove(appWidgetId: Int)

    /** Sorted ids of widgets bound to [scriptName]. */
    suspend fun widgetsUsing(scriptName: String): List<Int>
}
