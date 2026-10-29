package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetFamilyTest {

    @Test
    fun `2x2 widget on a phone is small`() {
        assertEquals(WidgetFamily.Small, WidgetFamily.fromSize(widthDp = 186, heightDp = 212))
    }

    @Test
    fun `wide but short widget is medium`() {
        assertEquals(WidgetFamily.Medium, WidgetFamily.fromSize(widthDp = 380, heightDp = 180))
    }

    @Test
    fun `wide and tall widget is large`() {
        assertEquals(WidgetFamily.Large, WidgetFamily.fromSize(widthDp = 380, heightDp = 400))
    }

    @Test
    fun `tall but narrow widget stays small`() {
        assertEquals(WidgetFamily.Small, WidgetFamily.fromSize(widthDp = 180, heightDp = 400))
    }

    @Test
    fun `unknown size falls back to small`() {
        assertEquals(WidgetFamily.Small, WidgetFamily.fromSize(widthDp = 0, heightDp = 0))
    }

    @Test
    fun `js names match Scriptable`() {
        assertEquals(listOf("small", "medium", "large"), WidgetFamily.entries.map { it.jsName })
    }
}
