package kr.b1ink.scriptwidget.widget

import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.widget.render.StackLayout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Scriptable stacks with a flexible spacer grow along their axis; RemoteViews rows/columns only
 * wrap their content, so the renderer must fill that axis explicitly.
 */
class StackLayoutTest {

    @Test
    fun `a horizontal stack with a flexible spacer fills its width`() {
        assertTrue(StackLayout.fillsMainAxis(StackNode(dir = "h", children = listOf(TextNode("a"), SpacerNode(), TextNode("b")))))
    }

    @Test
    fun `a vertical stack with a flexible spacer fills its height`() {
        assertTrue(StackLayout.fillsMainAxis(StackNode(dir = "v", children = listOf(TextNode("a"), SpacerNode()))))
    }

    @Test
    fun `fixed spacers and plain content keep the stack wrapping`() {
        assertFalse(StackLayout.fillsMainAxis(StackNode(children = listOf(TextNode("a"), SpacerNode(8f)))))
        assertFalse(StackLayout.fillsMainAxis(StackNode(children = listOf(TextNode("a")))))
    }

    @Test
    fun `an explicit size wins`() {
        assertFalse(StackLayout.fillsMainAxis(StackNode(size = listOf(100f, 0f), children = listOf(SpacerNode()))))
    }

    // --- sharing a row's width (SwiftUI HStack: flexible children split the space) ---

    private fun row(vararg children: kr.b1ink.scriptwidget.domain.model.WidgetNode) = StackNode(dir = "h", children = children.toList())
    private fun column(vararg children: kr.b1ink.scriptwidget.domain.model.WidgetNode) = StackNode(dir = "v", children = children.toList())

    @Test
    fun `a column of rows with flexible spacers wants width`() {
        // News widget: left column = rows of [rank, title, spacer, source].
        val newsColumn = column(row(TextNode("1"), TextNode("title"), SpacerNode(), TextNode("KBS")), row(TextNode("2")))

        assertTrue(StackLayout.wantsWidth(newsColumn))
    }

    @Test
    fun `nested deeper still wants width, plain content does not`() {
        assertTrue(StackLayout.wantsWidth(column(column(row(TextNode("a"), SpacerNode())))))
        assertFalse(StackLayout.wantsWidth(column(TextNode("a"), row(TextNode("b"), SpacerNode(4f)))))
        assertFalse(StackLayout.wantsWidth(TextNode("a")))
    }

    @Test
    fun `a fixed width never shares`() {
        val fixed = StackNode(dir = "v", size = listOf(120f, 0f), children = listOf(row(TextNode("a"), SpacerNode())))
        assertFalse(StackLayout.wantsWidth(fixed))
    }

    // --- RemoteViews draws at most 10 children per row/column ---

    @Test
    fun `up to ten children are drawn directly`() {
        val slots = StackLayout.nest((1..10).toList())
        assertTrue(slots.direct == (1..10).toList() && slots.rest == null)
    }

    @Test
    fun `more than ten nest the rest so nothing is dropped`() {
        val slots = StackLayout.nest((1..25).toList())

        kotlin.test.assertEquals((1..9).toList(), slots.direct)
        kotlin.test.assertEquals((10..18).toList(), slots.rest!!.direct)
        kotlin.test.assertEquals((19..25).toList(), slots.rest!!.rest!!.direct)
        kotlin.test.assertEquals(null, slots.rest!!.rest!!.rest)
        kotlin.test.assertEquals((1..25).toList(), slots.flatten())
    }

    // --- a single-line title before the flexible spacer truncates instead of pushing the rest out ---

    @Test
    fun `the one-line text before the flexible spacer takes the free space`() {
        // [rank, gap, title, spacer, source]
        val newsRow = row(TextNode("1"), SpacerNode(5f), TextNode("long title", lineLimit = 1), SpacerNode(), TextNode("KBS"))

        kotlin.test.assertEquals(2, StackLayout.stretchedText(newsRow))
    }

    @Test
    fun `no stretching without a single-line text right before one flexible spacer`() {
        kotlin.test.assertEquals(null, StackLayout.stretchedText(row(TextNode("t"), SpacerNode(), TextNode("s"))))
        kotlin.test.assertEquals(null, StackLayout.stretchedText(row(TextNode("t", lineLimit = 1), TextNode("s"))))
        kotlin.test.assertEquals(null, StackLayout.stretchedText(row(TextNode("t", lineLimit = 1), SpacerNode(), TextNode("u"), SpacerNode())))
        kotlin.test.assertEquals(null, StackLayout.stretchedText(column(TextNode("t", lineLimit = 1), SpacerNode())))
    }

    // --- growing vertically, and growth propagating to the parents (Sub Calendar cards) ---

    /** A card: [v-stack with a flexible spacer, column with a row holding a flexible spacer + text]. */
    private fun card() = row(column(SpacerNode()), column(row(SpacerNode()), TextNode("Netflix")))

    @Test
    fun `a card with spacers on both axes wants width and height`() {
        kotlin.test.assertTrue(StackLayout.wantsWidth(card()))
        kotlin.test.assertTrue(StackLayout.wantsHeight(card()))
    }

    @Test
    fun `rows and columns of such cards want the space too`() {
        val grid = column(row(card(), SpacerNode(6f), card()), SpacerNode(6f), row(card(), SpacerNode(6f), card()))

        kotlin.test.assertTrue(StackLayout.wantsWidth(grid))
        kotlin.test.assertTrue(StackLayout.wantsHeight(grid))
        kotlin.test.assertTrue(StackLayout.wantsHeight(grid.children.first()))
    }

    @Test
    fun `fixed heights and plain content don't want height`() {
        kotlin.test.assertFalse(StackLayout.wantsHeight(row(TextNode("a"), SpacerNode())))
        kotlin.test.assertFalse(StackLayout.wantsHeight(StackNode(dir = "v", size = listOf(0f, 15f), children = listOf(SpacerNode()))))
        kotlin.test.assertFalse(StackLayout.wantsHeight(TextNode("a")))
    }

    // --- aligning children inside a column only stretches when the column itself is full width ---

    @Test
    fun `a wrap-content column doesn't stretch its centered children`() {
        // Pixel widget: the weather icon's column sits between texts in a row.
        val iconColumn = column(SpacerNode(2f), kr.b1ink.scriptwidget.domain.model.ImageNode(src = "/i.png", align = "center"))

        kotlin.test.assertFalse(StackLayout.columnFillsWidth(iconColumn, weighted = false))
    }

    @Test
    fun `columns that grow, get a share or have a width align across it`() {
        kotlin.test.assertTrue(StackLayout.columnFillsWidth(column(row(TextNode("a"), SpacerNode())), weighted = false))
        kotlin.test.assertTrue(StackLayout.columnFillsWidth(column(TextNode("a")), weighted = true))
        kotlin.test.assertTrue(StackLayout.columnFillsWidth(StackNode(dir = "v", size = listOf(120f, 0f), children = listOf(TextNode("a"))), weighted = false))
    }
}
