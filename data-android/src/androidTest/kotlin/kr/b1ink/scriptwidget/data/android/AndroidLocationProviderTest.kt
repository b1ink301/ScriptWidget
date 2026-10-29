package kr.b1ink.scriptwidget.data.android

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.LocationAccuracy
import kr.b1ink.scriptwidget.data.engine.LocationUnavailableException
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Uses a GPS test provider: the test package is made the mock-location app via appops.
 * Instrumentation has no visible activity, so it counts as a *background* app for location,
 * the same situation as a widget refresh — hence the background permission.
 */
@RunWith(AndroidJUnit4::class)
class AndroidLocationProviderTest {
    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(LocationManager::class.java)
    private val provider = LocationManager.GPS_PROVIDER

    /** Runs a shell command and waits for it to finish (reads its output to EOF). */
    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    @Before
    fun setUp() {
        shell("appops set ${context.packageName} android:mock_location allow")
        manager.addTestProvider(
            provider, false, false, false, false, true, true, true,
            ProviderProperties.POWER_USAGE_LOW, ProviderProperties.ACCURACY_FINE,
        )
        manager.setTestProviderEnabled(provider, true)
    }

    @After
    fun tearDown() {
        runCatching { manager.removeTestProvider(provider) }
        shell("appops set ${context.packageName} android:mock_location deny")
    }

    private fun pushFix(latitude: Double, longitude: Double) {
        manager.setTestProviderLocation(provider, Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            accuracy = 5f
            altitude = 38.0
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        })
    }

    @Test
    fun missingPermissionIsReportedAsSuch() = runTest {
        val locations = AndroidLocationProvider(context, hasPermission = { false })

        val error = assertFailsWith<LocationUnavailableException> { locations.current(LocationAccuracy.Best) }
        assertTrue(error.permissionDenied)
    }

    @Test
    fun returnsTheDeviceLocation() = runTest {
        pushFix(37.5665, 126.978)
        // A fix that arrives while the request is pending; joined before tearDown revokes mock access.
        val pusher = Thread { Thread.sleep(500); runCatching { pushFix(37.5665, 126.978) } }.apply { start() }

        val location = try {
            AndroidLocationProvider(context, fixTimeoutMillis = 5_000).current(LocationAccuracy.Best)
        } finally {
            pusher.join()
        }

        assertEquals(37.5665, location.latitude, 0.0001)
        assertEquals(126.978, location.longitude, 0.0001)
        assertEquals(5.0, location.horizontalAccuracy!!, 0.01)
        assertTrue(location.timestamp > 0)
    }
}
