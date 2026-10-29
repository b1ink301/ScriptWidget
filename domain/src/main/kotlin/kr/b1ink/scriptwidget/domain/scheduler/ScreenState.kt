package kr.b1ink.scriptwidget.domain.scheduler

/** Whether the user is looking at the device (screen on), which decides how often widgets refresh. */
fun interface ScreenState {
    fun isInteractive(): Boolean

    companion object {
        val AlwaysOn = ScreenState { true }
    }
}

/** How often background refreshes may run, whatever `refreshAfterDate` asks for. */
object RefreshPolicy {
    const val MIN_INTERVAL_SCREEN_ON_MS = 5 * 60_000L
    const val MIN_INTERVAL_SCREEN_OFF_MS = 15 * 60_000L

    /** Alarms may fire a little early; a run this close to the minimum still counts as due. */
    const val EARLY_TOLERANCE_MS = 30_000L

    fun minInterval(screenOn: Boolean): Long = if (screenOn) MIN_INTERVAL_SCREEN_ON_MS else MIN_INTERVAL_SCREEN_OFF_MS
}

/** Whether the device is online; background runs of network scripts wait for it. */
fun interface NetworkState {
    fun isConnected(): Boolean

    companion object {
        val AlwaysConnected = NetworkState { true }
    }
}
