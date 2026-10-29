package kr.b1ink.scriptwidget.widget

import kr.b1ink.scriptwidget.widget.render.AvailableWidth
import kotlin.test.Test
import kotlin.test.assertEquals

class AvailableWidthTest {
    @Test
    fun `padding on both sides is taken away`() {
        // Paddings are [top, leading, bottom, trailing].
        assertEquals(156f, AvailableWidth.inside(parent = 180f, padding = listOf(10f, 12f, 10f, 12f)))
    }

    @Test
    fun `a fixed width replaces the parent width`() {
        assertEquals(76f, AvailableWidth.inside(parent = 180f, padding = listOf(0f, 2f, 0f, 2f), fixedWidth = 80f))
    }

    @Test
    fun `never negative and no padding means the whole parent`() {
        assertEquals(0f, AvailableWidth.inside(parent = 10f, padding = listOf(0f, 20f, 0f, 20f)))
        assertEquals(180f, AvailableWidth.inside(parent = 180f, padding = null))
    }
}
