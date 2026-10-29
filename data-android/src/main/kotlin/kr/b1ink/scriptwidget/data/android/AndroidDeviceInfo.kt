package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import android.view.Surface
import android.view.WindowManager
import kr.b1ink.scriptwidget.data.engine.DeviceInfo
import java.util.Locale

/** Collects [DeviceInfo] from platform services. */
class AndroidDeviceInfo(context: Context) {
    private val context = context.applicationContext

    fun snapshot(): DeviceInfo {
        val resources = context.resources
        val metrics = resources.displayMetrics
        val config = resources.configuration
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val audio = context.getSystemService(AudioManager::class.java)
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

        return DeviceInfo(
            name = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL,
            systemVersion = Build.VERSION.RELEASE,
            model = Build.MODEL,
            isPhone = config.smallestScreenWidthDp < 600,
            screenWidth = metrics.widthPixels / metrics.density,
            screenHeight = metrics.heightPixels / metrics.density,
            screenScale = metrics.density,
            screenBrightness = runCatching {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255.0
            }.getOrDefault(0.5).coerceIn(0.0, 1.0),
            orientation = orientation(config),
            batteryLevel = if (level >= 0 && scale > 0) (level.toDouble() / scale).coerceIn(0.0, 1.0) else 0.0,
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            isFullyCharged = status == BatteryManager.BATTERY_STATUS_FULL,
            preferredLanguages = LocaleList.getDefault().let { list -> (0 until list.size()).map { list[it].toLanguageTag() } },
            locale = Locale.getDefault().toString(),
            isUsingDarkAppearance = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
            volume = (audio.getStreamVolume(AudioManager.STREAM_MUSIC).toDouble() / maxVolume).coerceIn(0.0, 1.0),
        )
    }

    @Suppress("DEPRECATION")
    private fun orientation(config: Configuration): String = when (config.orientation) {
        Configuration.ORIENTATION_PORTRAIT -> "portrait"
        Configuration.ORIENTATION_LANDSCAPE -> {
            val rotation = context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation
            if (rotation == Surface.ROTATION_270) "landscapeRight" else "landscapeLeft"
        }
        else -> "unknown"
    }
}
