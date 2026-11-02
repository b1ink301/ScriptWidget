package kr.b1ink.scriptwidget.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.provideContent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kr.b1ink.scriptwidget.domain.model.DateNode
import kr.b1ink.scriptwidget.domain.model.RenderState
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.model.WidgetNode
import kr.b1ink.scriptwidget.domain.model.WidgetTree
import kr.b1ink.scriptwidget.widget.render.ScriptWidgetContent
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a regular test: draws the widget trees of the compatibility corpus as real RemoteViews and
 * saves them as PNGs, so renderer changes can be compared pixel by pixel. Does nothing unless the
 * `snapshots` argument is given; reads `files/snapshots/trees/<script>.json` and writes
 * `files/snapshots/png/<script>.png` in the test app. Run it with `scripts/compat-snapshots.sh`.
 */
@RunWith(AndroidJUnit4::class)
class CorpusSnapshotRender {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun render() = runBlocking {
        // Not assumeTrue: connected test reports count a skipped assumption as a failure.
        if (InstrumentationRegistry.getArguments().getString("snapshots") != "true") return@runBlocking
        val dir = File(context.filesDir, "snapshots")
        val trees = File(dir, "trees").listFiles { f -> f.extension == "json" }.orEmpty().sortedBy { it.name }
        val out = File(dir, "png").apply { deleteRecursively(); mkdirs() }
        val failures = mutableListOf<String>()

        for (file in trees) {
            val json = file.readText()
            // Runs that ended in an error have no tree to draw.
            if (!json.trimStart().startsWith("{")) continue
            val state = RenderState(scriptName = file.nameWithoutExtension, tree = WidgetTree.decode(json).let { it.copy(children = it.children.map(::frozen)) }, family = WidgetFamily.Medium)
            // A tree the renderer can't draw is a finding, not a reason to stop.
            val bitmap = runCatching { draw(state) }
                .onFailure { failures += "${file.nameWithoutExtension}: ${it::class.simpleName}: ${it.message}" }
                .getOrNull() ?: continue
            File(out, "${file.nameWithoutExtension}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        File(dir, "failures.txt").writeText(failures.joinToString("\n", postfix = "\n"))
    }

    private suspend fun draw(state: RenderState): Bitmap {
        val widget = object : GlanceAppWidget() {
            override suspend fun provideGlance(context: Context, id: GlanceId) = provideContent { ScriptWidgetContent(state) }
        }
        val views = widget.compose(context, size = MEDIUM)
        return withContext(Dispatchers.Main) {
            val view = views.apply(context, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            val width = (MEDIUM.width.value * density).toInt()
            val height = (MEDIUM.height.value * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
        }
    }

    /**
     * Relative and timer dates count live against the device clock, so they'd differ on every run;
     * they're drawn as a fixed label instead (other tests cover how they're drawn).
     */
    private fun frozen(node: WidgetNode): WidgetNode = when (node) {
        is DateNode -> if (node.style == "relative" || node.style == "timer") {
            TextNode("⏱ ${node.style}", color = node.color, size = node.size, weight = node.weight, align = node.align)
        } else {
            node
        }
        is StackNode -> node.copy(children = node.children.map(::frozen))
        else -> node
    }

    private companion object {
        /** Scriptable's medium widget on a 6.1" iPhone, so the PNGs line up with iOS screenshots. */
        val MEDIUM = DpSize(329.dp, 155.dp)
    }
}
