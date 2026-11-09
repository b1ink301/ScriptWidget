package kr.b1ink.scriptwidget.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetNode
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws a stack that [ViewBudget.canRasterize] accepts (text, fixed spacers, plain stacks) into one
 * bitmap, laid out like SwiftUI stacks: each stack is as large as its content (or its fixed size),
 * rows align children by the stack's alignment and columns align them to the leading edge.
 */
object StackBitmap {
    /** Longest side of the bitmap; one bitmap at this size stays well within a widget's memory budget. */
    const val MAX_PX = 1_024

    /** The bitmap and the size to show it at, in dp. */
    class Result(val bitmap: Bitmap, val widthDp: Float, val heightDp: Float)

    fun render(stack: StackNode, density: Float, night: Boolean, maxWidthDp: Float): Result {
        val natural = measure(stack, Scale(density, night, maxWidthDp))
        // Grids larger than the bitmap limit are drawn at a lower resolution, not cut off.
        val longest = max(natural.width, natural.height).coerceAtLeast(1)
        val scale = if (longest > MAX_PX) Scale(density * MAX_PX / longest, night, maxWidthDp) else Scale(density, night, maxWidthDp)
        val box = if (scale.density == density) natural else measure(stack, scale)
        val bitmap = Bitmap.createBitmap(box.width.coerceAtLeast(1), box.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        box.draw(Canvas(bitmap), 0f, 0f)
        return Result(bitmap, box.width / scale.density, box.height / scale.density)
    }

    private class Scale(val density: Float, val night: Boolean, val maxWidthDp: Float) {
        fun px(dp: Float) = (dp * density).roundToInt()
    }

    private class Box(val width: Int, val height: Int, val draw: (Canvas, Float, Float) -> Unit)

    private fun measure(node: WidgetNode, scale: Scale): Box = when (node) {
        is TextNode -> {
            // Text keeps its own width inside a stack; alignment applies within the stack's frame.
            val bitmap = TextBitmap.render(node.copy(align = "left"), scale.px(scale.maxWidthDp), scale.density, scale.night)
            Box(bitmap.width, bitmap.height) { canvas, x, y -> canvas.drawBitmap(bitmap, x, y, null) }
        }
        is StackNode -> stack(node, scale)
        // Spacers are handled by the stack (their length runs along its axis); nothing else gets here.
        else -> Box(0, 0) { _, _, _ -> }
    }

    private fun stack(node: StackNode, scale: Scale): Box {
        val horizontal = node.dir != "v"
        val gap = scale.px(node.spacing).toFloat()
        val pad = node.padding.orEmpty().map { scale.px(it).toFloat() }
        val top = pad.getOrElse(0) { 0f }
        val left = pad.getOrElse(1) { 0f }
        val bottom = pad.getOrElse(2) { 0f }
        val right = pad.getOrElse(3) { 0f }

        val children = node.children.map { child ->
            if (child is SpacerNode) {
                val length = scale.px(child.length ?: 0f)
                if (horizontal) Box(length, 0) { _, _, _ -> } else Box(0, length) { _, _, _ -> }
            } else {
                measure(child, scale)
            }
        }
        val gaps = gap * (children.size - 1).coerceAtLeast(0)
        val contentWidth = if (horizontal) children.sumOf { it.width } + gaps else (children.maxOfOrNull { it.width } ?: 0).toFloat()
        val contentHeight = if (horizontal) (children.maxOfOrNull { it.height } ?: 0).toFloat() else children.sumOf { it.height } + gaps

        // A fixed size wins; the content is centered in it, as in SwiftUI's frame().
        val fixedWidth = node.size?.getOrNull(0)?.takeIf { it > 0 }?.let(scale::px)
        val fixedHeight = node.size?.getOrNull(1)?.takeIf { it > 0 }?.let(scale::px)
        val width = fixedWidth ?: (contentWidth + left + right).roundToInt()
        val height = fixedHeight ?: (contentHeight + top + bottom).roundToInt()
        val innerWidth = width - left - right
        val innerHeight = height - top - bottom
        val originX = left + (innerWidth - contentWidth) / 2
        val originY = top + (innerHeight - contentHeight) / 2

        val background = node.bg?.let { Styles.color(it, Color.Transparent, scale.night).toArgb() }
        val radius = node.cornerRadius * scale.density
        return Box(width, height) { canvas, x, y ->
            canvas.save()
            canvas.translate(x, y)
            val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
            if (background != null) canvas.drawRoundRect(bounds, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background })
            canvas.clipRect(bounds)
            var cursor = 0f
            children.forEach { child ->
                if (horizontal) {
                    val dy = when (node.align) {
                        "top" -> 0f
                        "bottom" -> contentHeight - child.height
                        else -> (contentHeight - child.height) / 2
                    }
                    child.draw(canvas, originX + cursor, originY + dy)
                    cursor += child.width + gap
                } else {
                    child.draw(canvas, originX, originY + cursor)
                    cursor += child.height + gap
                }
            }
            canvas.restore()
        }
    }
}
