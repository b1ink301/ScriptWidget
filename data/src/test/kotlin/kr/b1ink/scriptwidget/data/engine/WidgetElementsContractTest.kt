package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Less common widget element members used by community scripts. */
class WidgetElementsContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private suspend fun run(script: String) = QuickJsScriptEngine(
        Prelude.load(), fakeServices(files = SandboxedFileSystem(tmp.newFolder())), timeoutMillis = 2_000,
    ).execute(script, ScriptEnvironment(runsInWidget = false), "elements")

    @Test
    fun `image alignment, tint and content mode`() = runTest {
        val result = run(
            """
            const img = await new Request("https://img").loadImage();
            const w = new ListWidget();
            const a = w.addImage(img); a.centerAlignImage(); a.tintColor = Color.red(); a.applyFillingContentMode();
            const b = w.addImage(img); b.rightAlignImage(); b.applyFittingContentMode(); b.resizable = false;
            const c = w.addImage(img); c.leftAlignImage(); c.containerRelativeShape = true; c.borderWidth = 1;
            Script.setWidget(w);
            """
        )
        assertNull(result.error, result.error)
        val images = result.widget!!.children.map { it as ImageNode }
        assertEquals(listOf("center", "right", "left"), images.map { it.align })
        assertEquals(listOf("#FFFF3B30", null, null), images.map { it.tint })
        assertEquals(listOf("fill", "fit", "fit"), images.map { it.contentMode })
    }

    @Test
    fun `stack borders`() = runTest {
        val result = run(
            """
            const w = new ListWidget();
            const s = w.addStack();
            s.borderColor = new Color("#00ff00");
            s.borderWidth = 2;
            Script.setWidget(w);
            """
        )
        val stack = result.widget!!.children.single() as StackNode
        assertEquals("#FF00FF00", stack.borderColor)
        assertEquals(2f, stack.borderWidth)
    }

    @Test
    fun `text members without an Android equivalent are accepted`() = runTest {
        val result = run(
            """
            const w = new ListWidget();
            const t = w.addText("shadow");
            t.shadowColor = Color.black(); t.shadowRadius = 2; t.shadowOffset = new Point(1, 1); t.minimumScaleFactor = 0.5;
            Script.setWidget(w);
            """
        )
        assertNull(result.error, result.error)
        assertEquals("shadow", (result.widget!!.children.single() as TextNode).text)
    }
}
