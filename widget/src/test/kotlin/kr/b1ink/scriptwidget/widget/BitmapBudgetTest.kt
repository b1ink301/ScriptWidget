package kr.b1ink.scriptwidget.widget

import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.widget.render.BitmapBudget
import kr.b1ink.scriptwidget.widget.render.Styles
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BitmapBudgetTest {
    private fun image() = ImageNode(src = "/x.png")

    @Test
    fun `a few images keep the full size`() {
        val root = WidgetRoot(children = listOf(image(), StackNode(children = listOf(image(), TextNode("t")))))

        assertEquals(2, BitmapBudget.imageCount(root))
        assertEquals(Styles.MAX_IMAGE_PX, BitmapBudget.maxImagePx(root))
    }

    @Test
    fun `many images share the budget so the widget stays under the RemoteViews limit`() {
        val root = WidgetRoot(children = List(40) { image() })

        val px = BitmapBudget.maxImagePx(root)

        assertTrue(px < Styles.MAX_IMAGE_PX)
        assertTrue(40L * px * px * 4 <= BitmapBudget.TOTAL_BYTES)
        assertTrue(px >= BitmapBudget.MIN_IMAGE_PX)
    }
}
