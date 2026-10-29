package kr.b1ink.scriptwidget.widget.work

import android.content.Context
import android.os.PowerManager
import kr.b1ink.scriptwidget.domain.scheduler.ScreenState

/** The screen is on (the device is interactive). */
class PowerManagerScreenState(context: Context) : ScreenState {
    private val power = context.applicationContext.getSystemService(PowerManager::class.java)

    override fun isInteractive(): Boolean = power?.isInteractive ?: true
}

/** Online = the active network can reach the internet. */
class ConnectivityNetworkState(context: Context) : kr.b1ink.scriptwidget.domain.scheduler.NetworkState {
    private val connectivity = context.applicationContext.getSystemService(android.net.ConnectivityManager::class.java)

    override fun isConnected(): Boolean {
        val manager = connectivity ?: return true
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
