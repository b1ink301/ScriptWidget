package kr.b1ink.scriptwidget.widget.render

import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.WidgetNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kotlin.math.sqrt

/**
 * Bitmaps in one widget update share a memory budget: a launcher rejects RemoteViews whose bitmaps
 * exceed roughly screen width × height × 4 bytes × 1.5. With many images each one is decoded smaller.
 */
object BitmapBudget {
    /** Conservative share for images (text bitmaps and backgrounds need the rest). */
    const val TOTAL_BYTES = 6L * 1024 * 1024
    const val MIN_IMAGE_PX = 64

    fun imageCount(root: WidgetRoot): Int = root.children.sumOf(::count)

    private fun count(node: WidgetNode): Int = when (node) {
        is ImageNode -> 1
        is StackNode -> node.children.sumOf(::count)
        else -> 0
    }

    /** The longest side each image may be decoded at. */
    fun maxImagePx(root: WidgetRoot): Int {
        val images = imageCount(root).coerceAtLeast(1)
        val perImage = sqrt(TOTAL_BYTES / 4.0 / images).toInt()
        return perImage.coerceIn(MIN_IMAGE_PX, Styles.MAX_IMAGE_PX)
    }
}
