package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DesignHeaderTest {
    private val design = WidgetDesign(children = listOf(DesignText("안녕")))
    private val body = "const w = new ListWidget();\nw.addText(\"안녕\");\nScript.setWidget(w);\nScript.complete();\n"

    @Test
    fun `a designed script carries its design in a header comment`() {
        val code = DesignHeader.attach(design, body)

        assertTrue(code.startsWith("// "))
        assertTrue(code.endsWith(body))
        assertEquals(DesignRead.Designed(design), DesignHeader.read(code))
    }

    @Test
    fun `hand edits below the header are detected`() {
        val code = DesignHeader.attach(design, body).replace("안녕\")", "hello\")")

        assertEquals(DesignRead.EditedByHand(design), DesignHeader.read(code))
    }

    @Test
    fun `scripts without a header are plain code`() {
        assertIs<DesignRead.Plain>(DesignHeader.read(body))
        assertIs<DesignRead.Plain>(DesignHeader.read("// scriptwidget-design: {broken\n$body"))
    }

    @Test
    fun `the icon header stays on top and is kept when the design is saved again`() {
        val withIcon = ScriptAppearance("deep-blue", "magic").applyTo(DesignHeader.attach(design, body))

        val resaved = DesignHeader.attach(design.copy(refreshMinutes = 10), body, previous = withIcon)

        assertEquals(ScriptAppearance("deep-blue", "magic"), ScriptAppearance.parse(resaved))
        assertEquals(DesignRead.Designed(design.copy(refreshMinutes = 10)), DesignHeader.read(resaved))
    }
}
