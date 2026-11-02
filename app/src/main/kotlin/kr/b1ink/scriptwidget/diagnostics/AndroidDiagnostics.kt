package kr.b1ink.scriptwidget.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.flow.first
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.model.CrashReport
import kr.b1ink.scriptwidget.domain.model.DiagnosticsInfo
import kr.b1ink.scriptwidget.permissions.AndroidPermissionStatus
import kr.b1ink.scriptwidget.permissions.AppPermission
import kr.b1ink.scriptwidget.widget.ScriptWidgetReceiver

/** Collects [DiagnosticsInfo] for a bug report from the device and the app's own records. */
class AndroidDiagnostics(context: Context, private val container: AppContainer) {
    private val context = context.applicationContext

    suspend fun collect(appVersion: String): DiagnosticsInfo {
        val permissions = AndroidPermissionStatus(context)
        return DiagnosticsInfo(
            appVersion = appVersion,
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            launcher = launcher(),
            settings = mapOf(
                "Refresh while screen off" to if (container.refreshSettings.refreshWhileScreenOff()) "on" else "off",
                "Locale" to context.resources.configuration.locales[0].toLanguageTag(),
            ),
            permissions = AppPermission.entries.associate { it.name to permissions.state(it).name },
            widgets = AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ScriptWidgetReceiver::class.java)).size,
            scripts = container.observeScriptNames().value.size,
            crashes = container.crashLog.all() + exits(),
            recentRuns = container.runHistory.history.first(),
        )
    }

    private fun launcher(): String? = runCatching {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    }.getOrNull()

    /**
     * What Android recorded about earlier deaths of the process that the app couldn't log itself:
     * native crashes (e.g. in the script engine), ANRs, being killed for memory. Uncaught Java
     * exceptions are already in the crash log.
     */
    private fun exits(): List<CrashReport> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(ActivityManager::class.java) ?: return emptyList()
        return runCatching { manager.getHistoricalProcessExitReasons(context.packageName, 0, MAX_EXITS) }.getOrDefault(emptyList())
            .mapNotNull { exit ->
                val kind = when (exit.reason) {
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
                    ApplicationExitInfo.REASON_ANR -> "ANR"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
                    ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for resource use"
                    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "initialization failure"
                    else -> return@mapNotNull null
                }
                CrashReport(
                    time = exit.timestamp,
                    kind = kind,
                    thread = exit.processName,
                    message = exit.description ?: kind,
                    stackTrace = "pss ${exit.pss} kB, rss ${exit.rss} kB, importance ${exit.importance}",
                )
            }
    }

    companion object {
        private const val MAX_EXITS = 20

        /** e.g. `scriptwidget-diagnostics-20261102-1530.txt` */
        fun fileName(now: Long): String =
            "scriptwidget-diagnostics-" + java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.ROOT).format(java.util.Date(now)) + ".txt"
    }
}
