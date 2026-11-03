package kr.b1ink.scriptwidget.data.android

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class AndroidClipboardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun worksWhenCreatedAndUsedOnAWorkerThread() {
        // Widget refreshes create the script engine (and this clipboard) on a worker thread without a
        // Looper. Before Android 9 the platform ClipboardManager threw there ("Can't create handler").
        val worker = Executors.newSingleThreadExecutor()
        try {
            val copied = worker.submit<String?> {
                val clipboard = AndroidClipboard(context)
                clipboard.copy("from a worker")
                clipboard.paste()
            }.get()
            // Reading back is allowed for this (instrumented, foreground) app.
            copied?.let { assertEquals("from a worker", it) }
        } finally {
            worker.shutdown()
        }
    }
}
