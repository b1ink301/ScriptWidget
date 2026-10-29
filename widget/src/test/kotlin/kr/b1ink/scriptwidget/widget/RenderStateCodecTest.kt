package kr.b1ink.scriptwidget.widget

import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.RunTimings
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.widget.state.RenderStateCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RenderStateCodecTest {

    @Test
    fun `round trips a full state`() {
        val state = RenderState(
            scriptName = "s",
            tree = WidgetRoot(children = listOf(TextNode("hi"))),
            error = "e",
            updatedAt = 5,
            timings = RunTimings(totalMs = 3),
        )
        assertEquals(state, RenderStateCodec.decode(RenderStateCodec.encode(state)))
    }

    @Test
    fun `missing or corrupted data decodes to null`() {
        assertNull(RenderStateCodec.decode(null))
        assertNull(RenderStateCodec.decode("{not json"))
    }
}
