package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import androidx.core.content.FileProvider
import kr.b1ink.scriptwidget.data.engine.MailSpec
import kr.b1ink.scriptwidget.data.engine.MessageSpec
import kr.b1ink.scriptwidget.data.engine.ShareSpec
import java.io.File

/** Builds the intents for sharing, mail and SMS. Only files inside the app's storage are shared. */
class ComposerIntents(context: Context) {
    private val context = context.applicationContext
    private val authority = "${context.packageName}.scriptfiles"

    fun share(spec: ShareSpec): Intent {
        val uris = spec.files.mapNotNull(::uriFor)
        val text = spec.texts.joinToString("\n").takeIf { it.isNotEmpty() }
        return when {
            uris.size > 1 -> Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
            uris.size == 1 -> Intent(Intent.ACTION_SEND).apply {
                type = context.contentResolver.getType(uris[0]) ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uris[0])
            }
            else -> Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        }.apply {
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            if (uris.isNotEmpty()) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun mail(spec: MailSpec): Intent {
        val uris = spec.attachments.mapNotNull(::uriFor)
        return Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            // The selector limits the chooser to email apps while still allowing attachments.
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            putExtra(Intent.EXTRA_EMAIL, spec.to.toTypedArray())
            putExtra(Intent.EXTRA_CC, spec.cc.toTypedArray())
            putExtra(Intent.EXTRA_BCC, spec.bcc.toTypedArray())
            putExtra(Intent.EXTRA_SUBJECT, spec.subject)
            if (spec.html) {
                putExtra(Intent.EXTRA_TEXT, Html.fromHtml(spec.body, Html.FROM_HTML_MODE_COMPACT))
                putExtra(Intent.EXTRA_HTML_TEXT, spec.body)
            } else {
                putExtra(Intent.EXTRA_TEXT, spec.body)
            }
            if (uris.isNotEmpty()) {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    /** Opens the SMS composer; the user sends the message (attachments are not supported for SMS). */
    fun message(spec: MessageSpec): Intent =
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + spec.recipients.joinToString(";"))).apply {
            putExtra("sms_body", spec.body)
        }

    /** Content URI for files under the app's storage; null for anything else. */
    fun uriFor(path: String): Uri? {
        val file = File(path).canonicalFile
        val root = context.filesDir.canonicalFile
        if (!file.isFile || !file.path.startsWith(root.path + File.separator)) return null
        return runCatching { FileProvider.getUriForFile(context, authority, file) }.getOrNull()
    }
}
