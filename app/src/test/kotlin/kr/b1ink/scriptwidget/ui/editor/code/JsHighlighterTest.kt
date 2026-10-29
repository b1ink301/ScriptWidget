package kr.b1ink.scriptwidget.ui.editor.code

import kr.b1ink.scriptwidget.ui.editor.code.JsHighlighter.Kind
import kotlin.test.Test
import kotlin.test.assertEquals

class JsHighlighterTest {
    private val highlighter = JsHighlighter(apiNames = setOf("ListWidget", "Font"))

    private fun spans(code: String) = highlighter.spans(code).map { code.substring(it.start, it.end) to it.kind }

    @Test
    fun `keywords, api names, strings, numbers and comments`() {
        val code = """const w = new ListWidget(); // hi
w.font = Font.boldSystemFont(12.5);
let s = "a // not comment"; /* block
comment */ x = `t${'$'}{1}`"""

        assertEquals(
            listOf(
                "const" to Kind.Keyword, "new" to Kind.Keyword, "ListWidget" to Kind.Api, "// hi" to Kind.Comment,
                "Font" to Kind.Api, "12.5" to Kind.Number,
                "let" to Kind.Keyword, "\"a // not comment\"" to Kind.String, "/* block\ncomment */" to Kind.Comment,
                "`t${'$'}{1}`" to Kind.String,
            ),
            spans(code),
        )
    }

    @Test
    fun `identifiers that merely contain keywords are not keywords`() {
        assertEquals(emptyList(), spans("constant = newValue + iffy"))
    }

    @Test
    fun `unterminated strings and comments run to the end without crashing`() {
        assertEquals(listOf("\"abc" to Kind.String), spans("\"abc"))
        assertEquals(listOf("/* open" to Kind.Comment), spans("/* open"))
        assertEquals(listOf("'x\\'y'" to Kind.String), spans("'x\\'y'"))
    }
}
