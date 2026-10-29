package kr.b1ink.scriptwidget.widget.render

import androidx.compose.runtime.staticCompositionLocalOf

/** Width (dp) left for an element's content once its own padding is applied; used to size text bitmaps. */
object AvailableWidth {
    fun inside(parent: Float, padding: List<Float>?, fixedWidth: Float? = null): Float {
        val outer = fixedWidth?.takeIf { it > 0f } ?: parent
        val horizontal = (padding?.getOrNull(1) ?: 0f) + (padding?.getOrNull(3) ?: 0f)
        return (outer - horizontal).coerceAtLeast(0f)
    }
}

/** Available content width in dp for the element being composed (NaN = unknown, use the widget width). */
val LocalAvailableWidth = staticCompositionLocalOf { Float.NaN }

/** Whether the enclosing column spans the available width (see StackLayout.columnFillsWidth). */
val LocalColumnFillsWidth = staticCompositionLocalOf { true }

/** Longest side images may be decoded at in this widget (see [BitmapBudget]). */
val LocalImageMaxPx = staticCompositionLocalOf { Styles.MAX_IMAGE_PX }
