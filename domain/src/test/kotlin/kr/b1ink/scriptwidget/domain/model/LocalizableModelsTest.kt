package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Things the domain hands to the UI as codes, so the app can show them in the user's language. */
class LocalizableModelsTest {
    @Test
    fun `widget errors travel as codes in the render state`() {
        assertEquals(WidgetError.NotBound, WidgetError.decode(WidgetError.encode(WidgetError.NotBound)))
        assertEquals(WidgetError.ScriptNotFound("오늘 일정"), WidgetError.decode(WidgetError.encode(WidgetError.ScriptNotFound("오늘 일정"))))
        assertNull(WidgetError.decode("TypeError: x is undefined"))
        assertNull(WidgetError.decode(null))
    }

    @Test
    fun `bookmark name problems are kinds, not sentences`() {
        assertEquals(BookmarkName.Problem.Blank, BookmarkName.problem("  "))
        assertEquals(BookmarkName.Problem.NotAFileName, BookmarkName.problem("a/b"))
        assertNull(BookmarkName.problem("wallpaper"))
    }

    @Test
    fun `catalog entries pick their translation for a language, falling back to the original`() {
        val entry = CatalogEntry(
            id = "events", name = "오늘 일정", title = "오늘 일정", category = "일정·할 일", description = "오늘 남은 일정",
            script = "events.js",
            translations = mapOf("en" to CatalogText(title = "Today's Events", description = "Events left today", category = "Calendar & To-dos")),
        )

        assertEquals(CatalogText("Today's Events", "Events left today", "Calendar & To-dos"), entry.textFor("en"))
        assertEquals(CatalogText("오늘 일정", "오늘 남은 일정", "일정·할 일"), entry.textFor("ko"))
        assertEquals(CatalogText("오늘 일정", "오늘 남은 일정", "일정·할 일"), entry.textFor("fr"))
        assertEquals("오늘 일정", entry.name, "the installed script keeps its name")
    }

    @Test
    fun `an entry can ship its script in other languages`() {
        val entry = CatalogEntry(id = "clock", name = "디자인 시계", script = "designs/clock.js", localizedScripts = mapOf("en" to "designs/clock.en.js"))

        assertEquals("designs/clock.en.js", entry.scriptFor("en"))
        assertEquals("designs/clock.js", entry.scriptFor("ko"))
        assertEquals("designs/clock.js", entry.scriptFor(null))
        assertEquals(listOf("designs/clock.js", "designs/clock.en.js"), entry.allScripts())
        assertEquals(true, CatalogEntry(id = "a", name = "a", script = "a.js").seed, "samples are installed on first launch unless they opt out")
    }

    @Test
    fun `catalog titles can be translated too`() {
        val catalog = Catalog("기본 갤러리", emptyList(), titles = mapOf("en" to "Built-in gallery"))

        assertEquals("Built-in gallery", catalog.titleFor("en"))
        assertEquals("기본 갤러리", catalog.titleFor("ko"))
    }
}
