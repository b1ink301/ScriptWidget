package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Scriptable's DrawContext, Path and Rect: drawing commands are recorded in JS and rendered natively. */
class DrawContextContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val canvas = FakeCanvasRenderer()
    private val images = FakeImageStore()

    private suspend fun run(script: String) = QuickJsScriptEngine(
        Prelude.load(),
        fakeServices(images = images, files = SandboxedFileSystem(tmp.newFolder()), canvas = canvas),
        timeoutMillis = 2_000,
    ).execute(script, ScriptEnvironment(runsInWidget = false), "draw")

    private suspend fun log(script: String): String {
        val result = run(script)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    @Test
    fun `Rect exposes Scriptable's geometry`() = runTest {
        assertEquals(
            "10 20 35 120 50 100 30 70 10,20 50x100",
            log(
                """
                const r = new Rect(10, 20, 50, 100);
                console.log(r.minX, r.minY, r.midX, r.maxY, r.width, r.height, r.x + 20, r.midY,
                  r.origin.x + "," + r.origin.y, r.size.width + "x" + r.size.height);
                """
            ),
        )
    }

    @Test
    fun `drawing operations are recorded in order and rendered to an Image`() = runTest {
        val result = run(
            """
            const ctx = new DrawContext();
            ctx.size = new Size(100, 50);
            ctx.opaque = false;
            ctx.respectScreenScale = true;
            ctx.setFillColor(Color.red());
            ctx.fillRect(new Rect(0, 0, 100, 50));
            ctx.setStrokeColor(new Color("#00ff00"));
            ctx.setLineWidth(3);
            ctx.strokeEllipse(new Rect(10, 10, 20, 20));
            const p = new Path();
            p.move(new Point(0, 50));
            p.addLine(new Point(50, 0));
            p.addQuadCurve(new Point(100, 50), new Point(75, 0));
            p.addCurve(new Point(0, 50), new Point(60, 60), new Point(30, 60));
            p.addRoundedRect(new Rect(5, 5, 10, 10), 2, 2);
            p.closeSubpath();
            ctx.addPath(p);
            ctx.fillPath();
            ctx.setFont(Font.boldSystemFont(12));
            ctx.setTextColor(Color.white());
            ctx.setTextAlignedCenter();
            ctx.drawTextInRect("Hi", new Rect(0, 0, 100, 20));
            ctx.drawText("there", new Point(5, 30));
            const w = new ListWidget();
            w.addImage(ctx.getImage());
            Script.setWidget(w);
            """
        )
        assertNull(result.error, result.error)
        val spec = canvas.specs.single()
        assertEquals(100f, spec.width)
        assertEquals(50f, spec.height)
        assertEquals(false, spec.opaque)
        assertTrue(spec.scale > 1f)
        assertEquals(
            listOf(
                CanvasOp.FillColor("#FFFF3B30"),
                CanvasOp.FillRect(Box(0f, 0f, 100f, 50f)),
                CanvasOp.StrokeColor("#FF00FF00"),
                CanvasOp.LineWidth(3f),
                CanvasOp.StrokeEllipse(Box(10f, 10f, 20f, 20f)),
                CanvasOp.FillPath(
                    listOf(
                        PathSegment.Move(0f, 50f),
                        PathSegment.Line(50f, 0f),
                        PathSegment.Quad(75f, 0f, 100f, 50f),
                        PathSegment.Curve(60f, 60f, 30f, 60f, 0f, 50f),
                        PathSegment.RoundedRect(Box(5f, 5f, 10f, 10f), 2f, 2f),
                        PathSegment.Close,
                    )
                ),
                CanvasOp.Font(12f, "bold"),
                CanvasOp.TextColor("#FFFFFFFF"),
                CanvasOp.TextAlign("center"),
                CanvasOp.Text("Hi", Box(0f, 0f, 100f, 20f)),
                CanvasOp.Text("there", Box(5f, 30f, null, null)),
            ),
            spec.ops,
        )
        assertTrue((result.widget!!.children.single() as ImageNode).src.startsWith("/cache/"))
    }

    @Test
    fun `fillPath and strokePath consume the added paths`() = runTest {
        run(
            """
            const ctx = new DrawContext();
            const a = new Path(); a.addRect(new Rect(0, 0, 1, 1));
            ctx.addPath(a); ctx.strokePath();
            ctx.fillPath();
            ctx.getImage();
            """
        )
        val ops = canvas.specs.single().ops
        assertEquals(listOf(CanvasOp.StrokePath(listOf(PathSegment.AddRect(Box(0f, 0f, 1f, 1f))))), ops)
    }

    @Test
    fun `images can be drawn into the context`() = runTest {
        run(
            """
            const img = await new Request("https://img").loadImage();
            const ctx = new DrawContext();
            ctx.drawImageInRect(img, new Rect(0, 0, 10, 10));
            ctx.drawImageAtPoint(img, new Point(5, 5));
            ctx.getImage();
            """
        )
        val ops = canvas.specs.single().ops
        val src = "/cache/${images.stored.first().runKey}/img_0.png"
        assertEquals(listOf(CanvasOp.DrawImage(src, Box(0f, 0f, 10f, 10f)), CanvasOp.DrawImage(src, Box(5f, 5f, null, null))), ops)
    }

    @Test
    fun `defaults follow Scriptable`() = runTest {
        run("""new DrawContext().getImage();""")
        val spec = canvas.specs.single()
        assertEquals(200f, spec.width)
        assertEquals(200f, spec.height)
        assertEquals(true, spec.opaque)
        assertEquals(1f, spec.scale)
    }

    @Test
    fun `addLines and addRects helpers`() = runTest {
        run(
            """
            const p = new Path();
            p.addLines([new Point(0, 0), new Point(1, 1), new Point(2, 0)]);
            p.addRects([new Rect(0, 0, 1, 1)]);
            p.addEllipse(new Rect(0, 0, 4, 4));
            const ctx = new DrawContext(); ctx.addPath(p); ctx.strokePath(); ctx.getImage();
            """
        )
        assertEquals(
            CanvasOp.StrokePath(
                listOf(
                    PathSegment.Move(0f, 0f), PathSegment.Line(1f, 1f), PathSegment.Line(2f, 0f),
                    PathSegment.AddRect(Box(0f, 0f, 1f, 1f)), PathSegment.Ellipse(Box(0f, 0f, 4f, 4f)),
                )
            ),
            canvas.specs.single().ops.single(),
        )
    }
}
