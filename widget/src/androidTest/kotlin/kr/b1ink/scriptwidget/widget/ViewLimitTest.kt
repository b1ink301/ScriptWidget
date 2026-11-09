package kr.b1ink.scriptwidget.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.widget.render.ScriptWidgetContent
import kr.b1ink.scriptwidget.widget.render.StackBitmap
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Glance draws at most 500 views per widget; bigger trees must still produce RemoteViews. */
@RunWith(AndroidJUnit4::class)
class ViewLimitTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val size = DpSize(329.dp, 155.dp)

    /** A year as a dot per day: 17 rows of 22, like the community "days left" widget. */
    private val year = StackNode(
        dir = "v",
        spacing = 2f,
        children = List(17) { row -> StackNode(spacing = 2f, children = List(22) { TextNode("●", color = if (row < 8) "#FFFFFFFF" else "#FF555555", size = 5f) }) },
    )

    private fun views(root: WidgetRoot): View = runBlocking {
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { ScriptWidgetContent(RenderState("s", root)) }
        }
        val remote = widget.compose(context, size = size)
        lateinit var view: View
        InstrumentationRegistry.getInstrumentation().runOnMainSync { view = remote.apply(context, FrameLayout(context)) }
        view
    }

    private fun View.count(): Int = 1 + if (this is ViewGroup) (0 until childCount).sumOf { getChildAt(it).count() } else 0

    private fun View.images(): Int = (if (this is ImageView) 1 else 0) + if (this is ViewGroup) (0 until childCount).sumOf { getChildAt(it).images() } else 0

    /** Image views Glance adds on its own, without any image in the tree. */
    private val baselineImages by lazy { views(WidgetRoot(children = listOf(TextNode("x")))).images() }

    @Test
    fun aGridOverTheViewLimitIsDrawnAsOneImage() {
        val root = WidgetRoot(children = listOf(year, SpacerNode(6f), TextNode("120 days left")))

        val view = views(root)

        assertTrue(view.count() < 50, "drew ${view.count()} views")
        assertEquals(baselineImages + 1, view.images())
    }

    @Test
    fun aGridWithinTheLimitKeepsItsViews() {
        val small = StackNode(children = List(9) { TextNode("●") })

        assertEquals(baselineImages, views(WidgetRoot(children = listOf(small))).images())
    }

    @Test
    fun theBitmapIsLaidOutLikeTheStacks() {
        val density = context.resources.displayMetrics.density
        val result = StackBitmap.render(year, density, night = false, maxWidthDp = 300f)

        // 22 dots and 21 gaps across, 17 lines and 16 gaps down: fits the widget, nothing cut off.
        val dot = StackBitmap.render(StackNode(children = listOf(TextNode("●", size = 5f))), density, night = false, maxWidthDp = 300f)
        // In pixels: gaps are rounded to whole pixels, so dp sums drift by a pixel or two per density.
        val gap = Math.round(2 * density)
        assertEquals(22 * dot.bitmap.width + 21 * gap, result.bitmap.width)
        assertEquals(17 * dot.bitmap.height + 16 * gap, result.bitmap.height)
        assertTrue(result.widthDp < 300f && result.heightDp < 155f, "${result.widthDp} x ${result.heightDp}")
        // Dots are drawn: some pixels are white (past days), some gray.
        val pixels = IntArray(result.bitmap.width * result.bitmap.height).also { result.bitmap.getPixels(it, 0, result.bitmap.width, 0, 0, result.bitmap.width, result.bitmap.height) }
        assertTrue(pixels.any { it == android.graphics.Color.WHITE })
    }

    @Test
    fun solidBackgroundsAndFixedSizesAreDrawn() {
        val density = context.resources.displayMetrics.density
        val cell = StackNode(bg = "#FFFF0000", size = listOf(10f, 10f), children = listOf(TextNode("", size = 1f)))
        val result = StackBitmap.render(StackNode(children = List(3) { cell }), density, night = false, maxWidthDp = 300f)

        assertEquals(30f, result.widthDp, 0.5f)
        assertEquals(10f, result.heightDp, 0.5f)
        assertEquals(android.graphics.Color.RED, result.bitmap.getPixel(result.bitmap.width / 2, result.bitmap.height / 2))
    }
}
