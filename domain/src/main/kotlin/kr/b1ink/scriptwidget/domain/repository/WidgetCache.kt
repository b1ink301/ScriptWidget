package kr.b1ink.scriptwidget.domain.repository

/** Files kept for one widget (e.g. its downloaded images), removed with the widget. */
fun interface WidgetCache {
    suspend fun clear(appWidgetId: Int)

    companion object {
        val None = WidgetCache { }
    }
}
