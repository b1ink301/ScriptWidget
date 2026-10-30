package kr.b1ink.scriptwidget.widget.state

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.repository.RenderStateRepository

/** Stores [RenderState] in Glance state; publishing it draws the widget once. */
class GlanceRenderStateRepository(
    context: Context,
    widget: GlanceAppWidget,
) : RenderStateRepository {
    private val context = context.applicationContext
    private val drawer = GlanceWidgetDrawer(context, widget)

    override suspend fun get(appWidgetId: Int): RenderState? {
        val glanceId = glanceIdOf(appWidgetId) ?: return null
        return RenderStateCodec.decode(
            getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[RenderStateCodec.KEY]
        )
    }

    override suspend fun publish(appWidgetId: Int, state: RenderState) {
        // The widget may have been removed while its script was running.
        val glanceId = glanceIdOf(appWidgetId) ?: return
        updateAppWidgetState(context, glanceId) { it[RenderStateCodec.KEY] = RenderStateCodec.encode(state) }
        drawer.draw(appWidgetId)
    }

    private fun glanceIdOf(appWidgetId: Int): GlanceId? =
        runCatching { GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId) }.getOrNull()
}
