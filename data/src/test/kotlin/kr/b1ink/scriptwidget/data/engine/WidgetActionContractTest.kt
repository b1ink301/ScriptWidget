package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Interactive widgets (Android extension): `tapAction` on elements, `args.widgetAction` on re-run. */
class WidgetActionContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private suspend fun run(script: String, action: String? = null) = QuickJsScriptEngine(
        Prelude.load(), fakeServices(files = SandboxedFileSystem(tmp.newFolder())), timeoutMillis = 2_000,
    ).execute(script, ScriptEnvironment(runsInWidget = true, widgetAction = action), "actions")

    @Test
    fun `elements carry their tap action`() = runTest {
        val result = run(
            """
            const w = new ListWidget();
            w.tapAction = "refresh";
            const row = w.addStack();
            row.tapAction = "row";
            const plus = row.addText("+");
            plus.tapAction = "increment";
            Script.setWidget(w);
            """
        )
        assertNull(result.error, result.error)
        val root = result.widget!!
        assertEquals("refresh", root.action)
        val row = root.children.single() as StackNode
        assertEquals("row", row.action)
        assertEquals("increment", (row.children.single() as TextNode).action)
    }

    @Test
    fun `the tapped action is available as args widgetAction`() = runTest {
        val tapped = run("""console.log(args.widgetAction); Script.setWidget(new ListWidget());""", action = "increment")
        assertEquals("increment", tapped.logs.single().message)

        val plain = run("""console.log(args.widgetAction); Script.setWidget(new ListWidget());""")
        assertEquals("null", plain.logs.single().message)
    }

    @Test
    fun `addButton is a shortcut for a tappable text`() = runTest {
        val result = run("""const w = new ListWidget(); w.addButton("+1", "increment"); Script.setWidget(w);""")
        val button = result.widget!!.children.single() as StackNode
        assertEquals("increment", button.action)
        assertEquals("+1", (button.children.single() as TextNode).text)
    }
}
