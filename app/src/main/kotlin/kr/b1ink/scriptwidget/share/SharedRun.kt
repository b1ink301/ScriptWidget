package kr.b1ink.scriptwidget.share

import android.content.Context
import android.content.Intent
import kr.b1ink.scriptwidget.MainActivity
import kr.b1ink.scriptwidget.domain.model.ShareInput

/** Hands shared content, already copied into the sandbox, to [MainActivity] to run a script with it. */
object SharedRun {
    private const val ACTION = "kr.b1ink.scriptwidget.action.RUN_SHARED"
    private const val EXTRA_SCRIPT = "scriptName"
    private const val EXTRA_TEXTS = "plainTexts"
    private const val EXTRA_URLS = "urls"
    private const val EXTRA_FILES = "fileURLs"
    private const val EXTRA_IMAGES = "images"

    fun intent(context: Context, scriptName: String, input: ShareInput): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_SCRIPT, scriptName)
            .putStringArrayListExtra(EXTRA_TEXTS, ArrayList(input.plainTexts))
            .putStringArrayListExtra(EXTRA_URLS, ArrayList(input.urls))
            .putStringArrayListExtra(EXTRA_FILES, ArrayList(input.fileURLs))
            .putStringArrayListExtra(EXTRA_IMAGES, ArrayList(input.images))

    fun parse(intent: Intent?): Pair<String, ShareInput>? {
        if (intent?.action != ACTION) return null
        val script = intent.getStringExtra(EXTRA_SCRIPT) ?: return null
        return script to ShareInput(
            plainTexts = intent.getStringArrayListExtra(EXTRA_TEXTS).orEmpty(),
            urls = intent.getStringArrayListExtra(EXTRA_URLS).orEmpty(),
            fileURLs = intent.getStringArrayListExtra(EXTRA_FILES).orEmpty(),
            images = intent.getStringArrayListExtra(EXTRA_IMAGES).orEmpty(),
        )
    }
}
