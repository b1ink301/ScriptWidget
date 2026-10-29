package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.TextNode
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Font names, custom font files, shadows and minimumScaleFactor on WidgetText. */
class TextStyleContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val files by lazy { SandboxedFileSystem(tmp.newFolder()) }

    @Test
    fun `fonts, shadows and scale factors reach the widget tree`() = runTest {
        files.writeBytes("${files.documentsDirectory}/fonts/Pixel.ttf", byteArrayOf(0, 1, 0, 0))

        val result = QuickJsScriptEngine(Prelude.load(), fakeServices(files = files), timeoutMillis = 2_000).execute(
            """
            const w = new ListWidget();
            const a = w.addText("a"); a.font = new Font("Menlo", 12);
            const b = w.addText("b"); b.font = Font.heavyMonospacedSystemFont(14);
            b.shadowColor = Color.black(); b.shadowRadius = 3; b.shadowOffset = new Point(1, 2);
            const c = w.addText("c"); c.font = new Font("Pixel", 10); c.minimumScaleFactor = 0.5;
            const d = w.addText("d"); d.font = Font.semiboldRoundedSystemFont(12);
            const e = w.addText("e");
            Script.setWidget(w);
            """,
            ScriptEnvironment(runsInWidget = true),
            "fonts",
        )

        assertNull(result.error, result.error)
        val texts = result.widget!!.children.map { it as TextNode }
        assertEquals(listOf("Menlo", "monospace", "Pixel", "rounded", "system"), texts.map { it.font })
        assertEquals(listOf("regular", "heavy", "regular", "semibold", "regular"), texts.map { it.weight })
        assertTrue(texts[2].fontFile!!.endsWith("/Documents/fonts/Pixel.ttf"), texts[2].fontFile)
        assertEquals(listOf(null, null, null, null), listOf(texts[0], texts[1], texts[3], texts[4]).map { it.fontFile })
        assertEquals("#FF000000", texts[1].shadowColor)
        assertEquals(3f, texts[1].shadowRadius)
        assertEquals(listOf(1f, 2f), texts[1].shadowOffset)
        assertNull(texts[0].shadowColor)
        assertEquals(listOf(1f, 1f, 0.5f, 1f, 1f), texts.map { it.minimumScaleFactor })
    }

    @Test
    fun `dynamic colors carry both the light and the dark color`() = runTest {
        val result = QuickJsScriptEngine(Prelude.load(), fakeServices(files = files), timeoutMillis = 2_000).execute(
            """
            const c = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E", 0.5));
            console.log(c instanceof Color, c.hex, c.alpha);
            const w = new ListWidget();
            w.backgroundColor = c;
            const t = w.addText("a"); t.textColor = Color.dynamic(Color.black(), Color.white());
            Script.setWidget(w);
            """,
            ScriptEnvironment(runsInWidget = true),
            "dynamic",
        )

        assertNull(result.error, result.error)
        assertEquals("true FFFFFF 1", result.logs.single().message)
        assertEquals("#FFFFFFFF/#801C1C1E", result.widget!!.bg)
        assertEquals("#FF000000/#FFFFFFFF", (result.widget!!.children.single() as TextNode).color)
    }

    @Test
    fun `an invalid hex is black instead of an error, like Scriptable`() = runTest {
        val result = QuickJsScriptEngine(Prelude.load(), fakeServices(files = files), timeoutMillis = 2_000).execute(
            """
            const c = new Color("default");
            console.log(c.hex, c.alpha, JSON.stringify(new Color("nope", 0.5)));
            """,
            ScriptEnvironment(runsInWidget = false),
            "color",
        )

        assertNull(result.error, result.error)
        assertEquals("000000 1 \"#80000000\"", result.logs.single().message)
    }
}
