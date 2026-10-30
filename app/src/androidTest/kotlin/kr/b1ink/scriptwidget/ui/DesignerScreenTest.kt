package kr.b1ink.scriptwidget.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.DataBinding
import kr.b1ink.scriptwidget.domain.model.DesignElement
import kr.b1ink.scriptwidget.domain.model.DesignStack
import kr.b1ink.scriptwidget.domain.model.DesignText
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetDesign
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.ui.designer.DesignerActions
import kr.b1ink.scriptwidget.ui.designer.DesignerScreen
import kr.b1ink.scriptwidget.ui.designer.DesignerUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class DesignerScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val design = WidgetDesign(children = listOf(DesignText("제목"), DesignStack(children = listOf(DesignText("안쪽")))))
    private val preview = ScriptRunResult(
        widget = WidgetRoot(children = listOf(TextNode("제목"), StackNode(children = listOf(TextNode("안쪽"))))),
    )
    private val calls = mutableListOf<String>()
    private var state by mutableStateOf(DesignerUiState(name = "design_1", loaded = true, design = design, preview = preview))
    private var updated: DesignElement? = null

    private val actions = DesignerActions(
        onBack = { calls += "back" },
        onUndo = { calls += "undo" },
        onRedo = { calls += "redo" },
        onShowCode = { calls += "code" },
        onPin = { calls += "pin" },
        onSelect = { path -> calls += "select:$path"; state = state.copy(selected = path) },
        onFamily = { calls += "family:$it" },
        onAdd = { calls += "add:$it" },
        onUpdateSelected = { _, change -> updated = change(state.design.elementAt(state.selected!!)!!) },
        onUpdateDesign = { calls += "design" },
        onMove = { calls += "move:$it" },
        onDrop = { from, parent, index ->
            calls += "drop:$from:$parent:$index"
            state = state.copy(selected = parent + index)
        },
        onWrap = { calls += "wrap:$it" },
        onRemove = { calls += "remove" },
    )

    private fun show() = compose.setContent { DesignerScreen(state, actions) }

    @Test
    fun elementsAreSelectedFromTheOutlineOrByTappingThePreview() {
        show()

        compose.onNodeWithTag("outline-1-0").performClick()
        compose.onNodeWithTag("preview-0").performClick()

        assertEquals(listOf("select:[1, 0]", "select:[0]"), calls)
    }

    @Test
    fun theAddMenuAddsElementsAndDataBlocks() {
        show()

        compose.onNodeWithTag("add-element").performClick()
        compose.onNodeWithText("텍스트").performClick()
        compose.onNodeWithTag("add-element").performClick()
        compose.onNodeWithText("배터리").performClick()

        assertEquals(listOf("add:${DesignText("텍스트")}", "add:${DesignText("{value}%", data = DataBinding.Battery, size = 24f, weight = "bold")}"), calls)
    }

    @Test
    fun aSelectedTextIsEditedInItsProperties() {
        state = state.copy(selected = listOf(0))
        show()

        compose.onNodeWithTag("prop-text").performTextReplacement("새 제목")
        assertEquals("새 제목", (updated as DesignText).text)

        compose.onNodeWithTag("prop-data-battery").performClick()
        assertEquals(DataBinding.Battery, (updated as DesignText).data)
    }

    @Test
    fun theToolbarMovesWrapsAndRemovesTheSelection() {
        state = state.copy(selected = listOf(0))
        show()

        compose.onNodeWithTag("tool-up").performClick()
        compose.onNodeWithTag("tool-down").performClick()
        compose.onNodeWithTag("tool-wrap").performClick()
        compose.onNodeWithTag("tool-remove").performClick()

        assertEquals(listOf("move:-1", "move:1", "wrap:v", "remove"), calls)
    }

    @Test
    fun aRowDraggedByItsHandleIsDroppedIntoAStackOrBetweenRows() {
        show()
        val row = compose.onNodeWithTag("outline-0").fetchSemanticsNode().size.height.toFloat()

        // Down onto the middle of the stack row: inside it, at the end.
        compose.onNodeWithTag("drag-0").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, row / 2))
            moveBy(androidx.compose.ui.geometry.Offset(0f, row / 2))
            up()
        }
        // The inner row down below the last row: the end of the widget.
        compose.onNodeWithTag("drag-1-0").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(0f, row))
            moveBy(androidx.compose.ui.geometry.Offset(0f, row * 2))
            up()
        }

        assertEquals(listOf("drop:[0]:[1]:1", "drop:[1, 0]:[]:2"), calls)
        // Still on the outline, ready for the next move.
        compose.onNodeWithTag("drag-0").assertIsDisplayed()
    }

    @Test
    fun codeEditedByHandIsFlagged() {
        state = state.copy(editedByHand = true)
        show()

        compose.onNodeWithText("코드를 직접 고친 스크립트", substring = true).assertIsDisplayed()
        compose.onNodeWithText("코드").performClick()
        assertEquals(listOf("code"), calls)
    }
}
