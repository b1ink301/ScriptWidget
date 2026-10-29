package kr.b1ink.scriptwidget.permissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

class AndroidPermissionStatus(
    context: Context,
    private val sdkInt: Int = Build.VERSION.SDK_INT,
) : PermissionStatusProvider {
    private val context = context.applicationContext

    override fun state(permission: AppPermission): PermissionState = when (permission) {
        AppPermission.Notifications ->
            if (sdkInt < Build.VERSION_CODES.TIRAMISU) PermissionState.NotApplicable
            else granted(Manifest.permission.POST_NOTIFICATIONS)

        AppPermission.Location ->
            if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) PermissionState.Granted
            else PermissionState.Denied

        AppPermission.BackgroundLocation ->
            if (sdkInt < Build.VERSION_CODES.Q) PermissionState.NotApplicable
            else granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        AppPermission.BatteryOptimization ->
            if (context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)) PermissionState.Granted
            else PermissionState.Denied

        AppPermission.Calendar -> granted(Manifest.permission.READ_CALENDAR)
        AppPermission.Contacts -> granted(Manifest.permission.READ_CONTACTS)
        AppPermission.Photos ->
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) granted(Manifest.permission.READ_MEDIA_IMAGES)
            else granted(Manifest.permission.READ_EXTERNAL_STORAGE)

        AppPermission.ExactAlarm ->
            if (sdkInt < Build.VERSION_CODES.S) PermissionState.NotApplicable
            else if (context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()) PermissionState.Granted
            else PermissionState.Denied
    }

    private fun has(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun granted(permission: String) = if (has(permission)) PermissionState.Granted else PermissionState.Denied
}
