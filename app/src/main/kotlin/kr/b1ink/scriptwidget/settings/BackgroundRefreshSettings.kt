package kr.b1ink.scriptwidget.settings

import android.content.SharedPreferences
import kr.b1ink.scriptwidget.domain.scheduler.RefreshSettings

/** "Refresh while the screen is off" (off by default: widgets catch up when the screen turns on). */
class BackgroundRefreshSettings(private val prefs: SharedPreferences) : RefreshSettings {
    override fun refreshWhileScreenOff(): Boolean = prefs.getBoolean(KEY, false)

    fun setRefreshWhileScreenOff(enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
    }

    private companion object {
        const val KEY = "refresh_while_screen_off"
    }
}
