package kr.b1ink.scriptwidget.data.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.data.engine.Box
import kr.b1ink.scriptwidget.data.engine.CanvasOp
import kr.b1ink.scriptwidget.data.engine.CanvasSpec
import kr.b1ink.scriptwidget.data.engine.PathSegment
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidCanvasRendererTest {
    private val renderer = AndroidCanvasRenderer()

    private fun render(spec: CanvasSpec, images: Map<String, ByteArray> = emptyMap()): Bitmap {
        val png = renderer.render(spec) { images[it] }
        return BitmapFactory.decodeByteArray(png, 0, png.size)
    }

    @Test
    fun opaqueCanvasesStartBlackAndTransparentOnesClear() {
        assertEquals(Color.BLACK, render(CanvasSpec(10f, 10f, opaque = true, ops = emptyList())).getPixel(5, 5))
        assertEquals(0, Color.alpha(render(CanvasSpec(10f, 10f, opaque = false, ops = emptyList())).getPixel(5, 5)))
    }

    @Test
    fun scaleMultipliesThePixelSize() {
        val bitmap = render(CanvasSpec(10f, 20f, scale = 3f, opaque = false, ops = emptyList()))
        assertEquals(30, bitmap.width)
        assertEquals(60, bitmap.height)
    }

    @Test
    fun fillsRectsEllipsesAndPaths() {
        val bitmap = render(
            CanvasSpec(
                100f, 100f, opaque = false,
                ops = listOf(
                    CanvasOp.FillColor("#FFFF0000"),
                    CanvasOp.FillRect(Box(0f, 0f, 50f, 50f)),
                    CanvasOp.FillColor("#FF0000FF"),
                    CanvasOp.FillEllipse(Box(50f, 50f, 50f, 50f)),
                    CanvasOp.FillColor("#FF00FF00"),
                    CanvasOp.FillPath(listOf(PathSegment.Move(50f, 0f), PathSegment.Line(100f, 0f), PathSegment.Line(100f, 50f), PathSegment.Close)),
                ),
            )
        )
        assertEquals(Color.RED, bitmap.getPixel(25, 25))
        assertEquals(Color.BLUE, bitmap.getPixel(75, 75))
        assertEquals(Color.GREEN, bitmap.getPixel(95, 5))
        assertEquals(0, Color.alpha(bitmap.getPixel(55, 45)))
        assertEquals(0, Color.alpha(bitmap.getPixel(52, 52)))
    }

    @Test
    fun strokesUseLineWidthAndColor() {
        val bitmap = render(
            CanvasSpec(
                40f, 40f, opaque = false,
                ops = listOf(CanvasOp.StrokeColor("#FFFF0000"), CanvasOp.LineWidth(4f), CanvasOp.StrokeRect(Box(10f, 10f, 20f, 20f))),
            )
        )
        assertEquals(Color.RED, bitmap.getPixel(10, 20))
        assertEquals(0, Color.alpha(bitmap.getPixel(20, 20)))
    }

    @Test
    fun drawsTextAndImages() {
        val red = ByteArrayOutputStream().also {
            Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }.compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val bitmap = render(
            CanvasSpec(
                100f, 40f, opaque = false,
                ops = listOf(
                    CanvasOp.DrawImage("/red.png", Box(0f, 0f, 20f, 20f)),
                    CanvasOp.TextColor("#FF0000FF"),
                    CanvasOp.Font(20f, "bold"),
                    CanvasOp.Text("WWW", Box(30f, 0f, 70f, 40f)),
                ),
            ),
            images = mapOf("/red.png" to red),
        )
        assertEquals(Color.RED, bitmap.getPixel(10, 10))
        val blue = (30 until 100).sumOf { x -> (0 until 40).count { y -> Color.blue(bitmap.getPixel(x, y)) > 200 && Color.alpha(bitmap.getPixel(x, y)) > 200 } }
        assertTrue(blue > 30, "text pixels: $blue")
    }
}
