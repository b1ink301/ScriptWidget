package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pasteboard, ShareSheet, Mail, Message, CallbackURL, Speech and Dictation. */
class SystemIntegrationContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clipboard = FakeClipboard()
    private val composer = FakeComposer()
    private val callbacks = FakeCallbackBroker()
    private val speaker = FakeSpeaker()
    private val ui = FakeUiPresenter()
    private val images = FakeImageStore()
    private val files by lazy { SandboxedFileSystem(tmp.newFolder()) }

    private suspend fun run(script: String, inWidget: Boolean = false) = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(
            images = images, files = files, ui = ui, clipboard = clipboard, composer = composer,
            callbacks = callbacks, speaker = speaker,
        ),
        timeoutMillis = 3_000,
    ).execute(script, ScriptEnvironment(runsInWidget = inWidget), "sys")

    private suspend fun out(script: String, inWidget: Boolean = false): String {
        val result = run(script, inWidget)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    // --- Pasteboard ---

    @Test
    fun `pasteboard copies and pastes strings`() = runTest {
        assertEquals("hello hello null", out("""Pasteboard.copy("hello"); console.log(Pasteboard.paste(), Pasteboard.pasteString(), Pasteboard.pasteImage());"""))
        out("""Pasteboard.copyString("again");""")
        assertEquals("again", clipboard.text)
    }

    @Test
    fun `copying works during widget refreshes`() = runTest {
        out("""Pasteboard.copy("from widget"); Script.setWidget(new ListWidget());""", inWidget = true)
        assertEquals("from widget", clipboard.text)
    }

    // --- ShareSheet ---

    @Test
    fun `share sheet receives text, urls, images and files`() = runTest {
        val log = out(
            """
            const img = await new Request("https://img").loadImage();
            const fm = FileManager.local();
            const p = fm.joinPath(fm.documentsDirectory(), "r.txt"); fm.writeString(p, "x");
            const r = await ShareSheet.present(["Look", "https://example.com", img, p]);
            console.log(r.completed);
            """
        )
        assertEquals("true", log)
        val spec = composer.shares.single()
        assertEquals(listOf("Look", "https://example.com"), spec.texts)
        assertEquals(2, spec.files.size)
        assertTrue(spec.files[0].startsWith("/cache/"))
        assertTrue(spec.files[1].endsWith("/r.txt"))
    }

    // --- Mail & Message ---

    @Test
    fun `mail opens the composer with recipients, body and attachments`() = runTest {
        out(
            """
            const m = new Mail();
            m.toRecipients = ["a@example.com"];
            m.ccRecipients = ["c@example.com"];
            m.subject = "Report";
            m.body = "<b>hi</b>";
            m.isBodyHTML = true;
            m.addImageAttachment(await new Request("https://img").loadImage());
            m.addDataAttachment(Data.fromString("csv,1"), "text/csv", "data.csv");
            await m.send();
            """
        )
        val mail = composer.mails.single()
        assertEquals(listOf("a@example.com"), mail.to)
        assertEquals(listOf("c@example.com"), mail.cc)
        assertEquals("Report", mail.subject)
        assertEquals("<b>hi</b>", mail.body)
        assertTrue(mail.html)
        assertEquals(2, mail.attachments.size)
        assertEquals("data.csv", mail.attachments[1].substringAfterLast('/'))
        assertEquals("csv,1", files.readString(mail.attachments[1]))
    }

    @Test
    fun `message opens the SMS composer`() = runTest {
        out("""const m = new Message(); m.recipients = ["01012345678"]; m.body = "On my way"; await m.send();""")
        assertEquals(MessageSpec(recipients = listOf("01012345678"), body = "On my way"), composer.messages.single())
    }

    @Test
    fun `composers need the app`() = runTest {
        assertContains(assertNotNull(run("""await ShareSheet.present(["x"]);""", inWidget = true).error), "widget")
        assertContains(assertNotNull(run("""await new Mail().send();""", inWidget = true).error), "widget")
    }

    // --- CallbackURL ---

    @Test
    fun `x-callback-url adds the callback parameters and returns the result`() = runTest {
        callbacks.answer = CallbackResult("success", mapOf("text" to "done"))
        val log = out(
            """
            const cb = new CallbackURL("otherapp://x-callback-url/do");
            cb.addParameter("q", "hello world");
            console.log(cb.getURL());
            const r = await cb.open();
            console.log(r.text);
            """
        )
        val (url, result) = log.lines()
        assertEquals("otherapp://x-callback-url/do?q=hello%20world", url)
        assertEquals("done", result)
        val opened = callbacks.opened.single()
        assertContains(opened, "x-success=scriptwidget%3A%2F%2Fx-callback-url%2Fsuccess%3FrequestId%3D")
        assertContains(opened, "x-error=")
        assertContains(opened, "x-cancel=")
    }

    @Test
    fun `x-callback errors and cancellations reject`() = runTest {
        callbacks.answer = CallbackResult("error", mapOf("errorMessage" to "nope"))
        assertContains(assertNotNull(run("""await new CallbackURL("a://b").open();""").error), "nope")
        callbacks.answer = CallbackResult("cancel", emptyMap())
        assertContains(assertNotNull(run("""await new CallbackURL("a://b").open();""").error), "cancel")
    }

    // --- Speech & Dictation ---

    @Test
    fun `speech speaks text, also in widgets`() = runTest {
        out("""await Speech.speak("안녕하세요"); Script.setWidget(new ListWidget());""", inWidget = true)
        assertEquals(listOf("안녕하세요"), speaker.spoken)
    }

    @Test
    fun `dictation returns the recognized text`() = runTest {
        assertEquals("hello world", out("""console.log(await Dictation.start("en-US"));"""))
        assertEquals(listOf<String?>("en-US"), ui.dictations)

        ui.dictationAnswer = null
        assertContains(assertNotNull(run("""await Dictation.start();""").error), "cancel")
    }
}
