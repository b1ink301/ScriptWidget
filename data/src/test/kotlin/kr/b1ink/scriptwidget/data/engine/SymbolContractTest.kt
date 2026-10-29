package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.LogLine
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scriptable's SFSymbol, rendered from Material Icons. */
class SymbolContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val symbols = FakeSymbolRenderer()
    private val images = FakeImageStore()

    private suspend fun run(script: String) = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(images = images, files = SandboxedFileSystem(tmp.newFolder()), symbols = symbols),
        timeoutMillis = 2_000,
    ).execute(script, ScriptEnvironment(runsInWidget = false), "symbols")

    @Test
    fun `named symbols produce an Image usable in widgets`() = runTest {
        val result = run(
            """
            const sym = SFSymbol.named("sun.max.fill");
            const w = new ListWidget();
            const img = w.addImage(sym.image);
            img.tintColor = Color.yellow();
            console.log(sym.image instanceof Image);
            Script.setWidget(w);
            """
        )
        assertNull(result.error, result.error)
        assertEquals("true", result.logs.single().message)
        assertEquals("wb_sunny", symbols.calls.first().materialName)
        assertTrue((result.widget!!.children.single() as ImageNode).src.endsWith(".png"))
    }

    @Test
    fun `font size and weight are applied`() = runTest {
        run(
            """
            const sym = SFSymbol.named("cloud");
            sym.applyFont(Font.systemFont(30));
            sym.applyBoldWeight();
            sym.image;
            """
        )
        assertEquals(FakeSymbolRenderer.Call("cloud", 90, "bold"), symbols.calls.single())
    }

    @Test
    fun `the image is rendered once per configuration`() = runTest {
        run("""const s = SFSymbol.named("cloud"); s.image; s.image;""")
        assertEquals(1, symbols.calls.size)
    }

    @Test
    fun `unknown symbols render a placeholder and warn`() = runTest {
        val result = run("""SFSymbol.named("no.such.symbol").image;""")
        assertNull(result.error)
        assertEquals("help_outline", symbols.calls.single().materialName)
        assertTrue(result.logs.any { it.level == "warn" && "no.such.symbol" in it.message })
    }
}
