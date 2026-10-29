package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidDeviceInfoTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun snapshotReflectsTheDevice() {
        val info = AndroidDeviceInfo(context).snapshot()

        assertEquals(Build.MODEL, info.model)
        assertEquals(Build.VERSION.RELEASE, info.systemVersion)
        assertTrue(info.name.isNotBlank())
        assertTrue(info.screenWidth > 0 && info.screenHeight > 0)
        assertTrue(info.screenScale >= 1f)
        assertTrue(info.batteryLevel in 0.0..1.0, "battery ${info.batteryLevel}")
        assertTrue(info.volume in 0.0..1.0)
        assertTrue(info.screenBrightness in 0.0..1.0)
        assertTrue(info.preferredLanguages.isNotEmpty())
        assertTrue(info.orientation in setOf("portrait", "landscapeLeft", "landscapeRight", "unknown"))
    }
}
