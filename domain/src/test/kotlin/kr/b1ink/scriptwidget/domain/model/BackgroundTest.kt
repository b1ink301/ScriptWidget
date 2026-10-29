package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals

class BackgroundTest {
    private val gradient = Gradient(listOf("#FF000000", "#FFFFFFFF"))

    @Test
    fun `image wins over gradient and color`() {
        assertEquals(Background.Image("/a.png"), Background.of(color = "#FF000000", gradient = gradient, image = "/a.png"))
    }

    @Test
    fun `gradient wins over color`() {
        assertEquals(Background.Linear(gradient), Background.of(color = "#FF000000", gradient = gradient, image = null))
    }

    @Test
    fun `color is used when nothing else is set`() {
        assertEquals(Background.Solid("#FF000000"), Background.of(color = "#FF000000", gradient = null, image = null))
    }

    @Test
    fun `nothing set means no background`() {
        assertEquals(Background.None, Background.of(color = null, gradient = null, image = null))
    }

    @Test
    fun `a gradient needs at least two colors`() {
        assertEquals(Background.Solid("#FF000000"), Background.of("#FF000000", Gradient(listOf("#FFFFFFFF")), null))
    }

    @Test
    fun `missing locations are spread evenly`() {
        assertEquals(listOf(0f, 0.5f, 1f), Gradient(listOf("#FF000000", "#FF888888", "#FFFFFFFF")).stops())
        assertEquals(listOf(0f, 0.2f), Gradient(listOf("#FF000000", "#FFFFFFFF"), locations = listOf(0f, 0.2f)).stops())
    }
}
