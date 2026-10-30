package kr.b1ink.scriptwidget.data.design

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.EventData
import kr.b1ink.scriptwidget.data.engine.FakeCalendarStore
import kr.b1ink.scriptwidget.data.engine.FakeHttpGateway
import kr.b1ink.scriptwidget.data.engine.HttpResponseSpec
import kr.b1ink.scriptwidget.data.engine.Prelude
import kr.b1ink.scriptwidget.data.engine.QuickJsScriptEngine
import kr.b1ink.scriptwidget.data.engine.ReminderData
import kr.b1ink.scriptwidget.data.engine.fakeServices
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.data.personal.FileReminderStore
import kr.b1ink.scriptwidget.data.engine.FakeNotifier
import kr.b1ink.scriptwidget.domain.model.DataBinding
import kr.b1ink.scriptwidget.domain.model.DateNode
import kr.b1ink.scriptwidget.domain.model.DesignBackground
import kr.b1ink.scriptwidget.domain.model.DesignDate
import kr.b1ink.scriptwidget.domain.model.DesignElement
import kr.b1ink.scriptwidget.domain.model.DesignImage
import kr.b1ink.scriptwidget.domain.model.DesignSpacer
import kr.b1ink.scriptwidget.domain.model.DesignStack
import kr.b1ink.scriptwidget.domain.model.DesignSymbol
import kr.b1ink.scriptwidget.domain.model.DesignTemplates
import kr.b1ink.scriptwidget.domain.model.DesignText
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetDesign
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Generated scripts run in the real QuickJS engine and draw exactly what was designed. */
class JsDesignScriptGeneratorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val generator = JsDesignScriptGenerator()
    private val calendar = FakeCalendarStore()
    private val reminders by lazy { FileReminderStore(tmp.root.resolve("reminders.json"), FakeNotifier()) }
    private val http = FakeHttpGateway().apply {
        responses = { spec ->
            when {
                "open-meteo" in spec.url -> ok("""{"current":{"temperature_2m":21.6,"weather_code":61}}""")
                spec.url == "https://api.example.com/data.json" -> ok("""{"data":[{"name":"첫째","n":3}]}""")
                else -> HttpResponseSpec(spec.url, 404, emptyMap(), "text/plain", "missing")
            }
        }
    }

    /** The fake services run scripts in Asia/Seoul. */
    private val SEOUL = java.util.TimeZone.getTimeZone("Asia/Seoul")

    private fun ok(body: String) = HttpResponseSpec("u", 200, emptyMap(), "application/json", body)

    private suspend fun run(
        design: WidgetDesign,
        family: WidgetFamily = WidgetFamily.Medium,
        parameter: String? = null,
        files: java.io.File = tmp.newFolder(),
    ): WidgetRoot {
        val engine = QuickJsScriptEngine(
            Prelude.load(),
            fakeServices(http = http, files = SandboxedFileSystem(files), calendar = calendar, reminders = reminders),
            timeoutMillis = 5_000,
        )
        val result = engine.execute(
            generator.generate(design),
            ScriptEnvironment(runsInWidget = true, widgetFamily = family, widgetParameter = parameter, cacheKey = "design"),
            "design",
        )
        assertNull(result.error, result.error)
        return assertNotNull(result.widget)
    }

    private fun WidgetRoot.text(vararg path: Int): String = assertIs<TextNode>(nodeAt(path.toList())).text

    private fun WidgetRoot.nodeAt(path: List<Int>): WidgetNode? {
        var list: List<WidgetNode> = children
        var node: WidgetNode? = null
        for (i in path) {
            node = list.getOrNull(i) ?: return null
            list = (node as? StackNode)?.children ?: emptyList()
        }
        return node
    }

    @Test
    fun `layout and styles become the widget tree`() = runTest {
        val design = WidgetDesign(
            background = DesignBackground(gradient = listOf("#FF112233", "#80445566")),
            padding = 12f,
            spacing = 4f,
            url = "https://example.com",
            refreshMinutes = 30,
            children = listOf(
                DesignText("제목", color = "#FFFF0000", size = 22f, weight = "bold", align = "center", lineLimit = 1, minimumScaleFactor = 0.5f),
                DesignStack(
                    dir = "h", align = "top", spacing = 6f, padding = 8f, bg = "#FF222222", cornerRadius = 10f,
                    children = listOf(DesignSymbol("star.fill", color = "#FFFFCC00", size = 20f), DesignText("별")),
                ),
                DesignSpacer(),
                DesignDate(style = "time", size = 30f, weight = "semibold"),
                DesignSpacer(length = 5f),
            ),
        )
        val before = System.currentTimeMillis()

        val root = run(design)

        assertEquals(listOf("#FF112233", "#80445566"), root.gradient?.colors)
        assertEquals(listOf(12f, 12f, 12f, 12f), root.padding)
        assertEquals(4f, root.spacing)
        assertEquals("https://example.com", root.url)
        assertTrue(root.refreshAfter!! >= before + 30 * 60_000 - 1_000)
        val title = assertIs<TextNode>(root.children[0])
        assertEquals(TextNode("제목", color = "#FFFF0000", size = 22f, weight = "bold", align = "center", lineLimit = 1, minimumScaleFactor = 0.5f), title)
        val stack = assertIs<StackNode>(root.children[1])
        assertEquals(listOf("h", "top", "#FF222222"), listOf(stack.dir, stack.align, stack.bg))
        assertEquals(listOf(6f, 10f), listOf(stack.spacing, stack.cornerRadius))
        assertEquals(listOf(8f, 8f, 8f, 8f), stack.padding)
        val symbol = assertIs<ImageNode>(stack.children[0])
        assertEquals(listOf(20f, 20f), symbol.size)
        assertEquals("#FFFFCC00", symbol.tint)
        assertEquals("별", root.text(1, 1))
        assertEquals(SpacerNode(null), root.children[2])
        val date = assertIs<DateNode>(root.children[3])
        assertEquals(listOf("time", "semibold"), listOf(date.style, date.weight))
        assertEquals(SpacerNode(5f), root.children[4])
    }

    @Test
    fun `text is kept exactly, quotes and all`() = runTest {
        val tricky = "따옴표 \" ' ` \\ \${not} {value} </script>\n둘째 줄"

        assertEquals(tricky, run(WidgetDesign(children = listOf(DesignText(tricky)))).text(0))
    }

    @Test
    fun `data blocks fill in live values`() = runTest {
        val start = Calendar.getInstance(SEOUL).apply { set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 0) }.timeInMillis
        calendar.events += EventData(identifier = "e", calendarId = "1", title = "늦은 회의", start = start, end = start + 50 * 60_000)
        reminders.save(ReminderData(title = "우유 사기"))
        reminders.save(ReminderData(title = "보고서", dueDate = System.currentTimeMillis() + 3_600_000))
        val design = WidgetDesign(
            children = listOf(
                DesignText("{value}%", data = DataBinding.Battery),
                DesignText("{value}", data = DataBinding.DateText("yyyy")),
                DesignText("{value}", data = DataBinding.Parameter("기본값")),
                DesignText("{value}", data = DataBinding.Json("https://api.example.com/data.json", "data.0.name")),
                DesignText("{value}°", data = DataBinding.Weather(DataBinding.Weather.Field.Temperature)),
                DesignText("{value}", data = DataBinding.Weather(DataBinding.Weather.Field.Condition)),
                DesignText("{value}", data = DataBinding.NextEvent(DataBinding.NextEvent.Field.Title)),
                DesignText("{value}", data = DataBinding.NextEvent(DataBinding.NextEvent.Field.Time)),
                DesignText("{value}개", data = DataBinding.Reminders(DataBinding.Reminders.Field.Count)),
                DesignText("{value}", data = DataBinding.Reminders(DataBinding.Reminders.Field.Next)),
            ),
        )

        val root = run(design)

        assertEquals("80%", root.text(0))
        assertEquals(Calendar.getInstance(SEOUL).get(Calendar.YEAR).toString(), root.text(1))
        assertEquals("기본값", root.text(2))
        assertEquals("첫째", root.text(3))
        assertEquals("22°", root.text(4))
        assertEquals("비", root.text(5))
        assertEquals("늦은 회의", root.text(6))
        assertEquals("23:00 – 23:50", root.text(7))
        assertEquals("2개", root.text(8))
        assertEquals("보고서", root.text(9))
        assertEquals("홈 화면에서 지정", run(design, parameter = "홈 화면에서 지정").text(2))
    }

    @Test
    fun `a failing data source shows a dash instead of breaking the widget`() = runTest {
        val design = WidgetDesign(children = listOf(DesignText("값: {value}", data = DataBinding.Json("https://api.example.com/missing.json", "a"))))

        assertEquals("값: –", run(design).text(0))
    }

    @Test
    fun `images that fail to load keep their place`() = runTest {
        http.bytes = { throw java.io.IOException("offline") }
        val root = run(WidgetDesign(children = listOf(DesignImage("https://example.com/a.png", size = 40f, cornerRadius = 8f), DesignText("뒤"))))

        assertIs<ImageNode>(root.children[0])
        assertEquals("뒤", root.text(1))
    }

    @Test
    fun `bookmarked pictures fill images and the background, and a missing one leaves a placeholder`() = runTest {
        val files = tmp.newFolder()
        val png = java.io.ByteArrayOutputStream().also {
            javax.imageio.ImageIO.write(java.awt.image.BufferedImage(7, 5, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", it)
        }.toByteArray()
        files.resolve("${SandboxedFileSystem.BOOKMARKS}/photo").apply { parentFile.mkdirs() }.writeBytes(png)
        val design = WidgetDesign(
            background = DesignBackground(color = "#FF000000", image = "photo"),
            children = listOf(DesignImage(bookmark = "photo", size = 50f), DesignImage(bookmark = "gone"), DesignText("뒤")),
        )

        val root = run(design, files = files)

        assertNotNull(root.backgroundImage)
        val picture = assertIs<ImageNode>(root.children[0])
        assertEquals(listOf(50f, 50f), picture.size)
        val placeholder = assertIs<ImageNode>(root.children[1])
        assertTrue(picture.src != placeholder.src, "the bookmarked picture, not the placeholder")
        assertEquals("뒤", root.text(2))
        assertNull(run(design.copy(background = DesignBackground(image = "gone"))).backgroundImage)
    }

    @Test
    fun `d-day counts whole days in the device time zone`() = runTest {
        val today = java.time.LocalDate.now(SEOUL.toZoneId())
        fun d(days: Long) = DataBinding.Countdown(today.plusDays(days).toString())
        val lastYear = today.minusDays(1)
        val nextTime = lastYear.plusYears(1)
        val design = WidgetDesign(
            children = listOf(
                DesignText("{value}", data = d(10)),
                DesignText("{value}", data = d(0)),
                DesignText("{value}", data = d(-3)),
                DesignText("{value}", data = DataBinding.Countdown(lastYear.toString(), yearly = true)),
                DesignText("{value}", data = DataBinding.Countdown("")),
                DesignText("{value}", data = DataBinding.Countdown("", field = DataBinding.Countdown.Field.Name)),
            ),
        )

        val plain = run(design)
        assertEquals(listOf("D-10", "D-DAY", "D+3"), listOf(plain.text(0), plain.text(1), plain.text(2)))
        assertEquals("D-${java.time.temporal.ChronoUnit.DAYS.between(today, nextTime)}", plain.text(3))
        assertEquals("–", plain.text(4))

        val fromParameter = run(design, parameter = "${today.plusDays(5)} 크리스마스 파티")
        assertEquals("D-5", fromParameter.text(4))
        assertEquals("크리스마스 파티", fromParameter.text(5))
    }

    @Test
    fun `progress and world time are worked out on the device`() = runTest {
        val design = WidgetDesign(
            children = listOf(
                DesignText("{value}%", data = DataBinding.Progress(DataBinding.Progress.Period.Year)),
                DesignText("{value}%", data = DataBinding.Progress(DataBinding.Progress.Period.Month)),
                DesignText("{value}", data = DataBinding.WorldTime("UTC")),
            ),
        )
        val now = java.time.ZonedDateTime.now(SEOUL.toZoneId())
        val year = (now.dayOfYear - 1 + now.toLocalTime().toSecondOfDay() / 86400.0) / now.toLocalDate().lengthOfYear() * 100
        val month = (now.dayOfMonth - 1 + now.toLocalTime().toSecondOfDay() / 86400.0) / now.toLocalDate().lengthOfMonth() * 100

        val root = run(design)

        assertEquals("${year.toInt()}%", root.text(0))
        assertEquals("${month.toInt()}%", root.text(1))
        val utc = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
        val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        assertTrue(root.text(2) in setOf(utc.minusMinutes(1).format(fmt), utc.format(fmt)), root.text(2))
    }

    @Test
    fun `texts use the chosen font, a font file when it is installed`() = runTest {
        val files = tmp.newFolder()
        files.resolve("Documents/fonts").mkdirs()
        files.resolve("Documents/fonts/Pixel.ttf").writeBytes(byteArrayOf(0, 1, 0, 0))
        val design = WidgetDesign(
            children = listOf(
                DesignText("a", font = "serif", weight = "bold", size = 20f),
                DesignText("b", font = "monospace"),
                DesignText("c", font = "Pixel"),
                DesignText("d"),
            ),
        )

        val root = run(design, files = files)

        val texts = root.children.map { it as TextNode }
        assertEquals(listOf("serif", "monospace", "Pixel", "system"), texts.map { it.font })
        assertEquals(listOf("bold", 20f), listOf(texts[0].weight, texts[0].size))
        assertEquals(files.resolve("Documents/fonts/Pixel.ttf").canonicalPath, java.io.File(texts[2].fontFile!!).canonicalPath)
        assertNull(texts[0].fontFile)
    }

    @Test
    fun `every template draws in every size, one node per element`() = runTest {
        for (template in DesignTemplates.all) for (family in WidgetFamily.entries) {
            val root = run(template.design, family)
            fun check(elements: List<DesignElement>, path: List<Int>) {
                elements.forEachIndexed { i, e ->
                    val node = assertNotNull(root.nodeAt(path + i), "${template.title} $family ${path + i}")
                    if (e is DesignStack) check(e.children, path + i)
                }
            }
            check(template.design.children, emptyList())
            assertEquals(template.design.children.size, root.children.size, template.title)
        }
    }

    @Test
    fun `fallback texts and weather words follow the chosen language`() = runTest {
        val english = JsDesignScriptGenerator(GeneratorTexts.ENGLISH)
        val design = WidgetDesign(
            children = listOf(
                DesignText("{value}", data = DataBinding.NextEvent(DataBinding.NextEvent.Field.Title)),
                DesignText("{value}", data = DataBinding.Weather(DataBinding.Weather.Field.Condition)),
            ),
        )
        val engine = QuickJsScriptEngine(
            Prelude.load(),
            fakeServices(http = http, files = SandboxedFileSystem(tmp.newFolder()), calendar = calendar, reminders = reminders),
            timeoutMillis = 5_000,
        )
        val root = assertNotNull(engine.execute(english.generate(design), ScriptEnvironment(runsInWidget = true), "d").widget)

        assertEquals("No events", root.text(0))
        assertEquals("Rain", root.text(1))
        assertTrue("디자이너" !in english.generate(design))
    }
}
