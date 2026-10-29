package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ImageNode
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

/** Scriptable's Data, Image.fromData and Notification on real QuickJS. */
class DataNotificationContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val http = FakeHttpGateway()
    private val images = FakeImageStore()
    private val notifier = FakeNotifier()
    private val files by lazy { SandboxedFileSystem(tmp.newFolder()) }

    private suspend fun run(script: String) = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(http = http, images = images, files = files, notifier = notifier),
        timeoutMillis = 2_000,
    ).execute(script, ScriptEnvironment(runsInWidget = false), "data")

    private suspend fun out(script: String): String {
        val result = run(script)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    // --- Data ---

    @Test
    fun `converts between strings, base64 and bytes`() = runTest {
        assertEquals(
            listOf("7Iqk7YGs66a97Yq4", "스크립트", "[104,105]", "hi", "true").joinToString("\n"),
            out(
                """
                const d = Data.fromString("스크립트");
                console.log(d.toBase64String());
                console.log(Data.fromBase64String(d.toBase64String()).toRawString());
                console.log(JSON.stringify(Data.fromString("hi").getBytes()));
                console.log(Data.fromBytes([104, 105]).toRawString());
                console.log(d instanceof Data);
                """
            ),
        )
    }

    @Test
    fun `invalid base64 is rejected`() = runTest {
        assertContains(assertNotNull(run("""Data.fromBase64String("@@@");""").error), "base64")
    }

    @Test
    fun `Request load returns Data and FileManager reads and writes it`() = runTest {
        http.bytes = { byteArrayOf(0, 1, 2, 255.toByte()) }
        val result = run(
            """
            const data = await new Request("https://bin").load();
            const fm = FileManager.local();
            const path = fm.joinPath(fm.documentsDirectory(), "blob.bin");
            fm.write(path, data);
            console.log(JSON.stringify(fm.read(path).getBytes()), fm.read(fm.joinPath(fm.documentsDirectory(), "none")));
            console.log(JSON.stringify(Data.fromFile(path).getBytes()));
            """
        )
        assertNull(result.error, result.error)
        assertEquals(listOf("[0,1,2,255] null", "[0,1,2,255]"), result.logs.map { it.message })
        assertContentEquals(byteArrayOf(0, 1, 2, 255.toByte()), files.readBytes("${files.documentsDirectory}/blob.bin"))
    }

    @Test
    fun `images convert to and from Data`() = runTest {
        val result = run(
            """
            const img = Image.fromData(Data.fromBytes([1, 2, 3]));
            const png = Data.fromPNG(await new Request("https://img").loadImage());
            console.log(JSON.stringify(png.getBytes()));
            const w = new ListWidget(); w.addImage(img); Script.setWidget(w);
            """
        )
        assertNull(result.error, result.error)
        assertEquals(listOf(0, 1), images.stored.map { it.index })
        assertEquals(3, images.stored[0].size)
        assertEquals("[9,8,7]", result.logs.single().message)
        assertTrue((result.widget!!.children.single() as ImageNode).src.endsWith("img_0.png"))
    }

    // --- Notification ---

    @Test
    fun `schedules a notification with its content`() = runTest {
        out(
            """
            const n = new Notification();
            n.identifier = "rain";
            n.title = "비 소식";
            n.subtitle = "서울";
            n.body = "오후 3시부터 비";
            n.openURL = "https://weather.example";
            n.threadIdentifier = "weather";
            await n.schedule();
            """
        )
        assertEquals(
            NotificationSpec(
                identifier = "rain", title = "비 소식", subtitle = "서울", body = "오후 3시부터 비",
                threadIdentifier = "weather", openURL = "https://weather.example", triggerAt = null, scriptName = "data",
            ),
            notifier.scheduled.single(),
        )
    }

    @Test
    fun `trigger dates and removal`() = runTest {
        out(
            """
            const n = new Notification();
            n.title = "later";
            n.setTriggerDate(new Date(1700000000000));
            await n.schedule();
            await n.remove();
            await Notification.removePending(["a", "b"]);
            """
        )
        val spec = notifier.scheduled.single()
        assertEquals(1_700_000_000_000L, spec.triggerAt)
        assertTrue(spec.identifier.isNotBlank())
        assertEquals(listOf(listOf(spec.identifier), listOf("a", "b")), notifier.removed)
    }

    @Test
    fun `notification failures reject the schedule promise`() = runTest {
        notifier.failure = SecurityException("Notification permission not granted")
        val out = out("""try { await new Notification().schedule(); } catch (e) { console.log(e.message); }""")
        assertContains(out, "Notification permission not granted")
    }

    @Test
    fun `pending and delivered notifications are listed as Notification objects`() = runTest {
        notifier.pendingSpecs = listOf(
            NotificationSpec(identifier = "SUBCALAT1AT2", title = "결제 예정", body = "Netflix", triggerAt = 5_000L, userInfo = """{"id":1}""", scriptName = "sub"),
        )
        notifier.deliveredSpecs = listOf(NotificationSpec(identifier = "done", title = "끝"))

        val log = out(
            """
            const pending = await Notification.allPending();
            console.log(pending.length, pending[0] instanceof Notification, pending[0].identifier, pending[0].title, pending[0].body);
            console.log(pending[0].nextTriggerDate.getTime(), pending[0].userInfo.id, pending[0].scriptName);
            const delivered = await Notification.allDelivered();
            console.log(delivered.map(n => n.identifier).join(","));
            """
        )

        assertEquals(listOf("1 true SUBCALAT1AT2 결제 예정 Netflix", "5000 1 sub", "done"), log.lines())
    }

    @Test
    fun `current is the notification that started the run`() = runTest {
        val log = out(
            """
            console.log(Notification.current() === null);
            await Notification.resetCurrent();
            """
        )
        assertEquals("true", log)
    }
}
