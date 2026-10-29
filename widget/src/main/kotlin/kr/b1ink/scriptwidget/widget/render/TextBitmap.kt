package kr.b1ink.scriptwidget.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kr.b1ink.scriptwidget.domain.model.TextNode
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import android.graphics.Color as AndroidColor

/**
 * Draws a [TextNode] into a bitmap for styles Glance lacks: every font weight, custom font files,
 * shadows and `minimumScaleFactor` (shrinks to fit [maxWidthPx] before truncating).
 */
object TextBitmap {
    /** Taller than the largest widget on any phone. */
    const val MAX_HEIGHT_PX = 2_048

    private val WEIGHTS = mapOf(
        "ultraLight" to 100, "thin" to 200, "light" to 300, "regular" to 400, "medium" to 500,
        "semibold" to 600, "bold" to 700, "heavy" to 800, "black" to 900,
    )

    fun render(node: TextNode, maxWidthPx: Int, density: Float, night: Boolean = false): Bitmap {
        val width = max(1, maxWidthPx)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            typeface = typeface(node)
            color = parse(node.color?.let { ThemedColor.pick(it, night) }, AndroidColor.WHITE).withOpacity(node.opacity)
            node.shadowColor?.let { shadow ->
                setShadowLayer(
                    max(node.shadowRadius * density, 0.01f),
                    node.shadowOffset.getOrElse(0) { 0f } * density,
                    node.shadowOffset.getOrElse(1) { 0f } * density,
                    parse(ThemedColor.pick(shadow, night), AndroidColor.BLACK),
                )
            }
        }

        // Shrink in small steps until the text fits on its line limit (or the minimum scale is reached).
        var scale = 1f
        var layout: StaticLayout
        while (true) {
            paint.textSize = node.size * density * scale
            layout = layout(node, paint, width)
            val fits = node.lineLimit <= 0 || !truncated(layout)
            if (fits || scale <= node.minimumScaleFactor + 0.001f) break
            scale = max(node.minimumScaleFactor, scale - 0.05f)
        }

        val pad = shadowPadding(node, density)
        val contentWidth = (0 until layout.lineCount).maxOfOrNull { layout.getLineWidth(it) } ?: 0f
        val bitmapWidth = (ceil(if (node.align == "left") contentWidth else width.toFloat()).toInt() + pad.horizontal).coerceAtMost(width + pad.horizontal)
        // Text taller than any widget is cut off rather than allocating a huge bitmap.
        val height = (layout.height + pad.vertical).coerceIn(1, MAX_HEIGHT_PX)
        val bitmap = Bitmap.createBitmap(max(1, bitmapWidth), height, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            translate(pad.left.toFloat(), pad.top.toFloat())
            layout.draw(this)
        }
        return bitmap
    }

    private fun layout(node: TextNode, paint: TextPaint, width: Int): StaticLayout {
        val alignment = when (node.align) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "right" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        return StaticLayout.Builder.obtain(node.text, 0, node.text.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(false)
            .apply {
                if (node.lineLimit > 0) {
                    setMaxLines(node.lineLimit)
                    setEllipsize(TextUtils.TruncateAt.END)
                }
            }
            .build()
    }

    private fun truncated(layout: StaticLayout): Boolean =
        (0 until layout.lineCount).any { layout.getEllipsisCount(it) > 0 }

    private fun typeface(node: TextNode): Typeface {
        val base = node.fontFile?.let { path -> runCatching { Typeface.createFromFile(File(path)) }.getOrNull() }
            ?: when (TextPlan.familyOf(node.font)) {
                TextPlan.Family.Monospace -> Typeface.MONOSPACE
                TextPlan.Family.Serif -> Typeface.SERIF
                TextPlan.Family.SansSerif -> Typeface.SANS_SERIF
            }
        val weight = WEIGHTS[node.weight] ?: 400
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(base, weight, node.italic)
        } else {
            val style = when {
                weight >= 600 && node.italic -> Typeface.BOLD_ITALIC
                weight >= 600 -> Typeface.BOLD
                node.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            Typeface.create(base, style)
        }
    }

    private data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val horizontal get() = left + right
        val vertical get() = top + bottom
    }

    /** Room around the text so the shadow isn't clipped. */
    private fun shadowPadding(node: TextNode, density: Float): Padding {
        if (node.shadowColor == null) return Padding(0, 0, 0, 0)
        val blur = node.shadowRadius * density
        val dx = node.shadowOffset.getOrElse(0) { 0f } * density
        val dy = node.shadowOffset.getOrElse(1) { 0f } * density
        fun px(v: Float) = ceil(max(0f, v)).toInt()
        return Padding(px(blur - dx), px(blur - dy), px(blur + dx), px(blur + dy))
    }

    private fun parse(color: String?, default: Int): Int =
        color?.let { runCatching { AndroidColor.parseColor(it) }.getOrNull() } ?: default

    private fun Int.withOpacity(opacity: Float): Int =
        AndroidColor.argb((AndroidColor.alpha(this) * opacity.coerceIn(0f, 1f)).toInt(), AndroidColor.red(this), AndroidColor.green(this), AndroidColor.blue(this))
}
