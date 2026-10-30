package kr.b1ink.scriptwidget.ui.designer

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.fake.FakeScriptEngine
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.fake.FakeWidgetBindingRepository
import kr.b1ink.scriptwidget.domain.model.DataBinding
import kr.b1ink.scriptwidget.domain.model.DesignHeader
import kr.b1ink.scriptwidget.domain.model.DesignRead
import kr.b1ink.scriptwidget.domain.model.DesignSpacer
import kr.b1ink.scriptwidget.domain.model.DesignStack
import kr.b1ink.scriptwidget.domain.model.DesignText
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetDesign
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.domain.repository.DesignScriptGenerator
import kr.b1ink.scriptwidget.domain.usecase.OpenDesignUseCase
import kr.b1ink.scriptwidget.domain.usecase.RunScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.SaveDesignUseCase
import kr.b1ink.scriptwidget.domain.usecase.SaveScriptUseCase
import kr.b1ink.scriptwidget.ui.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesignerViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val generator = DesignScriptGenerator { d -> "// ${d.children.size} elements\n" }
    private val start = WidgetDesign(children = listOf(DesignText("제목"), DesignStack(children = listOf(DesignText("안")))))
    private val scripts = FakeScriptRepository(mapOf("clock" to DesignHeader.attach(start, generator.generate(start))))
    private val engine = FakeScriptEngine(result = ScriptRunResult(widget = WidgetRoot(children = listOf(TextNode("ok")))))

    private val bookmarks = kr.b1ink.scriptwidget.domain.fake.FakeFileBookmarks()
    private val documents = kr.b1ink.scriptwidget.domain.fake.FakeScriptDocuments().apply { bytes["content://pic.jpg"] = byteArrayOf(1) }

    private fun vm(name: String = "clock") = DesignerViewModel(
        name = name,
        openDesign = OpenDesignUseCase(scripts),
        saveDesign = SaveDesignUseCase(SaveScriptUseCase(scripts, FakeWidgetBindingRepository(), FakeRefreshScheduler()), scripts, generator),
        generator = generator,
        runScript = RunScriptUseCase(engine),
        previewDelayMs = 0,
        bookmarks = bookmarks,
        addBookmark = kr.b1ink.scriptwidget.domain.usecase.AddFileBookmarkUseCase(bookmarks, documents),
    )

    @Test
    fun `opens the design and draws a preview of the generated script`() = runTest {
        val vm = vm()
        val state = vm.state.value

        assertEquals(start, state.design)
        assertEquals(WidgetRoot(children = listOf(TextNode("ok"))), state.preview?.widget)
        assertEquals("// 2 elements\n", engine.calls.last().script)
        assertTrue(engine.calls.last().env.runsInWidget)
    }

    @Test
    fun `adding selects the new element, editing changes it and the preview follows`() = runTest {
        val vm = vm()
        vm.select(listOf(1))

        vm.add(DesignText("새 글"))
        assertEquals(listOf(1, 1), vm.state.value.selected)

        vm.updateSelected { (it as DesignText).copy(text = "고친 글") }
        assertEquals(DesignText("고친 글"), vm.state.value.design.elementAt(listOf(1, 1)))
        assertEquals("// 2 elements\n", engine.calls.last().script)
        assertTrue(vm.state.value.dirty)

        vm.add(DesignSpacer())
        assertEquals("// 2 elements\n", engine.calls.last().script)
        vm.select(null)
        vm.add(DesignSpacer())
        assertEquals("// 3 elements\n", engine.calls.last().script)
    }

    @Test
    fun `move, wrap and remove keep the selection on the element`() = runTest {
        val vm = vm()
        vm.select(listOf(0))

        vm.moveSelected(1)
        assertEquals(listOf(1), vm.state.value.selected)
        vm.wrapSelected("v")
        assertEquals(DesignStack(dir = "v", children = listOf(DesignText("제목"))), vm.state.value.design.elementAt(listOf(1)))
        vm.removeSelected()
        assertNull(vm.state.value.selected)
        assertEquals(1, vm.state.value.design.children.size)
    }

    @Test
    fun `dragging moves an element into a stack, selects it and is undone in one step`() = runTest {
        val vm = vm()

        vm.moveTo(listOf(0), toParent = listOf(1), index = 0)

        assertEquals(listOf(DesignStack(children = listOf(DesignText("제목"), DesignText("안")))), vm.state.value.design.children)
        assertEquals(listOf(0, 0), vm.state.value.selected)
        vm.undo()
        assertEquals(start, vm.state.value.design)
    }

    @Test
    fun `undo and redo step through the edits`() = runTest {
        val vm = vm()
        assertFalse(vm.state.value.canUndo)
        vm.add(DesignText("하나"))
        vm.add(DesignText("둘"))

        vm.undo()
        assertEquals(3, vm.state.value.design.children.size)
        assertTrue(vm.state.value.canRedo)
        vm.undo()
        assertEquals(start, vm.state.value.design)
        assertFalse(vm.state.value.canUndo)
        vm.redo()
        assertEquals(3, vm.state.value.design.children.size)
        vm.add(DesignText("셋"))
        assertFalse(vm.state.value.canRedo)
    }

    @Test
    fun `saving writes the regenerated script with its design`() = runTest {
        val vm = vm()
        vm.updateDesign { it.copy(refreshMinutes = 15) }
        vm.select(listOf(0))
        vm.updateSelected { (it as DesignText).copy(data = DataBinding.Battery, text = "{value}%") }

        vm.saveIfDirty()

        assertFalse(vm.state.value.dirty)
        assertEquals(DesignRead.Designed(vm.state.value.design), OpenDesignUseCase(scripts)("clock"))
    }

    @Test
    fun `a script whose code was edited by hand warns before the code is replaced`() = runTest {
        scripts.files["clock"] = scripts.files.getValue("clock") + "// 손으로 고친 줄\n"

        val vm = vm()

        assertTrue(vm.state.value.editedByHand)
        assertEquals(start, vm.state.value.design)
    }

    @Test
    fun `the preview size can be changed`() = runTest {
        val vm = vm()

        vm.selectFamily(WidgetFamily.Large)

        assertEquals(WidgetFamily.Large, engine.calls.last().env.widgetFamily)
    }

    @Test
    fun `typing into one field is undone in one step`() = runTest {
        val vm = vm()
        vm.select(listOf(0))

        "제목입니다".forEachIndexed { i, _ -> vm.updateSelected(coalesce = true) { e -> (e as DesignText).copy(text = "제목입니다".take(i + 1)) } }
        vm.undo()

        assertEquals(start, vm.state.value.design)
    }

    @Test
    fun `a picked picture becomes a bookmark and fills the image or the background`() = runTest {
        bookmarks.save("photo", byteArrayOf(0))
        val vm = vm()
        assertEquals(listOf("photo"), vm.state.value.bookmarks)

        vm.add(kr.b1ink.scriptwidget.domain.model.DesignImage(bookmark = "photo"))
        vm.addPicture("content://pic.jpg", PictureTarget.SelectedImage)
        assertEquals(kr.b1ink.scriptwidget.domain.model.DesignImage(bookmark = "photo 2"), vm.state.value.design.elementAt(vm.state.value.selected!!))

        vm.addPicture("content://pic.jpg", PictureTarget.Background)
        assertEquals("photo 3", vm.state.value.design.background.image)
        assertEquals(listOf("photo", "photo 2", "photo 3"), vm.state.value.bookmarks)
    }

    @Test
    fun `a picture saved under the name the design already shows redraws the preview`() = runTest {
        val vm = vm()
        vm.updateDesign { it.copy(background = it.background.copy(image = "photo")) }
        val runs = engine.calls.size

        vm.addPicture("content://pic.jpg", PictureTarget.Background)

        assertEquals("photo", vm.state.value.design.background.image)
        assertEquals(runs + 1, engine.calls.size)
    }
}
