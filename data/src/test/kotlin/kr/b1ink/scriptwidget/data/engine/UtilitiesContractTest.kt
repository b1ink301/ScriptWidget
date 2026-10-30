package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Device, UUID, Timer, RelativeDateTimeFormatter, XMLParser, Safari and URLScheme. */
class UtilitiesContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val urls = FakeUrlOpener()

    private suspend fun run(script: String, env: ScriptEnvironment = ScriptEnvironment(runsInWidget = false), locale: Locale = Locale.US) =
        QuickJsScriptEngine(
            Prelude.load(),
            fakeServices(files = SandboxedFileSystem(tmp.newFolder()), urls = urls, locale = locale),
            timeoutMillis = 3_000,
        ).execute(script, env, "My Script")

    private suspend fun out(script: String, locale: Locale = Locale.US): String {
        val result = run(script, locale = locale)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    @Test
    fun `Device reports the snapshot`() = runTest {
        assertEquals(
            listOf(
                "Pixel Test Android 17 Pixel 10 Pro true false",
                "412x915 1442x3203 3.5 0.5",
                "true false false",
                "0.8 true false",
                "[\"ko-KR\",\"en-US\"] ko_KR ko true 0.25",
            ).joinToString("\n"),
            out(
                """
                console.log(Device.name(), Device.systemName(), Device.systemVersion(), Device.model(), Device.isPhone(), Device.isPad());
                const s = Device.screenSize(), r = Device.screenResolution();
                console.log(s.width + "x" + s.height, r.width + "x" + r.height, Device.screenScale(), Device.screenBrightness());
                console.log(Device.isInPortrait(), Device.isInLandscapeLeft(), Device.isFaceUp());
                console.log(Device.batteryLevel(), Device.isCharging(), Device.isFullyCharged());
                console.log(JSON.stringify(Device.preferredLanguages()), Device.locale(), Device.language(), Device.isUsingDarkAppearance(), Device.volume());
                """
            ),
        )
    }

    @Test
    fun `UUID strings are unique uppercase v4 UUIDs`() = runTest {
        val (a, b) = out("console.log(UUID.string()); console.log(UUID.string());").lines()
        assertTrue(Regex("^[0-9A-F]{8}-[0-9A-F]{4}-4[0-9A-F]{3}-[89AB][0-9A-F]{3}-[0-9A-F]{12}$").matches(a), a)
        assertTrue(a != b)
    }

    @Test
    fun `Timer fires once or repeatedly until invalidated`() = runTest {
        assertEquals(
            "once 3",
            out(
                """
                let once = 0, ticks = 0;
                Timer.schedule(20, false, () => once++);
                const t = new Timer();
                t.timeInterval = 10;
                t.repeats = true;
                t.schedule(() => { ticks++; if (ticks === 3) t.invalidate(); });
                await sleep(150);
                console.log(once === 1 ? "once" : "times:" + once, ticks);
                """
            ),
        )
    }

    @Test
    fun `RelativeDateTimeFormatter in English and Korean`() = runTest {
        val script = """
            const f = new RelativeDateTimeFormatter();
            const now = new Date(1700000000000);
            console.log(f.string(new Date(1700000000000 - 5 * 60000), now));
            console.log(f.string(new Date(1700000000000 + 3 * 3600000), now));
            f.useNamedDateTimeStyle();
            console.log(f.string(new Date(1700000000000 - 86400000), now));
            console.log(f.string(new Date(1700000000000 + 86400000), now));
            console.log(f.string(new Date(1700000000000 - 2 * 86400000), now));
            console.log(f.string(new Date(1700000000000 + 10000), now));
        """
        assertEquals(
            "5 minutes ago\nin 3 hours\nyesterday\ntomorrow\n2 days ago\nnow",
            out(script),
        )
        assertEquals(
            "5분 전\n3시간 후\n어제\n내일\n2일 전\n지금",
            out(script, Locale.KOREA),
        )
    }

    @Test
    fun `XMLParser reports SAX-style events`() = runTest {
        val result = run(
            """
            const xml = '<rss version="2.0"><channel><title>News &amp; more</title><item id="1"><title>A</title></item></channel></rss>';
            const p = new XMLParser(xml);
            const events = [];
            p.didStartDocument = () => events.push("start-doc");
            p.didStartElement = (name, attrs) => events.push("<" + name + (attrs.id ? "#" + attrs.id : "") + (attrs.version ? "@" + attrs.version : ""));
            p.foundCharacters = (text) => { if (text.trim()) events.push("'" + text + "'"); };
            p.didEndElement = (name) => events.push("/" + name);
            p.didEndDocument = () => events.push("end-doc");
            console.log(p.parse(), events.join(" "));
            const bad = new XMLParser("<a><b></a>");
            let reported = "";
            bad.parseErrorOccurred = (e) => { reported = e; };
            console.log(bad.parse(), reported.length > 0);
            """
        )
        assertNull(result.error, result.error)
        assertEquals(
            listOf(
                "true start-doc <rss@2.0 <channel <title 'News & more' /title <item#1 <title 'A' /title /item /channel /rss end-doc",
                "false true",
            ),
            result.logs.map { it.message },
        )
    }

    @Test
    fun `Safari opens URLs`() = runTest {
        out("""Safari.open("https://example.com"); await Safari.openInApp("https://example.org", false);""")
        assertEquals(listOf("https://example.com", "https://example.org"), urls.opened)
    }

    @Test
    fun `URLScheme builds deep links for the current script`() = runTest {
        assertEquals(
            listOf(
                "scriptwidget://",
                "scriptwidget://run?scriptName=My%20Script",
                "scriptwidget://open?scriptName=My%20Script",
                "scriptwidget://settings?scriptName=My%20Script",
            ).joinToString("\n"),
            out(
                """
                console.log(URLScheme.forOpeningScriptSettings ? URLScheme.forOpeningApp() : "");
                console.log(URLScheme.forRunningScript());
                console.log(URLScheme.forOpeningScript());
                console.log(URLScheme.forOpeningScriptSettings());
                """
            ),
        )
    }

    @Test
    fun `URLScheme exposes query parameters from args`() = runTest {
        val result = run("""console.log(URLScheme.parameter("q"), JSON.stringify(URLScheme.allParameters()), args.queryParameters.q);""",
            env = ScriptEnvironment(runsInWidget = false, queryParameters = mapOf("q" to "seoul")))
        assertNull(result.error, result.error)
        assertEquals("""seoul {"q":"seoul"} seoul""", result.logs.single().message)
    }

    @Test
    fun `Safari cannot open from a widget refresh`() = runTest {
        val result = run("""Safari.open("https://example.com");""", env = ScriptEnvironment(runsInWidget = true))
        assertContains(assertNotNull(result.error), "widget")
        assertTrue(urls.opened.isEmpty())
    }

    @Test
    fun `atob and btoa convert base64 like browsers`() = runTest {
        assertEquals(
            "aGVsbG8gd29ybGQ= hello world SGk= Hi  true",
            out(
                """
                let threw = false;
                try { btoa("한") } catch (e) { threw = true }
                console.log(btoa("hello world"), atob("aGVsbG8gd29ybGQ="), btoa("Hi"), atob("SGk"), atob(""), threw);
                """.trimIndent(),
            ),
        )
    }
}
