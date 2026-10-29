package kr.b1ink.scriptwidget.data.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kr.b1ink.scriptwidget.data.engine.Box
import kr.b1ink.scriptwidget.data.engine.CanvasOp
import kr.b1ink.scriptwidget.data.engine.CanvasRenderer
import kr.b1ink.scriptwidget.data.engine.CanvasSpec
import kr.b1ink.scriptwidget.data.engine.PathSegment
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Replays a recorded `DrawContext` on an Android [Canvas]. Coordinates are points; pixels = points × scale. */
class AndroidCanvasRenderer : CanvasRenderer {

    override fun render(spec: CanvasSpec, imageBytes: (String) -> ByteArray?): ByteArray {
        val widthPx = ceil(spec.width * spec.scale).toInt().coerceIn(1, MAX_PX)
        val heightPx = ceil(spec.height * spec.scale).toInt().coerceIn(1, MAX_PX)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (spec.opaque) canvas.drawColor(Color.BLACK)
        canvas.scale(spec.scale, spec.scale)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.BLACK; strokeWidth = 1f }
        val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 17f }
        var align = Layout.Alignment.ALIGN_NORMAL

        for (op in spec.ops) when (op) {
            is CanvasOp.FillColor -> fill.color = parse(op.color)
            is CanvasOp.StrokeColor -> stroke.color = parse(op.color)
            is CanvasOp.LineWidth -> stroke.strokeWidth = op.width
            is CanvasOp.Font -> {
                text.textSize = op.size
                text.typeface = if (op.weight in BOLD_WEIGHTS) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            is CanvasOp.TextColor -> text.color = parse(op.color)
            is CanvasOp.TextAlign -> align = when (op.align) {
                "center" -> Layout.Alignment.ALIGN_CENTER
                "right" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }
            is CanvasOp.FillRect -> canvas.drawRect(op.rect.toRectF(), fill)
            is CanvasOp.FillEllipse -> canvas.drawOval(op.rect.toRectF(), fill)
            is CanvasOp.StrokeRect -> canvas.drawRect(op.rect.toRectF(), stroke)
            is CanvasOp.StrokeEllipse -> canvas.drawOval(op.rect.toRectF(), stroke)
            is CanvasOp.FillPath -> canvas.drawPath(op.segments.toPath(), fill)
            is CanvasOp.StrokePath -> canvas.drawPath(op.segments.toPath(), stroke)
            is CanvasOp.Text -> drawText(canvas, op, text, align, spec.width)
            is CanvasOp.DrawImage -> imageBytes(op.src)
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?.let { image ->
                    val r = op.rect
                    val dest = RectF(r.x, r.y, r.x + (r.w ?: image.width.toFloat()), r.y + (r.h ?: image.height.toFloat()))
                    canvas.drawBitmap(image, null, dest, Paint(Paint.FILTER_BITMAP_FLAG))
                }
        }

        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
            it.toByteArray()
        }
    }

    /** Text starts at the top-left of the point/rect and wraps inside the rect width, like Scriptable. */
    private fun drawText(canvas: Canvas, op: CanvasOp.Text, paint: TextPaint, align: Layout.Alignment, canvasWidth: Float) {
        val r = op.rect
        val width = (r.w ?: (canvasWidth - r.x)).coerceAtLeast(1f).toInt()
        val layout = StaticLayout.Builder.obtain(op.text, 0, op.text.length, paint, width).setAlignment(align).build()
        canvas.save()
        canvas.translate(r.x, r.y)
        r.h?.let { canvas.clipRect(0f, 0f, width.toFloat(), it) }
        layout.draw(canvas)
        canvas.restore()
    }

    private fun List<PathSegment>.toPath(): Path = Path().also { path ->
        for (segment in this) when (segment) {
            is PathSegment.Move -> path.moveTo(segment.x, segment.y)
            is PathSegment.Line -> path.lineTo(segment.x, segment.y)
            is PathSegment.Quad -> path.quadTo(segment.cx, segment.cy, segment.x, segment.y)
            is PathSegment.Curve -> path.cubicTo(segment.c1x, segment.c1y, segment.c2x, segment.c2y, segment.x, segment.y)
            is PathSegment.AddRect -> path.addRect(segment.rect.toRectF(), Path.Direction.CW)
            is PathSegment.RoundedRect -> path.addRoundRect(segment.rect.toRectF(), segment.rx, segment.ry, Path.Direction.CW)
            is PathSegment.Ellipse -> path.addOval(segment.rect.toRectF(), Path.Direction.CW)
            PathSegment.Close -> path.close()
        }
    }

    private fun Box.toRectF() = RectF(x, y, x + (w ?: 0f), y + (h ?: 0f))

    private fun parse(color: String) = runCatching { Color.parseColor(color) }.getOrDefault(Color.BLACK)

    private companion object {
        const val MAX_PX = 2048
        val BOLD_WEIGHTS = setOf("semibold", "bold", "heavy", "black")
    }
}
