package kr.b1ink.scriptwidget.widget.render

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.ui.graphics.Color
import kr.b1ink.scriptwidget.domain.model.DateNode
import java.util.Date
import android.graphics.Color as AndroidColor

/** Rendering rules shared by the Glance widget and the in-app Compose preview. */
object Styles {
    val DEFAULT_BACKGROUND = Color(0xFF1C1C1E)
    val DEFAULT_TEXT = Color.White
    val DEFAULT_PADDING = listOf(16f, 16f, 16f, 16f)
    const val MAX_IMAGE_PX = 512

    /** [night] picks the dark half of a `Color.dynamic` pair. */
    fun color(value: String?, fallback: Color, night: Boolean = false): Color =
        value?.let { runCatching { Color(AndroidColor.parseColor(ThemedColor.pick(it, night))) }.getOrNull() } ?: fallback

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    fun dateText(context: Context, node: DateNode, now: Long = System.currentTimeMillis()): String = when (node.style) {
        "date" -> DateFormat.getMediumDateFormat(context).format(Date(node.time))
        "relative" -> DateUtils.getRelativeTimeSpanString(node.time, now, DateUtils.MINUTE_IN_MILLIS).toString()
        "timer" -> DateUtils.formatElapsedTime(((now - node.time) / 1000).coerceAtLeast(0))
        else -> DateFormat.getTimeFormat(context).format(Date(node.time))
    }
}
