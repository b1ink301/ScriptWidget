package kr.b1ink.scriptwidget.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * A square bitmap containing only a (rounded) rectangle outline. Glance has no border modifier, so
 * stacks without a solid background get this stretched behind them. Stretching makes it approximate:
 * stroke width and corners scale with the stack's aspect ratio.
 */
object BorderBitmap {
    const val DEFAULT_SIZE = 128

    fun create(color: Int, strokePx: Float, cornerRadiusPx: Float, size: Int = DEFAULT_SIZE): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = color
            strokeWidth = strokePx
        }
        val inset = strokePx / 2f
        Canvas(bitmap).drawRoundRect(RectF(inset, inset, size - inset, size - inset), cornerRadiusPx, cornerRadiusPx, paint)
        return bitmap
    }
}
