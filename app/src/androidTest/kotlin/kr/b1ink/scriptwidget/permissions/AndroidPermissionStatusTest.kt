package kr.b1ink.scriptwidget.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class AndroidPermissionStatusTest {
    @get:Rule
    val grant: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reportsGrantedRuntimePermissions() {
        assertEquals(PermissionState.Granted, AndroidPermissionStatus(context).state(AppPermission.Notifications))
    }

    @Test
    fun olderVersionsDontNeedNewerPermissions() {
        val legacy = AndroidPermissionStatus(context, sdkInt = Build.VERSION_CODES.P)
        assertEquals(PermissionState.NotApplicable, legacy.state(AppPermission.Notifications))
        assertEquals(PermissionState.NotApplicable, legacy.state(AppPermission.BackgroundLocation))
        assertEquals(PermissionState.NotApplicable, legacy.state(AppPermission.ExactAlarm))
    }

    @Test
    fun exactAlarmsAreAvailableThanksToUseExactAlarm() {
        // USE_EXACT_ALARM is granted at install time (allowed because the app isn't on Google Play).
        assertEquals(PermissionState.Granted, AndroidPermissionStatus(context).state(AppPermission.ExactAlarm))
    }

    @Test
    fun everyPermissionHasAState() {
        val status = AndroidPermissionStatus(context)
        AppPermission.entries.forEach { assertNotNull(status.state(it)) }
    }
}
