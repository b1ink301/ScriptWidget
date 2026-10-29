package kr.b1ink.scriptwidget.ui

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.ui.preview.WidgetPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/** The in-app preview must lay out flexible stacks like the home screen widget (GlanceRenderer). */
@RunWith(AndroidJUnit4::class)
class WidgetPreviewTest {
    @get:Rule
    val compose = createComposeRule()

    /** A cell that grows both ways and centers its letter, like a grid cell. */
    private fun cell(letter: String) = StackNode(
        dir = "v",
        children = listOf(
            SpacerNode(),
            StackNode(children = listOf(SpacerNode(), TextNode(letter, size = 14f), SpacerNode())),
            SpacerNode(),
        ),
    )

    private fun centerX(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().let { (it.left + it.right) / 2 }
    private fun centerY(text: String): Dp = compose.onNodeWithText(text).getUnclippedBoundsInRoot().let { (it.top + it.bottom) / 2 }

    @Test
    fun flexibleCellsShareTheRowAndRowsShareTheHeight() {
        val root = WidgetRoot(
            padding = listOf(0f, 0f, 0f, 0f),
            children = listOf(
                StackNode(children = listOf(cell("A"), cell("B"), cell("C"))),
                StackNode(children = listOf(cell("D"), cell("E"), cell("F"))),
            ),
        )
        compose.setContent { WidgetPreview(root, WidgetFamily.Large) } // 345 x 360 dp

        // Columns at 1/6, 3/6 and 5/6 of the width.
        assertTrue(centerX("A") < Dp(345f / 3), "A at ${centerX("A")}")
        assertTrue(centerX("B") > Dp(345f / 3) && centerX("B") < Dp(345f * 2 / 3), "B at ${centerX("B")}")
        assertTrue(centerX("C") > Dp(345f * 2 / 3), "C at ${centerX("C")}")
        // Rows in the top and bottom halves.
        assertTrue(centerY("A") < Dp(180f), "A at ${centerY("A")}")
        assertTrue(centerY("D") > Dp(180f), "D at ${centerY("D")}")
    }
}
