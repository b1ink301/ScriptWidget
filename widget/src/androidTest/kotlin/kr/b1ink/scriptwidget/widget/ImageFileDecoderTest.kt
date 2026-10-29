package kr.b1ink.scriptwidget.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.widget.render.ImageFileDecoder
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class ImageFileDecoderTest {
    private val dir = ApplicationProvider.getApplicationContext<Context>().cacheDir

    private fun pngFile(width: Int, height: Int): File = File(dir, "decoder-test-${width}x$height.png").apply {
        outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun decodesWithinTheRequestedBound() {
        val bitmap = ImageFileDecoder.decode(pngFile(400, 200).absolutePath, maxPx = 100)!!

        assertEquals(100, bitmap.width)
        assertEquals(50, bitmap.height)
    }

    @Test
    fun keepsSmallImages() {
        val bitmap = ImageFileDecoder.decode(pngFile(30, 20).absolutePath, maxPx = 100)!!

        assertEquals(30, bitmap.width)
    }

    @Test
    fun missingFileDecodesToNull() {
        assertNull(ImageFileDecoder.decode("/no/such/file.png", maxPx = 100))
    }
}
