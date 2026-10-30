package kr.b1ink.scriptwidget.widget.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.widget.RefreshAction
import androidx.glance.LocalContext
import kr.b1ink.scriptwidget.domain.model.WidgetError
import kr.b1ink.scriptwidget.widget.R

/** Shown on a widget whose last run failed but which still shows its last good content. */
const val STALE_BADGE = "⚠"

/** Everything a widget instance shows for a given [RenderState]. */
@Composable
fun ScriptWidgetContent(state: RenderState?) {
    val context = LocalContext.current
    val tree = state?.tree
    // App errors are codes; script errors are the script's own message.
    val error = state?.error?.let { message ->
        when (val appError = WidgetError.decode(message)) {
            WidgetError.NotBound -> context.getString(R.string.widget_error_not_bound)
            is WidgetError.ScriptNotFound -> context.getString(R.string.widget_error_script_not_found, appError.name)
            null -> message
        }
    }
    when {
        tree != null -> Box(modifier = GlanceModifier.fillMaxSize()) {
            GlanceRenderer.Root(tree)
            if (error != null) {
                Box(modifier = GlanceModifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                    Text(
                        text = STALE_BADGE,
                        style = TextStyle(color = colorProvider(Color(0xFFFFCC00)), fontSize = 12.sp),
                        modifier = GlanceModifier.clickable(actionRunCallback<RefreshAction>()),
                    )
                }
            }
        }

        error != null -> Message(
            title = "⚠ ${state.scriptName ?: "Script"}",
            body = error,
            background = Color(0xFF3A1212),
            bodyColor = Color(0xFFFCA5A5),
        )

        else -> Message(
            title = "ScriptWidget",
            body = context.getString(R.string.widget_waiting),
            background = Styles.DEFAULT_BACKGROUND,
            bodyColor = Color(0xFF8E8E93),
        )
    }
}

@Composable
private fun Message(title: String, body: String, background: Color, bodyColor: Color) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(background)
            .padding(12.dp)
            .clickable(actionRunCallback<RefreshAction>()),
    ) {
        Text(
            text = title,
            style = TextStyle(color = colorProvider(Color.White), fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Text(text = body, style = TextStyle(color = colorProvider(bodyColor), fontSize = 11.sp), maxLines = 8)
    }
}
