package kr.b1ink.scriptwidget.widget

import kr.b1ink.scriptwidget.widget.render.ThemedColor
import kotlin.test.Test
import kotlin.test.assertEquals

/** `Color.dynamic(light, dark)` arrives as "#light/#dark"; plain colors are the same in both themes. */
class ThemedColorTest {
    @Test
    fun `dynamic colors split into light and dark`() {
        assertEquals("#FFFFFFFF" to "#801C1C1E", ThemedColor.split("#FFFFFFFF/#801C1C1E"))
    }

    @Test
    fun `plain colors are used for both themes`() {
        assertEquals("#FF112233" to "#FF112233", ThemedColor.split("#FF112233"))
    }

    @Test
    fun `picking by theme`() {
        assertEquals("#FF000000", ThemedColor.pick("#FF000000/#FFFFFFFF", night = false))
        assertEquals("#FFFFFFFF", ThemedColor.pick("#FF000000/#FFFFFFFF", night = true))
    }
}
