package kr.b1ink.scriptwidget.widget

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.Gradient
import kr.b1ink.scriptwidget.widget.render.GradientBitmap
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class GradientBitmapTest {

    private fun near(expected: Int, actual: Int) =
        assertTrue(
            listOf(Color::red, Color::green, Color::blue, Color::alpha).all { kotlin.math.abs(it(expected) - it(actual)) <= 8 },
            "expected ${Integer.toHexString(expected)} but was ${Integer.toHexString(actual)}",
        )

    @Test
    fun verticalByDefault() {
        val bitmap = GradientBitmap.create(Gradient(listOf("#FFFF0000", "#FF0000FF")), size = 64)

        assertEquals(64, bitmap.width)
        near(Color.RED, bitmap.getPixel(32, 0))
        near(Color.BLUE, bitmap.getPixel(32, 63))
        near(bitmap.getPixel(0, 10), bitmap.getPixel(63, 10))
    }

    @Test
    fun followsStartAndEndPoints() {
        val bitmap = GradientBitmap.create(
            Gradient(listOf("#FF00FF00", "#FFFFFFFF"), start = listOf(0f, 0f), end = listOf(1f, 0f)),
            size = 64,
        )

        near(Color.GREEN, bitmap.getPixel(0, 32))
        near(Color.WHITE, bitmap.getPixel(63, 32))
        near(bitmap.getPixel(20, 0), bitmap.getPixel(20, 63))
    }

    @Test
    fun honorsLocations() {
        val bitmap = GradientBitmap.create(
            Gradient(listOf("#FFFF0000", "#FF0000FF"), locations = listOf(0.5f, 1f)),
            size = 64,
        )

        near(Color.RED, bitmap.getPixel(32, 20))
    }
}
