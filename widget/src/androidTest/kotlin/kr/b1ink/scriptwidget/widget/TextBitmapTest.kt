package kr.b1ink.scriptwidget.widget

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.widget.render.TextBitmap
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class TextBitmapTest {
    private val density = 2f

    private fun Bitmap.count(predicate: (Int) -> Boolean): Int {
        var n = 0
        for (y in 0 until height) for (x in 0 until width) if (predicate(getPixel(x, y))) n++
        return n
    }

    private fun opaque(c: Int) = Color.alpha(c) > 200

    @Test
    fun drawsTheTextInItsColorWithinTheWidth() {
        val bitmap = TextBitmap.render(TextNode("Hello", size = 20f, color = "#FFFF0000", weight = "semibold"), maxWidthPx = 500, density = density)

        assertTrue(bitmap.width in 1..500 && bitmap.height > 0)
        assertTrue(bitmap.count { Color.red(it) > 200 && Color.green(it) < 60 && opaque(it) } > 20)
    }

    @Test
    fun heavierWeightsUseMoreInk() {
        fun ink(weight: String) = TextBitmap.render(TextNode("Weight", size = 24f, color = "#FF000000", weight = weight), 800, density).count(::opaque)

        assertTrue(ink("thin") < ink("regular"))
        assertTrue(ink("regular") < ink("black"))
    }

    @Test
    fun shadowIsDrawnInItsColor() {
        val node = TextNode("Shadow", size = 24f, color = "#FFFFFFFF", shadowColor = "#FF0000FF", shadowRadius = 2f, shadowOffset = listOf(2f, 2f))

        val bitmap = TextBitmap.render(node, 800, density)

        assertTrue(bitmap.count { Color.blue(it) > 120 && Color.red(it) < 80 && Color.alpha(it) > 60 } > 20)
    }

    @Test
    fun minimumScaleFactorShrinksLongTextToFit() {
        val long = "This line is far too long to fit"
        val fixed = TextBitmap.render(TextNode(long, size = 20f, lineLimit = 1), maxWidthPx = 200, density = density)
        val scaled = TextBitmap.render(TextNode(long, size = 20f, lineLimit = 1, minimumScaleFactor = 0.3f), maxWidthPx = 200, density = density)

        assertTrue(scaled.width <= 200)
        assertTrue(scaled.height < fixed.height, "scaled ${scaled.height} vs ${fixed.height}")
    }

    @Test
    fun lineLimitCapsTheHeight() {
        val text = (1..20).joinToString(" ") { "word$it" }
        val free = TextBitmap.render(TextNode(text, size = 16f), 200, density)
        val twoLines = TextBitmap.render(TextNode(text, size = 16f, lineLimit = 2), 200, density)

        assertTrue(twoLines.height < free.height)
    }

    @Test
    fun usesTheFontFile() {
        val mono = listOf("CutiveMono.ttf", "DroidSansMono.ttf", "NotoSansMono-Regular.ttf")
            .map { File("/system/fonts", it) }.firstOrNull(File::exists)
        assumeTrue("no monospaced system font file", mono != null)

        fun width(text: String, file: String?) = TextBitmap.render(TextNode(text, size = 20f, font = "x", fontFile = file), 2000, density).width

        // In a monospaced font narrow and wide letters take the same room.
        val narrow = width("iiiiiiii", mono!!.path)
        val wide = width("WWWWWWWW", mono.path)
        assertTrue(kotlin.math.abs(narrow - wide) <= 4, "mono $narrow vs $wide")
        assertTrue(width("iiiiiiii", null) < width("WWWWWWWW", null))
    }

    @Test
    fun dynamicTextColorFollowsTheTheme() {
        val node = TextNode("Theme", size = 24f, color = "#FFFF0000/#FF0000FF")

        val day = TextBitmap.render(node, 400, density, night = false)
        val night = TextBitmap.render(node, 400, density, night = true)

        assertTrue(day.count { Color.red(it) > 200 && Color.blue(it) < 60 && opaque(it) } > 20)
        assertTrue(night.count { Color.blue(it) > 200 && Color.red(it) < 60 && opaque(it) } > 20)
    }

    // --- memory: long text can't produce a huge bitmap ---

    @Test
    fun veryLongTextIsCappedInHeight() {
        val text = (1..400).joinToString("\n") { "line $it" }
        val bitmap = TextBitmap.render(TextNode(text, size = 20f, color = "#FF000000", weight = "semibold", lineLimit = 0), 400, density)

        assertTrue(bitmap.height <= TextBitmap.MAX_HEIGHT_PX, "height ${bitmap.height}")
    }
}
