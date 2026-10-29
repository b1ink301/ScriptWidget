package kr.b1ink.scriptwidget.data.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import kr.b1ink.scriptwidget.data.engine.UrlOpener

/** Opens URLs with whatever app handles them (backs `Safari`). */
class AndroidUrlOpener(context: Context) : UrlOpener {
    private val context = context.applicationContext

    override fun open(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            throw IllegalArgumentException("No app can open $url")
        }
    }
}
