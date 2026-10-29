package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kr.b1ink.scriptwidget.data.engine.SymbolRenderer
import kr.b1ink.scriptwidget.data.symbols.MaterialIcons
import java.io.ByteArrayOutputStream

/** Draws Material Icons glyphs (bundled font, Apache-2.0) in black, like Scriptable's template symbols. */
class MaterialSymbolRenderer(context: Context) : SymbolRenderer {
    private val typeface: Typeface by lazy {
        Typeface.createFromAsset(context.applicationContext.assets, "fonts/MaterialIcons-Regular.ttf")
    }

    override fun render(materialName: String, sizePx: Int, weight: String): ByteArray? {
        val codepoint = MaterialIcons.codepointOf(materialName) ?: return null
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = this@MaterialSymbolRenderer.typeface
            color = Color.BLACK
            textSize = sizePx.toFloat()
            textAlign = Paint.Align.CENTER
            // The font has a single weight; approximate heavier weights.
            isFakeBoldText = weight in setOf("semibold", "bold", "heavy", "black")
        }
        val glyph = String(Character.toChars(codepoint))
        val baseline = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
        Canvas(bitmap).drawText(glyph, sizePx / 2f, baseline, paint)
        return ByteArrayOutputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            bitmap.recycle()
            it.toByteArray()
        }
    }
}
