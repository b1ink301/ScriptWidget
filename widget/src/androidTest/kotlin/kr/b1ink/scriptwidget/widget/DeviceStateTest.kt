package kr.b1ink.scriptwidget.widget

import android.content.Context
import android.net.ConnectivityManager
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.widget.work.ConnectivityNetworkState
import kr.b1ink.scriptwidget.widget.work.PowerManagerScreenState
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Screen and network state come from the platform services (no permissions beyond the manifest). */
@RunWith(AndroidJUnit4::class)
class DeviceStateTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun screenStateFollowsPowerManager() {
        assertEquals(context.getSystemService(PowerManager::class.java).isInteractive, PowerManagerScreenState(context).isInteractive())
    }

    @Test
    fun networkStateFollowsTheActiveNetwork() {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val expected = connectivity.activeNetwork != null
        assertEquals(expected, ConnectivityNetworkState(context).isConnected())
    }
}
