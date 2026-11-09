package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonWidgetTest {
    private fun fields(json: String) = JsonWidget.fields(Json.parseToJsonElement(json))

    @Test
    fun `lists every value with its path and a preview`() {
        val fields = fields("""{"name":"Seoul","main":{"temp":21.5,"humidity":40},"ok":true,"note":null}""")

        assertEquals(
            listOf(
                JsonField("name", "Seoul"),
                JsonField("main.temp", "21.5"),
                JsonField("main.humidity", "40"),
                JsonField("ok", "true"),
                JsonField("note", "null"),
            ),
            fields,
        )
    }

    @Test
    fun `arrays show their first few items by index`() {
        val fields = fields("""{"items":[{"t":"a"},{"t":"b"},{"t":"c"},{"t":"d"},{"t":"e"}]}""")

        assertEquals(listOf("items.0.t", "items.1.t", "items.2.t"), fields.map { it.path })
    }

    @Test
    fun `a top-level array works too`() {
        assertEquals(listOf(JsonField("0.price", "3"), JsonField("1.price", "4")), fields("""[{"price":3},{"price":4}]"""))
    }

    @Test
    fun `long values are shortened in the preview`() {
        val preview = fields("""{"text":"${"x".repeat(200)}"}""").single().preview

        assertTrue(preview.length <= JsonWidget.MAX_PREVIEW + 1)
        assertTrue(preview.endsWith("…"))
    }

    @Test
    fun `huge responses are capped`() {
        val big = (1..1000).joinToString(",", "{", "}") { "\"k$it\":$it" }

        assertEquals(JsonWidget.MAX_FIELDS, fields(big).size)
    }

    @Test
    fun `labels are readable names from the path`() {
        assertEquals("Stargazers count", JsonWidget.label("stargazers_count"))
        assertEquals("Temp", JsonWidget.label("main.temp"))
        assertEquals("Wind speed", JsonWidget.label("current.windSpeed"))
        assertEquals("Name", JsonWidget.label("data.0.name"))
        assertEquals("Value", JsonWidget.label("0"))
    }

    @Test
    fun `one field becomes a big number under the title`() {
        val design = JsonWidget.design("ScriptWidget", "https://api.github.com/repos/a/b", listOf(JsonField("stargazers_count", "12")))

        val (title, value) = design.children
        assertEquals("ScriptWidget", (title as DesignText).text)
        value as DesignText
        assertEquals(DataBinding.Json("https://api.github.com/repos/a/b", "stargazers_count"), value.data)
        assertEquals("bold", value.weight)
        assertTrue(value.size >= 28f)
        assertEquals(JsonWidget.REFRESH_MINUTES, design.refreshMinutes)
    }

    @Test
    fun `several fields become label and value rows`() {
        val url = "https://api.example.com/weather"
        val design = JsonWidget.design("Weather", url, listOf(JsonField("main.temp", "21.5"), JsonField("main.humidity", "40")))

        val rows = design.children.drop(1).map { it as DesignStack }
        assertEquals(2, rows.size)
        val (label, spacer, value) = rows[0].children
        assertEquals("Temp", (label as DesignText).text)
        assertEquals(DesignSpacer(null), spacer)
        assertEquals(DataBinding.Json(url, "main.temp"), (value as DesignText).data)
        assertEquals(1, value.lineLimit)
    }

    @Test
    fun `the title defaults to the address's host`() {
        assertEquals("api.example.com", JsonWidget.defaultTitle("https://api.example.com/v1/x?y=1"))
        assertEquals("", JsonWidget.defaultTitle("not a url"))
    }
}
