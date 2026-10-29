package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.data.gallery.BuiltinCatalogSource
import kr.b1ink.scriptwidget.data.personal.FileReminderStore
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetJson
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Every gallery sample runs offline (faked services) in every widget size and shows real content. */
class GallerySamplesTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val catalog = BuiltinCatalogSource()
    private val files by lazy { SandboxedFileSystem(tmp.newFolder("sandbox")) }
    private val calendar = FakeCalendarStore()
    private val reminders by lazy { FileReminderStore(tmp.root.resolve("reminders.json"), FakeNotifier()) }
    private val contacts = FakeContactStore()
    private val http = FakeHttpGateway().apply {
        responses = { spec ->
            val body = when {
                "upbit" in spec.url -> """[{"market":"KRW-BTC","trade_price":98765432,"signed_change_rate":0.0123},""" +
                    """{"market":"KRW-ETH","trade_price":4321000,"signed_change_rate":-0.021},{"market":"KRW-XRP","trade_price":812,"signed_change_rate":0}]"""
                "rss" in spec.url || spec.url.endsWith(".xml") -> """<?xml version="1.0"?><rss><channel><title>테스트 뉴스</title>""" +
                    """<item><title>첫 번째 기사</title><link>https://n/1</link></item><item><title>두 번째 기사</title><link>https://n/2</link></item></channel></rss>"""
                else -> "{}"
            }
            HttpResponseSpec(spec.url, 200, emptyMap(), "text/plain", body)
        }
    }

    private val now = System.currentTimeMillis()
    private fun todayAt(hour: Int) = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun seed() {
        calendar.events += EventData(identifier = "e1", calendarId = "1", title = "팀 회의", start = todayAt(10), end = todayAt(11))
        calendar.events += EventData(identifier = "e2", calendarId = "2", title = "치과 예약", start = todayAt(15), end = todayAt(16))
        reminders.save(ReminderData(title = "우유 사기"))
        reminders.save(ReminderData(title = "보고서 제출", dueDate = now + 3_600_000))
        val birthday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 3); set(Calendar.YEAR, 1990) }.timeInMillis
        contacts.contacts += ContactData(identifier = "c1", containerId = "local", givenName = "지민", familyName = "김", birthday = birthday)
    }

    private fun engine() = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(http = http, files = files, calendar = calendar, reminders = reminders, contacts = contacts),
        timeoutMillis = 5_000,
    )

    private suspend fun run(id: String, family: WidgetFamily = WidgetFamily.Medium, parameter: String? = null, action: String? = null): WidgetRoot {
        val entry = catalog.catalog().entries.single { it.id == id }
        val result = engine().execute(
            catalog.code(entry),
            ScriptEnvironment(runsInWidget = true, widgetFamily = family, widgetParameter = parameter, widgetAction = action, cacheKey = "w-$id"),
            entry.name,
        )
        assertNull(result.error, "$id/$family: ${result.error}")
        return assertNotNull(result.widget, "$id/$family drew no widget")
    }

    private fun WidgetRoot.json() = WidgetJson.encodeToString(WidgetRoot.serializer(), this)

    // --- catalog ---

    @Test
    fun `the built-in catalog lists the old samples and the new gallery samples`() = runTest {
        val entries = catalog.catalog().entries

        assertEquals(entries.size, entries.map { it.id }.toSet().size, "ids are unique")
        assertTrue(entries.size >= 23, "${entries.size} entries")
        assertTrue(entries.all { kr.b1ink.scriptwidget.domain.model.ScriptName.isValid(it.name) })
        assertTrue(entries.all { it.description.isNotBlank() && it.sizes.isNotEmpty() })
        entries.forEach { assertTrue(catalog.code(it).isNotBlank(), it.id) }
    }

    @Test
    fun `every new sample draws a widget in every size`() = runTest {
        seed()
        val ids = catalog.catalog().entries.map { it.id }.filterNot { it.matches(Regex("""0\d_.*""")) }
        assertEquals(14, ids.size, ids.toString())
        for (id in ids) for (family in WidgetFamily.entries) run(id, family)
    }

    // --- specific samples ---

    @Test
    fun `today's events lists today's events in order`() = runTest {
        seed()
        val json = run("today_events").json()

        assertTrue(json.indexOf("팀 회의") in 0 until json.indexOf("치과 예약"), json)
    }

    @Test
    fun `reminders shows open items and a tap completes one`() = runTest {
        seed()
        val first = reminders.reminders(null).first { it.title == "우유 사기" }
        assertContains(run("reminders").json(), "우유 사기")

        val after = run("reminders", action = "done:${first.identifier}").json()

        assertTrue(reminders.reminders(null).single { it.title == "우유 사기" }.isCompleted)
        assertTrue("우유 사기" !in after, after)
    }

    @Test
    fun `d-day counts the days to the date in the parameter`() = runTest {
        val target = java.time.LocalDate.now().plusDays(10)
        val json = run("dday", WidgetFamily.Small, parameter = "$target 여행").json()

        assertContains(json, "D-10")
        assertContains(json, "여행")
    }

    @Test
    fun `habit tracker counts taps per day`() = runTest {
        run("habit", WidgetFamily.Small, action = "add")
        val json = run("habit", WidgetFamily.Small, action = "add").json()

        assertContains(json, "2")
    }

    @Test
    fun `memo shows the saved note`() = runTest {
        files.writeString("${files.documentsDirectory}/memo/note.txt", "장보기: 계란, 두부")

        assertContains(run("memo").json(), "장보기: 계란, 두부")
    }

    @Test
    fun `birthdays lists upcoming birthdays from contacts`() = runTest {
        seed()
        val json = run("birthdays").json()

        assertContains(json, "김지민")
        assertContains(json, "3일")
    }

    @Test
    fun `crypto and rss read their feeds`() = runTest {
        assertContains(run("crypto").json(), "BTC")
        assertContains(run("rss_news", WidgetFamily.Large).json(), "첫 번째 기사")
    }

    @Test
    fun `photo frame explains how to add a photo when no bookmark exists`() = runTest {
        assertContains(run("photo_frame").json(), "파일 북마크")
    }
}
