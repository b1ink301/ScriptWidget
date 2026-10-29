package kr.b1ink.scriptwidget.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kr.b1ink.scriptwidget.domain.model.Gradient
import android.graphics.Color as AndroidColor

/**
 * Renders a [Gradient] into a square bitmap. Gradient points are unit coordinates, so the bitmap
 * can be stretched (ContentScale.FillBounds) to any element size without distortion of the stops.
 */
object GradientBitmap {
    const val DEFAULT_SIZE = 128

    fun create(gradient: Gradient, size: Int = DEFAULT_SIZE): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val max = (size - 1).toFloat()
        val paint = Paint().apply {
            shader = LinearGradient(
                gradient.start.getOrElse(0) { 0f } * max,
                gradient.start.getOrElse(1) { 0f } * max,
                gradient.end.getOrElse(0) { 0f } * max,
                gradient.end.getOrElse(1) { 1f } * max,
                gradient.colors.map(::parse).toIntArray(),
                gradient.stops().toFloatArray(),
                Shader.TileMode.CLAMP,
            )
        }
        Canvas(bitmap).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bitmap
    }

    private fun parse(color: String): Int = runCatching { AndroidColor.parseColor(ThemedColor.pick(color, night = false)) }.getOrDefault(AndroidColor.TRANSPARENT)
}
