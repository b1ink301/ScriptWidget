package kr.b1ink.scriptwidget.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SharedContentImportTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val sandbox = File(context.filesDir, "share-import-test").apply { mkdirs() }
    private val source = File(context.filesDir, "script_storage/share-src").apply { mkdirs() }

    @After
    fun tearDown() {
        sandbox.deleteRecursively()
        source.deleteRecursively()
    }

    private fun uriOf(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.scriptfiles", file)

    @Test
    fun copiesSharedFilesIntoTheSandboxAndKeepsTheText() {
        val photo = File(source, "photo.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val doc = File(source, "memo.txt").apply { writeText("memo") }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_TEXT, "https://example.com")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uriOf(photo), uriOf(doc)))

        val input = SharedContent.read(context, intent, tmpDirectory = sandbox.path)

        assertEquals(listOf("https://example.com"), input.urls)
        assertEquals(1, input.images.size)
        assertEquals(1, input.fileURLs.size)
        assertTrue(input.images.single().startsWith(sandbox.path))
        assertTrue(input.images.single().endsWith("photo.png"))
        assertEquals("memo", File(input.fileURLs.single()).readText())
    }

    @Test
    fun plainTextShareHasNoFiles() {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "안녕")

        val input = SharedContent.read(context, intent, tmpDirectory = sandbox.path)

        assertEquals(listOf("안녕"), input.plainTexts)
        assertTrue(input.fileURLs.isEmpty() && input.images.isEmpty())
    }

    @Test
    fun runIntentCarriesTheScriptAndInput() {
        val input = kr.b1ink.scriptwidget.domain.model.ShareInput(listOf("t"), listOf("https://u"), listOf("/f"), listOf("/i.png"))

        val parsed = SharedRun.parse(SharedRun.intent(context, "Weather", input))

        assertEquals("Weather" to input, parsed)
        assertEquals(null, SharedRun.parse(Intent(Intent.ACTION_VIEW)))
    }
}
