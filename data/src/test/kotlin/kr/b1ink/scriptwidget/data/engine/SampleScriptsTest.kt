package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.data.repository.ClasspathSampleScriptSource
import kr.b1ink.scriptwidget.domain.model.Background
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.background
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Bundled samples run offline against faked services. */
class SampleScriptsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val samples = ClasspathSampleScriptSource().load()
    private val http = FakeHttpGateway().apply {
        responses = {
            HttpResponseSpec(
                it.url, 200, emptyMap(), "application/json",
                """{"current":{"temperature_2m":21.37,"weather_code":2,"time":"2026-10-28T15:00"}}""",
            )
        }
    }
    private val files by lazy { SandboxedFileSystem(tmp.newFolder()) }

    private suspend fun runWeather(location: FakeLocationProvider = FakeLocationProvider()) = QuickJsScriptEngine(
        Prelude.load(), fakeServices(http = http, files = files, location = location), timeoutMillis = 5_000,
    ).execute(samples.getValue("06_weather"), ScriptEnvironment(runsInWidget = true, widgetFamily = WidgetFamily.Small), "06_weather")

    @Test
    fun `weather sample uses location, caches the response and asks for a refresh later`() = runTest {
        val result = runWeather()

        assertNull(result.error, result.error)
        val root = assertNotNull(result.widget)
        val texts = root.children.flatMapTexts()
        assertTrue("21°" in texts, texts.toString())
        assertTrue(texts.any { "Partly cloudy" in it || "구름" in it }, texts.toString())
        assertTrue(root.background is Background.Linear)
        assertNotNull(root.refreshAfter)
        assertContains(http.requests.single().url, "latitude=37.5665")
        assertNotNull(files.readString("${files.documentsDirectory}/weather/last.json"))
    }

    @Test
    fun `weather sample falls back to the cached response when offline`() = runTest {
        runWeather()
        http.responses = { throw java.io.IOException("offline") }

        val result = runWeather()

        assertNull(result.error, result.error)
        assertTrue("21°" in result.widget!!.children.flatMapTexts())
    }

    @Test
    fun `weather sample uses Seoul when location is unavailable`() = runTest {
        val result = runWeather(FakeLocationProvider(location = null))

        assertNull(result.error, result.error)
        assertContains(http.requests.single().url, "latitude=37.5665")
    }

    @Test
    fun `showcase sample uses symbols, drawing, device info and image options`() = runTest {
        val symbols = FakeSymbolRenderer()
        val canvas = FakeCanvasRenderer()
        val result = QuickJsScriptEngine(
            Prelude.load(),
            fakeServices(files = files, symbols = symbols, canvas = canvas),
            timeoutMillis = 5_000,
        ).execute(samples.getValue("07_showcase"), ScriptEnvironment(runsInWidget = true, widgetFamily = WidgetFamily.Medium), "07_showcase")

        assertNull(result.error, result.error)
        assertTrue(symbols.calls.isNotEmpty())
        // The battery percentage is drawn into the ring image.
        assertTrue(canvas.specs.single().ops.any { it is CanvasOp.Text && it.text == "80%" })
        val texts = result.widget!!.children.flatMapTexts()
        assertTrue(texts.any { "Pixel 10 Pro" in it }, texts.toString())
    }

    @Test
    fun `settings sample configures itself with Alert and UITable in the app and renders the choice in the widget`() = runTest {
        val ui = FakeUiPresenter().apply {
            alertAnswers += AlertResult(index = 0, texts = listOf("Busan"))      // main menu: "도시 변경" → text field
            tableEvents += TableEvent.Selected(row = 2)                          // pick the 3rd theme row
        }
        val services = fakeServices(files = files, ui = ui)
        val sample = samples.getValue("08_settings_ui")

        val app = QuickJsScriptEngine(Prelude.load(), services, timeoutMillis = 5_000)
            .execute(sample, ScriptEnvironment(runsInWidget = false), "08_settings_ui")
        assertNull(app.error, app.error)
        assertEquals("Seoul", ui.alerts.single().textFields.single().text)
        assertTrue(ui.tables.single().rows.size >= 3)

        val widget = QuickJsScriptEngine(Prelude.load(), services, timeoutMillis = 5_000)
            .execute(sample, ScriptEnvironment(runsInWidget = true), "08_settings_ui")
        assertNull(widget.error, widget.error)
        val texts = widget.widget!!.children.flatMapTexts()
        assertTrue(texts.any { "Busan" in it }, texts.toString())
    }

    @Test
    fun `counter sample keeps its count across taps`() = runTest {
        val engine = QuickJsScriptEngine(Prelude.load(), fakeServices(files = files), timeoutMillis = 5_000)
        suspend fun tap(action: String?) = engine.execute(
            samples.getValue("09_counter"), ScriptEnvironment(runsInWidget = true, widgetAction = action), "09_counter",
        ).also { assertNull(it.error, it.error) }

        tap(null)
        tap("increment")
        tap("increment")
        val afterTwo = tap("increment").widget!!.children.flatMapTexts()
        assertTrue("3" in afterTwo, afterTwo.toString())

        val afterDecrement = tap("decrement").widget!!.children.flatMapTexts()
        assertTrue("2" in afterDecrement, afterDecrement.toString())

        val reset = tap("reset")
        assertTrue("0" in reset.widget!!.children.flatMapTexts())
        val actions = reset.widget!!.children.flatMapActions()
        assertEquals(setOf("decrement", "increment", "reset"), actions.toSet())
    }

    private fun List<kr.b1ink.scriptwidget.domain.model.WidgetNode>.flatMapActions(): List<String> = flatMap {
        when (it) {
            is kr.b1ink.scriptwidget.domain.model.StackNode -> listOfNotNull(it.action) + it.children.flatMapActions()
            is TextNode -> listOfNotNull(it.action)
            else -> emptyList()
        }
    }

    private fun List<kr.b1ink.scriptwidget.domain.model.WidgetNode>.flatMapTexts(): List<String> = flatMap {
        when (it) {
            is TextNode -> listOf(it.text)
            is kr.b1ink.scriptwidget.domain.model.StackNode -> it.children.flatMapTexts()
            else -> emptyList()
        }
    }
}
