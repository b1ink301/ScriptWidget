package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class MaterialSymbolRendererTest {
    private val renderer = MaterialSymbolRenderer(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun rendersAGlyphAsASquarePng() {
        val png = assertNotNull(renderer.render("wb_sunny", sizePx = 96, weight = "regular"))
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)

        assertEquals(96, bitmap.width)
        assertEquals(96, bitmap.height)
        val opaque = (0 until 96 step 4).sumOf { x -> (0 until 96 step 4).count { y -> Color.alpha(bitmap.getPixel(x, y)) > 128 } }
        assertTrue(opaque > 20, "glyph pixels: $opaque")
    }

    @Test
    fun unknownIconsRenderNothing() {
        assertNull(renderer.render("not_a_material_icon", sizePx = 48, weight = "regular"))
    }
}
