package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kr.b1ink.scriptwidget.domain.model.Gradient
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.LogLine
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract tests for the Scriptable-compatible JS API (prelude.js) running on real QuickJS.
 */
class QuickJsScriptEngineTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val http = FakeHttpGateway()
    private val images = FakeImageStore()
    private val files by lazy { SandboxedFileSystem(tmp.newFolder("sandbox")) }
    private val secrets = InMemorySecretStore()
    private fun services(http: HttpGateway = this.http) = fakeServices(http = http, images = images, files = files, secrets = secrets)
    private val engine by lazy { QuickJsScriptEngine(prelude = Prelude.load(), services = services(), timeoutMillis = 2_000) }
    private val appEnv = ScriptEnvironment(runsInWidget = false)

    private suspend fun run(script: String, env: ScriptEnvironment = appEnv) = engine.execute(script, env, "test")

    @Test
    fun `builds a widget tree from ListWidget`() = runTest {
        val result = run(
            """
            const w = new ListWidget();
            w.backgroundColor = new Color("#112233");
            w.setPadding(1, 2, 3, 4);
            const t = w.addText("Hello");
            t.font = Font.boldSystemFont(20);
            t.textColor = Color.white();
            t.lineLimit = 2;
            t.centerAlignText();
            w.addSpacer();
            const s = w.addStack();
            s.layoutVertically();
            s.addSpacer(8);
            Script.setWidget(w);
            """
        )

        assertNull(result.error)
        val root = assertNotNull(result.widget)
        assertEquals("#FF112233", root.bg)
        assertEquals(listOf(1f, 2f, 3f, 4f), root.padding)
        assertEquals(
            TextNode(text = "Hello", color = "#FFFFFFFF", size = 20f, weight = "bold", lineLimit = 2, align = "center"),
            root.children[0],
        )
        assertEquals(SpacerNode(null), root.children[1])
        assertEquals(StackNode(dir = "v", children = listOf(SpacerNode(8f))), root.children[2])
    }

    @Test
    fun `colors support short hex, alpha and named colors`() = runTest {
        val result = run(
            """
            const w = new ListWidget();
            w.addText("a").textColor = new Color("#abc", 0.5);
            w.addText("b").textColor = Color.red();
            Script.setWidget(w);
            """
        )
        val texts = result.widget!!.children.map { (it as TextNode).color }
        assertEquals(listOf("#80AABBCC", "#FFFF3B30"), texts)
    }

    @Test
    fun `exposes config and args`() = runTest {
        val env = ScriptEnvironment(runsInWidget = true, widgetFamily = WidgetFamily.Large, widgetParameter = "KRW")
        val result = run(
            """
            console.log(config.runsInWidget, config.runsInApp, config.widgetFamily, args.widgetParameter);
            const w = new ListWidget(); Script.setWidget(w);
            """,
            env,
        )
        assertEquals(listOf(LogLine("log", "true false large KRW")), result.logs)
    }

    @Test
    fun `captures console levels and formats objects as JSON`() = runTest {
        val result = run("""console.log("a", {b: 1}); console.warn("w"); console.error(new Error("e"));""")
        assertEquals(
            listOf(LogLine("log", """a {"b":1}"""), LogLine("warn", "w"), LogLine("error", "Error: e")),
            result.logs,
        )
    }

    @Test
    fun `Request loadJSON goes through the http gateway`() = runTest {
        http.responses = { HttpResponseSpec(it.url, 201, mapOf("X-A" to "1"), "application/json", """{"v":42}""") }
        val result = run(
            """
            const r = new Request("https://api.test/x");
            r.method = "POST";
            r.headers = {"Authorization": "Bearer t"};
            r.body = {q: 1};
            r.timeoutInterval = 5;
            const json = await r.loadJSON();
            console.log(json.v, r.response.statusCode, r.response.headers["X-A"]);
            """
        )

        assertNull(result.error)
        assertEquals("42 201 1", result.logs.single().message)
        val spec = http.requests.single()
        assertEquals("https://api.test/x", spec.url)
        assertEquals("POST", spec.method)
        assertEquals("""{"q":1}""", spec.body)
        assertEquals("Bearer t", spec.headers["Authorization"])
        assertEquals("application/json", spec.headers["Content-Type"])
        assertEquals(5.0, spec.timeout)
    }

    @Test
    fun `network failures reject the promise and can be caught`() = runTest {
        val failingEngine = QuickJsScriptEngine(Prelude.load(), services(FakeHttpGateway.failing("offline")), 2_000)
        val result = failingEngine.execute(
            """
            try { await new Request("https://x").loadString(); } catch (e) { console.log("caught", e.message); }
            """,
            appEnv,
            "test",
        )
        assertNull(result.error)
        assertContains(result.logs.single().message, "offline")
    }

    @Test
    fun `uncaught network failures become the run error`() = runTest {
        val failingEngine = QuickJsScriptEngine(Prelude.load(), services(FakeHttpGateway.failing("offline")), 2_000)
        val result = failingEngine.execute("""await new Request("https://x").loadString();""", appEnv, "test")
        assertContains(assertNotNull(result.error), "offline")
    }

    @Test
    fun `loadImage stores bytes per run and the tree references the cached file`() = runTest {
        val env = appEnv.copy(cacheKey = "widget-3")
        val result = run(
            """
            const img = await new Request("https://img").loadImage();
            console.log(img.size.width, img.size.height);
            const w = new ListWidget();
            const i = w.addImage(img);
            i.imageSize = new Size(24, 24);
            i.cornerRadius = 6;
            Script.setWidget(w);
            """,
            env,
        )

        assertNull(result.error)
        assertEquals("40 30", result.logs.single().message)
        val stored = images.stored.single()
        assertTrue(stored.runKey.startsWith("widget-3/"), stored.runKey)
        assertEquals(
            ImageNode(src = "/cache/${stored.runKey}/img_0.png", width = 40, height = 30, size = listOf(24f, 24f), cornerRadius = 6f),
            result.widget!!.children.single(),
        )
        assertEquals(listOf("widget-3" to stored.runKey), images.retained)
    }

    @Test
    fun `failed runs discard their images and keep the previous ones`() = runTest {
        val result = run("""await new Request("https://img").loadImage(); throw new Error("later");""")

        assertNotNull(result.error)
        assertTrue(images.retained.isEmpty())
        assertEquals(listOf(images.stored.single().runKey), images.cleared)
    }

    @Test
    fun `timers and promises behave like a browser`() = runTest {
        val result = run(
            """
            const order = [];
            const wait = (ms, v) => new Promise(r => setTimeout(() => { order.push(v); r(v); }, ms));
            const all = await Promise.all([wait(60, "slow"), wait(10, "fast")]);
            let fired = false;
            const id = setTimeout(() => { fired = true; }, 5);
            clearTimeout(id);
            await sleep(20);
            console.log(all.join(","), order.join(","), fired);
            """
        )
        assertNull(result.error)
        assertEquals("slow,fast fast,slow false", result.logs.single().message)
    }

    @Test
    fun `runtime errors report the user's line number`() = runTest {
        val result = run("const a = 1;\nconst b = 2;\nundefinedFn();")
        val error = assertNotNull(result.error)
        assertContains(error, "undefinedFn")
        assertContains(error, "test.js:3")
    }

    @Test
    fun `syntax errors are reported`() = runTest {
        assertContains(assertNotNull(run("const = ;").error), "SyntaxError")
    }

    @Test
    fun `infinite loops are interrupted by the timeout`() = runTest {
        val shortEngine = QuickJsScriptEngine(Prelude.load(), services(), timeoutMillis = 300)
        val result = shortEngine.execute("while (true) {}", appEnv, "test")
        assertContains(assertNotNull(result.error), "timed out")
    }

    @Test
    fun `setWidget only accepts ListWidget`() = runTest {
        assertContains(assertNotNull(run("Script.setWidget({});").error), "ListWidget")
    }

    @Test
    fun `addImage rejects non-images`() = runTest {
        assertContains(assertNotNull(run("new ListWidget().addImage('x');").error), "Image")
    }

    @Test
    fun `each run gets a fresh global scope`() = runTest {
        run("globalThis.leak = 1;")
        val result = run("console.log(typeof leak);")
        assertEquals("undefined", result.logs.single().message)
    }

    @Test
    fun `presentSmall also produces the widget`() = runTest {
        val result = run("""const w = new ListWidget(); w.addText("p"); await w.presentSmall();""")
        assertEquals(TextNode("p"), result.widget!!.children.single())
    }

    @Test
    fun `reports timings`() = runTest {
        val t = run("Script.setWidget(new ListWidget());").timings
        assertTrue(t.totalMs >= t.scriptMs, t.toString())
    }

    @Test
    fun `refreshAfterDate is exported as epoch millis`() = runTest {
        val result = run(
            """
            const w = new ListWidget();
            w.refreshAfterDate = new Date(1700000000000);
            Script.setWidget(w);
            """
        )
        assertEquals(1_700_000_000_000L, result.widget!!.refreshAfter)
    }

    @Test
    fun `refreshAfterDate accepts only dates`() = runTest {
        val result = run("const w = new ListWidget(); w.refreshAfterDate = 'soon'; Script.setWidget(w);")
        assertContains(assertNotNull(result.error), "refreshAfterDate")
    }

    @Test
    fun `LinearGradient is exported for widgets and stacks`() = runTest {
        val result = run(
            """
            const g = new LinearGradient();
            g.colors = [new Color("#000000"), new Color("#ffffff", 0.5)];
            g.locations = [0, 1];
            g.startPoint = new Point(0, 0);
            g.endPoint = new Point(1, 1);
            const w = new ListWidget();
            w.backgroundGradient = g;
            const s = w.addStack();
            const g2 = new LinearGradient();
            g2.colors = [Color.red(), Color.blue()];
            s.backgroundGradient = g2;
            Script.setWidget(w);
            """
        )
        assertNull(result.error)
        val root = result.widget!!
        assertEquals(Gradient(listOf("#FF000000", "#80FFFFFF"), listOf(0f, 1f), listOf(0f, 0f), listOf(1f, 1f)), root.gradient)
        assertEquals(Gradient(listOf("#FFFF3B30", "#FF007AFF")), (root.children.single() as StackNode).gradient)
    }

    @Test
    fun `backgroundImage takes an Image`() = runTest {
        val result = run(
            """
            const w = new ListWidget();
            w.backgroundImage = await new Request("https://img").loadImage();
            Script.setWidget(w);
            """
        )
        assertEquals("/cache/${images.stored.single().runKey}/img_0.png", result.widget!!.backgroundImage)
    }

    @Test
    fun `invalid backgrounds are reported`() = runTest {
        assertContains(assertNotNull(run("const w = new ListWidget(); w.backgroundGradient = 'red'; Script.setWidget(w);").error), "LinearGradient")
        assertContains(assertNotNull(run("const w = new ListWidget(); w.backgroundImage = 'x.png'; Script.setWidget(w);").error), "Image")
    }
}
