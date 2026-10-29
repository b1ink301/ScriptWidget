package kr.b1ink.scriptwidget.widget.work

import android.appwidget.AppWidgetManager
import kr.b1ink.scriptwidget.domain.model.WidgetFamily

fun interface WidgetFamilyResolver {
    fun familyOf(appWidgetId: Int): WidgetFamily
}

/** Uses the widget's portrait size (MIN_WIDTH × MAX_HEIGHT) reported by the launcher. */
class AppWidgetManagerFamilyResolver(private val manager: AppWidgetManager) : WidgetFamilyResolver {
    override fun familyOf(appWidgetId: Int): WidgetFamily {
        val options = manager.getAppWidgetOptions(appWidgetId)
        return WidgetFamily.fromSize(
            widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
            heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT),
        )
    }
}
