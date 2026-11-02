package kr.b1ink.scriptwidget.widget

import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.widget.render.ViewBudget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Glance gives a widget at most 500 views and throws past that, leaving the widget undrawn. Big
 * grids (a dot per day of the year) go over it; their densest plain stacks are drawn as one bitmap.
 */
class ViewBudgetTest {
    private fun dots(rows: Int, perRow: Int, spacing: Float = 0f) =
        StackNode(dir = "v", children = List(rows) { StackNode(spacing = spacing, children = List(perRow) { TextNode("●", size = 5f) }) })

    @Test
    fun `counts a view per element, per spacing gap and per container nested past ten children`() {
        assertEquals(1, ViewBudget.views(TextNode("a")))
        // stack + 3 texts + 2 gaps
        assertEquals(6, ViewBudget.views(StackNode(spacing = 4f, children = List(3) { TextNode("a") })))
        // stack + 12 texts + 1 inner container for the children past nine
        assertEquals(14, ViewBudget.views(StackNode(children = List(12) { TextNode("a") })))
    }

    @Test
    fun `the estimate matches where Glance gave up on a device`() {
        // Measured: 49 rows of 9 texts drew, 50 didn't; with spacing 2, 25 rows drew and 26 didn't.
        assertTrue(ViewBudget.views(WidgetRoot(children = listOf(dots(49, 9)))) < 500)
        assertTrue(ViewBudget.views(WidgetRoot(children = listOf(dots(50, 9)))) >= 500)
        assertTrue(ViewBudget.views(WidgetRoot(children = listOf(dots(25, 9, spacing = 2f)))) < 500)
        assertTrue(ViewBudget.views(WidgetRoot(children = listOf(dots(26, 9, spacing = 2f)))) >= 500)
    }

    @Test
    fun `a widget within the budget is drawn as it is`() {
        assertEquals(emptySet(), ViewBudget.rasterized(WidgetRoot(children = listOf(dots(10, 9)))))
    }

    @Test
    fun `an oversized grid is drawn as one bitmap`() {
        val grid = dots(17, 22, spacing = 9f)
        val root = WidgetRoot(children = listOf(grid, SpacerNode(8f), StackNode(children = listOf(TextNode("120 days left")))))

        assertEquals(setOf(grid), ViewBudget.rasterized(root))
    }

    @Test
    fun `only as much as needed is drawn as bitmaps, the largest stacks first`() {
        val big = dots(30, 9)
        val small = dots(20, 9)
        val root = WidgetRoot(children = listOf(big, small))

        assertEquals(setOf(big), ViewBudget.rasterized(root))
    }

    @Test
    fun `stacks with taps, images, flexible spacers or non-solid backgrounds keep their views`() {
        assertTrue(ViewBudget.canRasterize(dots(2, 3)))
        assertTrue(ViewBudget.canRasterize(StackNode(bg = "#FF000000", cornerRadius = 4f, padding = listOf(2f, 2f, 2f, 2f), children = listOf(TextNode("a"), SpacerNode(3f)))))
        assertFalse(ViewBudget.canRasterize(StackNode(children = listOf(TextNode("a", url = "https://a.b")))))
        assertFalse(ViewBudget.canRasterize(StackNode(children = listOf(TextNode("a", action = "x")))))
        assertFalse(ViewBudget.canRasterize(StackNode(action = "x", children = listOf(TextNode("a")))))
        assertFalse(ViewBudget.canRasterize(StackNode(children = listOf(ImageNode("/a.png")))))
        assertFalse(ViewBudget.canRasterize(StackNode(children = listOf(TextNode("a"), SpacerNode()))))
        assertFalse(ViewBudget.canRasterize(StackNode(backgroundImage = "/a.png", children = listOf(TextNode("a")))))
        assertFalse(ViewBudget.canRasterize(StackNode(borderColor = "#FFFFFFFF", borderWidth = 1f, children = listOf(TextNode("a")))))
        assertFalse(ViewBudget.canRasterize(StackNode(children = listOf(StackNode(children = listOf(TextNode("a", url = "https://a.b")))))))
    }

    @Test
    fun `a grid that can't be a bitmap is still split into the parts that can`() {
        // The outer column has a tappable footer, so its rows are drawn as bitmaps instead.
        val rows = List(40) { StackNode(children = List(12) { TextNode("●") }) }
        val root = WidgetRoot(children = listOf(StackNode(dir = "v", children = rows + TextNode("open", url = "https://a.b"))))

        val rasterized = ViewBudget.rasterized(root)

        assertTrue(rasterized.isNotEmpty())
        assertTrue(rasterized.all { it in rows })
        assertTrue(ViewBudget.views(root, rasterized) < ViewBudget.MAX_VIEWS)
    }
}
