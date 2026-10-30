package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.NotificationPayload
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ShareInput
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a script learns why it runs: `config.runs*` flags and the matching `args`. */
class RunContextContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val notifier = FakeNotifier()
    private val files by lazy { SandboxedFileSystem(tmp.newFolder()) }

    private suspend fun out(script: String, env: ScriptEnvironment, name: String = "ctx"): String {
        val result = QuickJsScriptEngine(
            Prelude.load(), fakeServices(files = files, images = FakeImageStore(), notifier = notifier), timeoutMillis = 2_000,
        ).execute(script, env, name)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    private val flags = """
        console.log(["runsInApp","runsInWidget","runsInActionExtension","runsInNotification","runsFromHomeScreen","runsFromAutomation","runsWithSiri"]
          .filter(k => config[k] === true).join(","));
    """

    @Test
    fun `config flags follow the run source`() = runTest {
        assertEquals("runsInApp", out(flags, ScriptEnvironment(runsInWidget = false)))
        assertEquals("runsInWidget", out(flags, ScriptEnvironment(runsInWidget = true)))
        assertEquals("runsInActionExtension", out(flags, ScriptEnvironment(runsInWidget = false, source = RunSource.ShareSheet)))
        assertEquals("runsInNotification", out(flags, ScriptEnvironment(runsInWidget = false, source = RunSource.Notification)))
        assertEquals("runsFromHomeScreen", out(flags, ScriptEnvironment(runsInWidget = false, source = RunSource.HomeScreen)))
        assertEquals("runsFromAutomation", out(flags, ScriptEnvironment(runsInWidget = false, source = RunSource.Automation)))
    }

    @Test
    fun `shared texts, urls, files and images are in args`() = runTest {
        val photo = "${files.temporaryDirectory}/share/photo.png".also { files.writeBytes(it, FakeImageStore.PNG_BYTES) }
        val notes = "${files.temporaryDirectory}/share/notes.txt".also { files.writeString(it, "hello") }
        val env = ScriptEnvironment(
            runsInWidget = false,
            source = RunSource.ShareSheet,
            shareInput = ShareInput(
                plainTexts = listOf("오늘 할 일"),
                urls = listOf("https://example.com/a"),
                fileURLs = listOf(notes),
                images = listOf(photo),
            ),
        )

        val log = out(
            """
            console.log(JSON.stringify(args.plainTexts));
            console.log(JSON.stringify(args.urls));
            console.log(FileManager.local().readString(args.fileURLs[0]));
            console.log(args.images[0] instanceof Image, args.images.length);
            console.log(args.all.length, args.all[0], args.all[3] instanceof Image);
            """,
            env,
        )

        assertEquals(
            listOf("[\"오늘 할 일\"]", "[\"https://example.com/a\"]", "hello", "true 1", "4 오늘 할 일 true"),
            log.lines(),
        )
    }

    @Test
    fun `args are empty arrays when nothing was shared`() = runTest {
        assertEquals(
            "0 0 0 0 0 null null",
            out(
                "console.log(args.plainTexts.length, args.urls.length, args.fileURLs.length, args.images.length, args.all.length, args.notification, args.shortcutParameter)",
                ScriptEnvironment(runsInWidget = false),
            ),
        )
    }

    @Test
    fun `a tapped notification is passed as args notification`() = runTest {
        val env = ScriptEnvironment(
            runsInWidget = false,
            source = RunSource.Notification,
            notification = NotificationPayload(
                identifier = "rain", title = "비 소식", subtitle = "서울", body = "오후 3시",
                threadIdentifier = "weather", userInfo = """{"city":"Seoul","level":2}""", deliveredAt = 1_000L,
            ),
        )

        val log = out(
            """
            const n = args.notification;
            console.log(n instanceof Notification, n.identifier, n.title, n.subtitle, n.body, n.threadIdentifier);
            console.log(n.userInfo.city, n.userInfo.level, n.deliveryDate.getTime());
            """,
            env,
        )

        assertEquals(listOf("true rain 비 소식 서울 오후 3시 weather", "Seoul 2 1000"), log.lines())
    }

    @Test
    fun `automation apps pass a shortcut parameter`() = runTest {
        assertEquals(
            "turn on",
            out("console.log(args.shortcutParameter)", ScriptEnvironment(runsInWidget = false, source = RunSource.Automation, shortcutParameter = "turn on")),
        )
    }

    @Test
    fun `an event trigger tells the script which event started it`() = runTest {
        assertEquals(
            "calendar true false",
            out(
                "console.log(args.trigger, config.runsFromTrigger, config.runsInApp)",
                ScriptEnvironment(runsInWidget = false, source = RunSource.Trigger, trigger = "calendar"),
            ),
        )
        assertEquals("null false", out("console.log(args.trigger, config.runsFromTrigger)", ScriptEnvironment(runsInWidget = false)))
    }

    @Test
    fun `notifications remember which script to run, user info and actions`() = runTest {
        out(
            """
            const n = new Notification();
            n.identifier = "a";
            n.userInfo = { city: "Seoul" };
            n.addAction("지도 열기", "https://maps.example");
            await n.schedule();

            const other = new Notification();
            other.identifier = "b";
            other.scriptName = "Weather";
            await other.schedule();
            """,
            ScriptEnvironment(runsInWidget = false),
            name = "Rain Alert",
        )

        val (a, b) = notifier.scheduled
        assertEquals("Rain Alert", a.scriptName)
        assertEquals("""{"city":"Seoul"}""", a.userInfo)
        assertEquals(listOf(NotificationAction("지도 열기", "https://maps.example")), a.actions)
        assertEquals("Weather", b.scriptName)
        assertNull(b.userInfo)
    }

    @Test
    fun `setShortcutOutput hands a value back to the automation app`() = runTest {
        val result = QuickJsScriptEngine(Prelude.load(), fakeServices(files = files), timeoutMillis = 2_000)
            .execute("""Script.setShortcutOutput({ temp: 21, unit: "C" });""", ScriptEnvironment(runsInWidget = false, source = RunSource.Automation), "a")

        assertEquals("""{"temp":21,"unit":"C"}""", result.shortcutOutput)
        val plain = QuickJsScriptEngine(Prelude.load(), fakeServices(files = files), timeoutMillis = 2_000)
            .execute("""Script.setShortcutOutput("done");""", ScriptEnvironment(runsInWidget = false), "a")
        assertEquals("\"done\"", plain.shortcutOutput)
    }
}
