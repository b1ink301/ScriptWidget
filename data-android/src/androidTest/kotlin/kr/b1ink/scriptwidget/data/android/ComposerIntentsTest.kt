package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.data.engine.MailSpec
import kr.b1ink.scriptwidget.data.engine.MessageSpec
import kr.b1ink.scriptwidget.data.engine.ShareSpec
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ComposerIntentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val intents = ComposerIntents(context)
    private val dir = File(context.filesDir, "script_storage/tmp/composer-test").apply { mkdirs() }
    private val a = File(dir, "a.txt").apply { writeText("a") }
    private val b = File(dir, "b.png").apply { writeText("b") }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun sharingTextOnly() {
        val intent = intents.share(ShareSpec(texts = listOf("Look", "https://example.com")))

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        assertEquals("Look\nhttps://example.com", intent.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun sharingFilesUsesContentUrisWithReadPermission() {
        val intent = intents.share(ShareSpec(texts = listOf("hi"), files = listOf(a.path, b.path)))

        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)!!
        assertEquals(2, uris.size)
        assertTrue(uris.all { it.scheme == "content" }, uris.toString())
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals("hi", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals("a", context.contentResolver.openInputStream(uris[0])!!.use { String(it.readBytes()) })
    }

    @Test
    fun mailTargetsEmailAppsWithFields() {
        val intent = intents.mail(MailSpec(to = listOf("a@example.com"), cc = listOf("c@example.com"), subject = "S", body = "B", attachments = listOf(a.path)))

        assertEquals("mailto", intent.selector?.data?.scheme)
        assertEquals(listOf("a@example.com"), intent.getStringArrayExtra(Intent.EXTRA_EMAIL)!!.toList())
        assertEquals(listOf("c@example.com"), intent.getStringArrayExtra(Intent.EXTRA_CC)!!.toList())
        assertEquals("S", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("B", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(1, IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)!!.size)
    }

    @Test
    fun messageOpensTheSmsComposer() {
        val intent = intents.message(MessageSpec(recipients = listOf("010-1234-5678", "01099998888"), body = "On my way"))

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:010-1234-5678;01099998888", intent.data.toString())
        assertEquals("On my way", intent.getStringExtra("sms_body"))
    }

    @Test
    fun filesOutsideTheAppAreNotShared() {
        val intent = intents.share(ShareSpec(files = listOf("/etc/hosts")))

        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals(null, IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
    }
}
