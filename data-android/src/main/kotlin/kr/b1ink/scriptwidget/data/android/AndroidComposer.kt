package kr.b1ink.scriptwidget.data.android

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kr.b1ink.scriptwidget.data.engine.Clipboard
import kr.b1ink.scriptwidget.data.engine.Composer
import kr.b1ink.scriptwidget.data.engine.MailSpec
import kr.b1ink.scriptwidget.data.engine.MessageSpec
import kr.b1ink.scriptwidget.data.engine.ShareSpec
import kr.b1ink.scriptwidget.data.engine.Speaker
import java.util.UUID

/** Starts the share sheet, mail and SMS composers. */
class AndroidComposer(context: Context) : Composer {
    private val context = context.applicationContext
    private val intents = ComposerIntents(context)

    override fun share(spec: ShareSpec) =
        start(Intent.createChooser(intents.share(spec), null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))

    override fun mail(spec: MailSpec) = start(intents.mail(spec))

    override fun message(spec: MessageSpec) = start(intents.message(spec))

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            throw IllegalStateException("No app can handle this (${intent.action})")
        }
    }
}

class AndroidClipboard(context: Context) : Clipboard {
    private val manager = context.applicationContext.getSystemService(ClipboardManager::class.java)

    override fun copy(text: String) {
        manager.setPrimaryClip(ClipData.newPlainText("ScriptWidget", text))
    }

    /** Android 10+ only lets the foreground app read the clipboard; otherwise this is null. */
    override fun paste(): String? = runCatching {
        manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(null)?.toString()
    }.getOrNull()
}

/** `Speech.speak()` with the platform TextToSpeech engine. */
class AndroidSpeaker(context: Context, private val timeoutMillis: Long = 60_000) : Speaker {
    private val context = context.applicationContext

    override suspend fun speak(text: String) {
        val ready = CompletableDeferred<TextToSpeech>()
        val done = CompletableDeferred<Unit>()
        var tts: TextToSpeech? = null
        withContext(Dispatchers.Main) {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) ready.complete(tts!!)
                else ready.completeExceptionally(IllegalStateException("Text-to-speech is unavailable"))
            }
        }
        try {
            withTimeout(timeoutMillis) {
                val engine = ready.await()
                val id = UUID.randomUUID().toString()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) { if (utteranceId == id) done.complete(Unit) }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId == id) done.completeExceptionally(IllegalStateException("Text-to-speech failed"))
                    }
                })
                if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
                    throw IllegalStateException("Text-to-speech failed")
                }
                done.await()
            }
        } finally {
            tts?.shutdown()
        }
    }
}
