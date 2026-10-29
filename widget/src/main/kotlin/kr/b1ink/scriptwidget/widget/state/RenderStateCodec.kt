package kr.b1ink.scriptwidget.widget.state

import androidx.datastore.preferences.core.stringPreferencesKey
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.WidgetJson

/** Serializes [RenderState] into the Glance state of each widget instance. */
object RenderStateCodec {
    val KEY = stringPreferencesKey("render_state")

    fun encode(state: RenderState): String = WidgetJson.encodeToString(RenderState.serializer(), state)

    fun decode(json: String?): RenderState? =
        json?.let { runCatching { WidgetJson.decodeFromString(RenderState.serializer(), it) }.getOrNull() }
}
