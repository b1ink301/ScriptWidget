package kr.b1ink.scriptwidget.widget

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.widget.render.BorderBitmap
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BorderBitmapTest {

    @Test
    fun drawsOnlyTheOutline() {
        val bitmap = BorderBitmap.create(Color.RED, strokePx = 4f, cornerRadiusPx = 0f, size = 64)

        assertEquals(64, bitmap.width)
        assertEquals(Color.RED, bitmap.getPixel(1, 32))
        assertEquals(Color.RED, bitmap.getPixel(32, 62))
        assertEquals(0, Color.alpha(bitmap.getPixel(32, 32)))
    }

    @Test
    fun roundedCornersLeaveTheCornerEmpty() {
        val bitmap = BorderBitmap.create(Color.RED, strokePx = 2f, cornerRadiusPx = 16f, size = 64)

        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        assertTrue(Color.alpha(bitmap.getPixel(1, 32)) > 200)
    }
}
