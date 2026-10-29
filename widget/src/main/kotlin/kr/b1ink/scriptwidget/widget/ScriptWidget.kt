package kr.b1ink.scriptwidget.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import kr.b1ink.scriptwidget.widget.render.ScriptWidgetContent
import kr.b1ink.scriptwidget.widget.state.RenderStateCodec

class ScriptWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            ScriptWidgetContent(RenderStateCodec.decode(currentState<Preferences>()[RenderStateCodec.KEY]))
        }
    }
}
