package kr.b1ink.scriptwidget.widget

import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.widget.render.TextPlan
import kotlin.test.Test
import kotlin.test.assertEquals

class TextPlanTest {
    private fun native(family: TextPlan.Family = TextPlan.Family.SansSerif, weight: TextPlan.Weight = TextPlan.Weight.Normal) =
        TextPlan.Native(family, weight)

    @Test
    fun `plain system text stays native`() {
        assertEquals(native(), TextPlan.of(TextNode("a")))
        assertEquals(native(weight = TextPlan.Weight.Medium), TextPlan.of(TextNode("a", weight = "medium")))
        assertEquals(native(weight = TextPlan.Weight.Bold), TextPlan.of(TextNode("a", weight = "bold", italic = true)))
    }

    @Test
    fun `well known font names map to system families`() {
        assertEquals(native(TextPlan.Family.Monospace), TextPlan.of(TextNode("a", font = "Menlo")))
        assertEquals(native(TextPlan.Family.Monospace), TextPlan.of(TextNode("a", font = "monospace")))
        assertEquals(native(TextPlan.Family.Monospace), TextPlan.of(TextNode("a", font = "Courier-Bold")))
        assertEquals(native(TextPlan.Family.Serif), TextPlan.of(TextNode("a", font = "Georgia")))
        assertEquals(native(TextPlan.Family.Serif), TextPlan.of(TextNode("a", font = "TimesNewRomanPSMT")))
        assertEquals(native(), TextPlan.of(TextNode("a", font = "rounded")))
        assertEquals(native(), TextPlan.of(TextNode("a", font = "AvenirNext-Regular")))
    }

    @Test
    fun `what Glance can't draw becomes a bitmap`() {
        listOf(
            TextNode("a", weight = "thin"),
            TextNode("a", weight = "semibold"),
            TextNode("a", weight = "black"),
            TextNode("a", shadowColor = "#FF000000", shadowRadius = 2f),
            TextNode("a", shadowColor = "#FF000000", shadowOffset = listOf(0f, 1f)),
            TextNode("a", minimumScaleFactor = 0.5f),
            TextNode("a", font = "Pixel", fontFile = "/fonts/Pixel.ttf"),
        ).forEach { assertEquals(TextPlan.Bitmap, TextPlan.of(it), it.toString()) }
    }

    @Test
    fun `a shadow without blur or offset is invisible and stays native`() {
        assertEquals(native(), TextPlan.of(TextNode("a", shadowColor = "#FF000000")))
    }
}
