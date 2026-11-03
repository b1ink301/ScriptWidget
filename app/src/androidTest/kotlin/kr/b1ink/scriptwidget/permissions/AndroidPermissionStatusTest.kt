package kr.b1ink.scriptwidget.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(AndroidJUnit4::class)
class AndroidPermissionStatusTest {
    /** POST_NOTIFICATIONS exists from Android 13; before that notifications need no permission. */
    @get:Rule
    val grant: TestRule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        RuleChain.emptyRuleChain()
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun reportsGrantedRuntimePermissions() {
        val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) PermissionState.Granted else PermissionState.NotApplicable
        assertEquals(expected, AndroidPermissionStatus(context).state(AppPermission.Notifications))
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
        // USE_EXACT_ALARM is granted at install time (allowed because the app isn't on Google Play);
        // before Android 12 exact alarms need no permission.
        val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PermissionState.Granted else PermissionState.NotApplicable
        assertEquals(expected, AndroidPermissionStatus(context).state(AppPermission.ExactAlarm))
    }

    @Test
    fun everyPermissionHasAState() {
        val status = AndroidPermissionStatus(context)
        AppPermission.entries.forEach { assertNotNull(status.state(it)) }
    }
}
