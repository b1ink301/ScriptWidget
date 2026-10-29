package kr.b1ink.scriptwidget.ui

import kr.b1ink.scriptwidget.domain.model.ScriptAppearance
import kr.b1ink.scriptwidget.ui.list.ScriptIcons
import kotlin.test.Test
import kotlin.test.assertEquals

class ScriptIconsTest {
    @Test
    fun `known glyphs and colors`() {
        assertEquals("☁️", ScriptIcons.symbol(ScriptAppearance("blue", "cloud"), "Weather"))
        assertEquals(0xFF0A84FFL, ScriptIcons.color(ScriptAppearance("blue", "cloud")))
    }

    @Test
    fun `unknown glyphs fall back to the script's initial and unknown colors to gray`() {
        assertEquals("W", ScriptIcons.symbol(ScriptAppearance("blue", "no-such-glyph"), "weather"))
        assertEquals("W", ScriptIcons.symbol(null, "weather"))
        assertEquals(ScriptIcons.color(ScriptAppearance("gray")), ScriptIcons.color(ScriptAppearance("not-a-color")))
        assertEquals(ScriptIcons.color(ScriptAppearance("gray")), ScriptIcons.color(null))
    }

    @Test
    fun `the initial skips leading digits and symbols`() {
        assertEquals("H", ScriptIcons.symbol(null, "01_hello"))
        assertEquals("날", ScriptIcons.symbol(null, "2026 날씨"))
        assertEquals("7", ScriptIcons.symbol(null, "777"))
    }

    @Test
    fun `common Scriptable glyphs are known`() {
        listOf("credit-card", "wallet", "gift", "graduation-cap", "film", "tv", "rss", "subway", "bicycle", "dumbbell", "pills", "paw", "sync", "list-ul", "user")
            .forEach { kotlin.test.assertTrue(it in ScriptIcons.GLYPHS, it) }
    }
}
