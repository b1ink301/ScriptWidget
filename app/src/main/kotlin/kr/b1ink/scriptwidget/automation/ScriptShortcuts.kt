package kr.b1ink.scriptwidget.automation

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kr.b1ink.scriptwidget.MainActivity
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.ui.editor.RunRequest

/** Home screen shortcuts that run a script (`config.runsFromHomeScreen`). */
object ScriptShortcuts {
    private const val ACTION = "kr.b1ink.scriptwidget.action.RUN_SHORTCUT"
    private const val EXTRA_SCRIPT = "scriptName"

    fun intent(context: Context, scriptName: String): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_SCRIPT, scriptName)

    fun parse(intent: Intent?): Pair<String, RunRequest>? {
        if (intent?.action != ACTION) return null
        val script = intent.getStringExtra(EXTRA_SCRIPT) ?: return null
        return script to RunRequest(source = RunSource.HomeScreen)
    }

    /** Asks the launcher to pin a shortcut; false when the launcher doesn't support it. */
    fun requestPin(context: Context, scriptName: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val shortcut = ShortcutInfoCompat.Builder(context, "script-$scriptName")
            .setShortLabel(scriptName.take(24))
            .setLongLabel(scriptName)
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_launcher))
            .setIntent(intent(context, scriptName))
            .build()
        return ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
