package kr.b1ink.scriptwidget.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BackgroundRefreshSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("bg-refresh-test", Context.MODE_PRIVATE)

    @After
    fun tearDown() {
        context.deleteSharedPreferences("bg-refresh-test")
    }

    @Test
    fun widgetsWaitForTheScreenByDefaultAndTheChoiceIsKept() {
        val settings = BackgroundRefreshSettings(prefs)
        assertFalse(settings.refreshWhileScreenOff())

        settings.setRefreshWhileScreenOff(true)

        assertTrue(BackgroundRefreshSettings(prefs).refreshWhileScreenOff())
    }
}
