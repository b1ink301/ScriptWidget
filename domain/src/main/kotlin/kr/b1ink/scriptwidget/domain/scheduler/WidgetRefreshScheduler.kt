package kr.b1ink.scriptwidget.domain.scheduler

interface WidgetRefreshScheduler {
    /** Runs the widget's script once, as soon as possible. */
    fun refreshNow(appWidgetId: Int)

    /** Runs the widget's script soon because a device event asked for it (see RefreshTrigger.Event). */
    fun refreshForEvent(appWidgetId: Int)

    /** Keeps the widget refreshing periodically (keeps an existing schedule). */
    fun schedulePeriodic(appWidgetId: Int)

    /**
     * Runs the widget's script once at [epochMillis] (replaces an earlier request). Without [wakeUp]
     * a sleeping device isn't woken for it: it runs when the device is next awake.
     */
    fun refreshAt(appWidgetId: Int, epochMillis: Long, wakeUp: Boolean = true)

    /** Runs the script for a tapped widget element; taps are queued, none are dropped. */
    fun runAction(appWidgetId: Int, action: String)

    fun cancel(appWidgetId: Int)
}
