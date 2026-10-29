package kr.b1ink.scriptwidget

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class IncomingFilesTest {
    private val a = Uri.parse("content://files/a.js")
    private val b = Uri.parse("content://files/b.js")

    @Test
    fun openWithGivesOneFile() {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(a, "text/javascript")
        assertEquals(listOf(a.toString()), incomingFiles(intent))
    }

    @Test
    fun shareGivesTheStream() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/javascript").putExtra(Intent.EXTRA_STREAM, a)
        assertEquals(listOf(a.toString()), incomingFiles(intent))
    }

    @Test
    fun shareMultipleGivesAllStreams() {
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("text/javascript")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(a, b))
        assertEquals(listOf(a.toString(), b.toString()), incomingFiles(intent))
    }

    @Test
    fun appLinksAndLauncherAreNotFiles() {
        assertEquals(emptyList(), incomingFiles(Intent(Intent.ACTION_VIEW, Uri.parse("scriptwidget://run?scriptName=a"))))
        assertEquals(emptyList(), incomingFiles(Intent(Intent.ACTION_MAIN)))
        assertEquals(emptyList(), incomingFiles(null))
    }

    // --- the system offers ScriptWidget for Scriptable exports ---

    private fun handlers(intent: Intent): List<String> {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        return context.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.name }
    }

    @Test
    fun scriptableFilesCanBeOpenedWithTheApp() {
        val byPath = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://files/docs/sub-calendar.scriptable"), "application/octet-stream")
        val noExtension = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://downloads/document/42"), "application/octet-stream")

        kotlin.test.assertTrue(MainActivity::class.java.name in handlers(byPath))
        kotlin.test.assertTrue(MainActivity::class.java.name in handlers(noExtension))
    }

    @Test
    fun scriptableFilesCanBeSharedToTheApp() {
        val share = Intent(Intent.ACTION_SEND).setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, a)

        kotlin.test.assertTrue(MainActivity::class.java.name in handlers(share))
    }
}
