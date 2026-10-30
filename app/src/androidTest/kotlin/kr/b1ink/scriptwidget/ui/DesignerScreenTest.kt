package kr.b1ink.scriptwidget.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.DataBinding
import kr.b1ink.scriptwidget.domain.model.DesignElement
import kr.b1ink.scriptwidget.domain.model.DesignImage
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
        onUpdateDesign = { calls += "design"; designChange = it },
        onMove = { calls += "move:$it" },
        onDrop = { from, parent, index ->
            calls += "drop:$from:$parent:$index"
            state = state.copy(selected = parent + index)
        },
        onWrap = { calls += "wrap:$it" },
        onRemove = { calls += "remove" },
        onPickPicture = { calls += "pick:$it" },
        onPickFont = { calls += "font-file" },
        onExportForGallery = { calls += "export" },
    )
    private var designChange: ((WidgetDesign) -> WidgetDesign)? = null

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
    fun anImageShowsABookmarkedPictureOrANewOne() {
        state = state.copy(
            design = WidgetDesign(children = listOf(DesignImage("https://a.b/c.png"))),
            selected = listOf(0),
            bookmarks = listOf("photo", "cat"),
        )
        show()

        compose.onNodeWithTag("prop-image-source-bookmark").performScrollTo().performClick()
        assertEquals(DesignImage(bookmark = "photo"), updated)
        state = state.copy(design = WidgetDesign(children = listOf(DesignImage(bookmark = "photo"))))
        compose.onNodeWithTag("prop-bookmark-cat").performScrollTo().performClick()
        assertEquals(DesignImage(bookmark = "cat"), updated)
        compose.onNodeWithTag("prop-bookmark-new").performScrollTo().performClick()
        compose.onNodeWithTag("prop-image-source-url").performScrollTo().performClick()
        assertEquals(DesignImage(), updated)

        assertEquals(listOf("pick:SelectedImage"), calls)
    }

    @Test
    fun theWidgetBackgroundCanBeABookmarkedPicture() {
        state = state.copy(bookmarks = listOf("photo"))
        show()
        compose.onNodeWithTag("panel-Properties").performClick()

        compose.onNodeWithTag("prop-bg-image-photo").performScrollTo().performClick()
        assertEquals("photo", designChange!!(design).background.image)
        compose.onNodeWithTag("prop-bg-image-new").performScrollTo().performClick()

        assertEquals(listOf("design", "pick:Background"), calls)
    }

    @Test
    fun dDayProgressAndWorldTimeAreDataBlocks() {
        state = state.copy(selected = listOf(0))
        show()

        compose.onNodeWithTag("prop-data-countdown").performScrollTo().performClick()
        assertEquals(DataBinding.Countdown(), (updated as DesignText).data)
        state = state.copy(design = WidgetDesign(children = listOf(DesignText("{value}", data = DataBinding.Countdown()))))
        compose.onNodeWithTag("prop-countdown-yearly-true").performScrollTo().performClick()
        assertEquals(DataBinding.Countdown(yearly = true), (updated as DesignText).data)
        compose.onNodeWithTag("prop-countdown-date").performScrollTo().performTextReplacement("2026-12-25")
        assertEquals(DataBinding.Countdown("2026-12-25"), (updated as DesignText).data)
        androidx.test.espresso.Espresso.closeSoftKeyboard()

        compose.onNodeWithTag("prop-data-progress").performScrollTo().performClick()
        assertEquals(DataBinding.Progress(), (updated as DesignText).data)
        compose.onNodeWithTag("prop-data-world").performScrollTo().performClick()
        assertEquals(DataBinding.WorldTime(), (updated as DesignText).data)
    }

    @Test
    fun aTextPicksAFontOrAddsAFontFile() {
        state = state.copy(selected = listOf(0), fonts = listOf("Pixel"))
        show()

        compose.onNodeWithTag("prop-font-serif").performScrollTo().performClick()
        assertEquals("serif", (updated as DesignText).font)
        compose.onNodeWithTag("prop-font-Pixel").performScrollTo().performClick()
        assertEquals("Pixel", (updated as DesignText).font)
        compose.onNodeWithTag("prop-font-new").performScrollTo().performClick()

        assertEquals(listOf("font-file"), calls)
    }

    @Test
    fun theMoreMenuExportsTheDesignForAGallery() {
        show()

        compose.onNodeWithTag("designer-more").performClick()
        compose.onNodeWithTag("export-gallery").performClick()

        assertEquals(listOf("export"), calls)
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
