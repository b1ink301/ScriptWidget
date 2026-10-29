package kr.b1ink.scriptwidget

import android.content.Intent
import android.net.Uri
import android.os.Build

/** Files handed to the app with "Open with" (VIEW) or the share sheet (SEND / SEND_MULTIPLE). */
fun incomingFiles(intent: Intent?): List<String> {
    intent ?: return emptyList()
    val uris: List<Uri?> = when (intent.action) {
        Intent.ACTION_VIEW -> listOf(intent.data)
        Intent.ACTION_SEND -> listOf(intent.stream())
        Intent.ACTION_SEND_MULTIPLE -> intent.streams()
        else -> emptyList()
    }
    return uris.filterNotNull().filter { it.scheme == "content" || it.scheme == "file" }.map(Uri::toString)
}

private fun Intent.stream(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    else @Suppress("DEPRECATION") getParcelableExtra(Intent.EXTRA_STREAM)

private fun Intent.streams(): List<Uri?> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    else @Suppress("DEPRECATION") getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
