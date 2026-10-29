package kr.b1ink.scriptwidget.ui.editor.code

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TextSearchTest {
    private val text = "Font a; font b; FONT c"

    @Test
    fun `finds every match, ignoring case by default`() {
        assertEquals(listOf(0, 8, 16), TextSearch.matches(text, "font").map { it.first })
        assertEquals(listOf(8), TextSearch.matches(text, "font", ignoreCase = false).map { it.first })
        assertEquals(emptyList(), TextSearch.matches(text, ""))
    }

    @Test
    fun `next and previous wrap around`() {
        assertEquals(8..11, TextSearch.next(text, "font", from = 1))
        assertEquals(0..3, TextSearch.next(text, "font", from = 17))
        assertEquals(8..11, TextSearch.previous(text, "font", before = 16))
        assertEquals(16..19, TextSearch.previous(text, "font", before = 0))
        assertNull(TextSearch.next(text, "zzz", from = 0))
    }

    @Test
    fun `replace all keeps everything else`() {
        assertEquals("Size a; Size b; Size c" to 3, TextSearch.replaceAll(text, "font", "Size"))
        assertEquals(text to 0, TextSearch.replaceAll(text, "", "x"))
    }
}
