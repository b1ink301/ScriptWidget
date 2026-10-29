package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** WebView and DocumentPicker on real QuickJS with faked platform services. */
class WebDocumentContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val web = FakeWebPages()
    private val ui = FakeUiPresenter()
    private val images = FakeImageStore()
    private val files by lazy { SandboxedFileSystem(tmp.newFolder()) }
    private val documents by lazy { FakeDocuments { files } }

    private suspend fun run(script: String, inWidget: Boolean = false) = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(images = images, files = files, ui = ui, web = web, documents = documents),
        timeoutMillis = 3_000,
    ).execute(script, ScriptEnvironment(runsInWidget = inWidget), "web")

    private suspend fun out(script: String, inWidget: Boolean = false): String {
        val result = run(script, inWidget)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    // --- WebView ---

    @Test
    fun `loads pages, evaluates JavaScript and reads the HTML`() = runTest {
        web.answers["document.title"] = "\"Hello\""
        web.answers["[1, {a: 2}]"] = "[1,{\"a\":2}]"
        val log = out(
            """
            const wv = new WebView();
            await wv.loadHTML("<title>Hello</title>", "https://base.example");
            console.log(await wv.evaluateJavaScript("document.title"));
            console.log(JSON.stringify(await wv.evaluateJavaScript("[1, {a: 2}]")));
            console.log(await wv.getHTML());
            await wv.loadURL("https://example.com");
            await wv.waitForLoad();
            """
        )
        assertEquals("Hello\n[1,{\"a\":2}]\n<title>Hello</title>", log)
        val page = web.pages.single()
        assertEquals(listOf("html:https://base.example", "eval:false:document.title", "eval:false:[1, {a: 2}]", "url:https://example.com"), page.log)
    }

    @Test
    fun `evaluateJavaScript with a completion callback`() = runTest {
        web.answers["setTimeout(() => completion(42), 10)"] = "42"
        assertEquals("42", out("""const wv = new WebView(); console.log(await wv.evaluateJavaScript("setTimeout(() => completion(42), 10)", true));"""))
        assertTrue(web.pages.single().log.single().startsWith("eval:true:"))
    }

    @Test
    fun `web views work during widget refreshes (headless scraping)`() = runTest {
        web.answers["document.querySelector('h1').innerText"] = "\"Headline\""
        val log = out(
            """
            const wv = new WebView();
            await wv.loadURL("https://news.example");
            console.log(await wv.evaluateJavaScript("document.querySelector('h1').innerText"));
            Script.setWidget(new ListWidget());
            """,
            inWidget = true,
        )
        assertEquals("Headline", log)
    }

    @Test
    fun `pages are closed when the run ends`() = runTest {
        out("""const a = new WebView(); const b = new WebView(); await a.loadHTML("x"); await b.loadHTML("y");""")
        assertEquals(listOf(true, true), web.pages.map { it.closed })
    }

    @Test
    fun `present shows the page in the app but not in widgets`() = runTest {
        out("""const wv = new WebView(); await wv.loadHTML("<b>hi</b>"); await wv.present(true);""")
        assertEquals(true, ui.presentedPages.single().second)

        val result = run("""const wv = new WebView(); await wv.loadHTML("x"); await wv.present();""", inWidget = true)
        assertContains(assertNotNull(result.error), "widget")
    }

    @Test
    fun `static helpers load and present in one call`() = runTest {
        out("""await WebView.loadHTML("<i>x</i>"); await WebView.loadURL("https://example.org", null, false);""")
        assertEquals(2, ui.presentedPages.size)
        assertEquals(listOf("html:"), web.pages[0].log)
        assertEquals(listOf("url:https://example.org"), web.pages[1].log)
    }

    // --- DocumentPicker ---

    @Test
    fun `picked documents are copied into the sandbox and readable with FileManager`() = runTest {
        documents.contents["content://1"] = "notes.txt" to "hello".toByteArray()
        documents.contents["content://2"] = "data.json" to "{}".toByteArray()
        ui.documentAnswers += listOf("content://1", "content://2")
        val log = out(
            """
            const paths = await DocumentPicker.open(["public.text", "public.json"]);
            console.log(paths.length, FileManager.local().readString(paths[0]), FileManager.local().fileName(paths[1], true));
            """
        )
        assertEquals("2 hello data.json", log)
        assertEquals(DocumentRequest(kind = "open", mimeTypes = listOf("text/*", "application/json"), multiple = true), ui.documentRequests.single())
    }

    @Test
    fun `openFile returns one path and cancelling rejects`() = runTest {
        documents.contents["content://img"] = "pic.png" to byteArrayOf(1)
        ui.documentAnswers += listOf("content://img")
        assertTrue(out("""console.log(await DocumentPicker.openFile());""").endsWith("/picked/pic.png"))
        assertEquals(DocumentRequest(kind = "open", mimeTypes = listOf("*/*"), multiple = false), ui.documentRequests.single())

        ui.documentAnswers += emptyList<String>()
        assertContains(assertNotNull(run("""await DocumentPicker.openFile();""").error), "cancel")
    }

    @Test
    fun `exports write the chosen document`() = runTest {
        ui.documentAnswers += listOf("content://out1")
        ui.documentAnswers += listOf("content://out2")
        ui.documentAnswers += listOf("content://out3")
        out(
            """
            await DocumentPicker.exportString("hi there", "note.txt");
            const fm = FileManager.local();
            const p = fm.joinPath(fm.documentsDirectory(), "a.json");
            fm.writeString(p, "{}");
            await DocumentPicker.export(p);
            await DocumentPicker.exportImage(await new Request("https://img").loadImage(), "logo.png");
            """
        )
        assertEquals(
            listOf(
                DocumentRequest(kind = "create", mimeTypes = listOf("text/plain"), suggestedName = "note.txt"),
                DocumentRequest(kind = "create", mimeTypes = listOf("application/json"), suggestedName = "a.json"),
                DocumentRequest(kind = "create", mimeTypes = listOf("image/png"), suggestedName = "logo.png"),
            ),
            ui.documentRequests,
        )
        assertContentEquals("hi there".toByteArray(), documents.written["content://out1"])
        assertContentEquals("{}".toByteArray(), documents.written["content://out2"])
        assertContentEquals(FakeImageStore.PNG_BYTES, documents.written["content://out3"])
    }

    @Test
    fun `document pickers need the app`() = runTest {
        assertContains(assertNotNull(run("""await DocumentPicker.openFile();""", inWidget = true).error), "widget")
    }
}
