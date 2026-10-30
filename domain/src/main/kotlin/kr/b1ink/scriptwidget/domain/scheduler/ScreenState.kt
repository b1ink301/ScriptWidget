package kr.b1ink.scriptwidget.domain.scheduler

/** Whether the user is looking at the device (screen on), which decides how often widgets refresh. */
fun interface ScreenState {
    fun isInteractive(): Boolean

    companion object {
        val AlwaysOn = ScreenState { true }
    }
}

/**
 * Background refresh rules. With the screen off nothing runs (unless [RefreshSettings] allows it):
 * refreshes wait in alarms that don't wake the device and run as soon as the screen turns on.
 */
object RefreshPolicy {
    const val MIN_INTERVAL_SCREEN_ON_MS = 5 * 60_000L
    const val MIN_INTERVAL_SCREEN_OFF_MS = 15 * 60_000L

    /** Widgets whose script sets no `refreshAfterDate` still catch up this long after their last run. */
    const val DEFAULT_INTERVAL_MS = 15 * 60_000L

    /** A refresh put off because the screen is off is retried this late (it fires on the next screen-on). */
    const val SCREEN_OFF_RETRY_MS = 2 * 60_000L

    /** Alarms may fire a little early; a run this close to the minimum still counts as due. */
    const val EARLY_TOLERANCE_MS = 30_000L

    fun minInterval(screenOn: Boolean): Long = if (screenOn) MIN_INTERVAL_SCREEN_ON_MS else MIN_INTERVAL_SCREEN_OFF_MS
}

/** User choice: keep refreshing (every 15 minutes, waking the device) while the screen is off. */
fun interface RefreshSettings {
    fun refreshWhileScreenOff(): Boolean

    companion object {
        val Default = RefreshSettings { false }
    }
}

/** Whether the device is online; background runs of network scripts wait for it. */
fun interface NetworkState {
    fun isConnected(): Boolean

    companion object {
        val AlwaysConnected = NetworkState { true }
    }
}
