package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScriptAppearanceTest {
    private val scriptableHeader = """
        // Variables used by Scriptable.
        // These must be at the very top of the file. Do not edit.
        // icon-color: deep-blue; icon-glyph: magic;
        const w = new ListWidget();
    """.trimIndent()

    @Test
    fun `reads Scriptable's icon header`() {
        assertEquals(ScriptAppearance("deep-blue", "magic"), ScriptAppearance.parse(scriptableHeader))
        assertEquals(ScriptAppearance("red", null), ScriptAppearance.parse("// icon-color: red;\nlog(1)"))
    }

    @Test
    fun `no header means no appearance`() {
        assertNull(ScriptAppearance.parse("const a = 1; // icon-color: red;"))
        assertNull(ScriptAppearance.parse(""))
    }

    @Test
    fun `applying replaces an existing header and keeps the code`() {
        val updated = ScriptAppearance("green", "cloud").applyTo(scriptableHeader)

        assertEquals(
            """
            // Variables used by Scriptable.
            // These must be at the very top of the file. Do not edit.
            // icon-color: green; icon-glyph: cloud;
            const w = new ListWidget();
            """.trimIndent(),
            updated,
        )
        assertEquals(ScriptAppearance("green", "cloud"), ScriptAppearance.parse(updated))
    }

    @Test
    fun `applying to code without a header adds Scriptable's header`() {
        val updated = ScriptAppearance("orange", "sun").applyTo("log(1)\n")

        assertEquals(
            "// Variables used by Scriptable.\n// These must be at the very top of the file. Do not edit.\n// icon-color: orange; icon-glyph: sun;\nlog(1)\n",
            updated,
        )
    }
}
