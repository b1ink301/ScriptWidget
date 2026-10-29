package kr.b1ink.scriptwidget.widget.render

import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetNode

/** Layout rules that RemoteViews doesn't apply by itself. */
object StackLayout {
    /**
     * Scriptable stacks containing a flexible spacer grow along their axis (so the spacer can push
     * content apart). RemoteViews rows/columns only wrap their content, so the renderer fills that
     * axis explicitly — unless the script gave the stack a fixed size on that axis.
     */
    fun fillsMainAxis(stack: StackNode): Boolean {
        val fixed = stack.size?.getOrNull(if (stack.dir == "v") 1 else 0)?.let { it > 0 } ?: false
        return !fixed && stack.children.any { it is SpacerNode && it.length == null }
    }

    /**
     * Whether a row child should take a share of the row's width, like SwiftUI's HStack splitting the
     * space between flexible children: a stack that fills its width, or contains one (e.g. a column
     * of rows with flexible spacers). Without this the first such child takes the whole row.
     */
    /**
     * Whether a column spans more than its widest child, so children can be centered / right-aligned
     * across it. A wrap-content column (SwiftUI VStack sized by its content) must not stretch its
     * aligned children: that would make it grab the rest of the row.
     */
    fun columnFillsWidth(stack: StackNode, weighted: Boolean): Boolean =
        weighted || (stack.size?.getOrNull(0) ?: 0f) > 0f || wantsWidth(stack)

    /** Like [wantsWidth] for the vertical axis: a stack that fills its height, or contains one. */
    fun wantsHeight(node: WidgetNode): Boolean {
        if (node !is StackNode) return false
        if ((node.size?.getOrNull(1) ?: 0f) > 0f) return false
        if (node.dir == "v" && fillsMainAxis(node)) return true
        return node.children.any(::wantsHeight)
    }

    fun wantsWidth(node: WidgetNode): Boolean {
        if (node !is StackNode) return false
        if ((node.size?.getOrNull(0) ?: 0f) > 0f) return false
        if (node.dir != "v" && fillsMainAxis(node)) return true
        return node.children.any(::wantsWidth)
    }

    /** RemoteViews draws at most this many children in one row or column and silently drops the rest. */
    const val MAX_CHILDREN = 10

    /** Children of one container: drawn directly, plus the rest nested in an inner container. */
    data class Nested<T>(val direct: List<T>, val rest: Nested<T>?) {
        fun flatten(): List<T> = direct + (rest?.flatten() ?: emptyList())
    }

    /** Keeps every container within [MAX_CHILDREN]: the last slot holds a nested container for the rest. */
    fun <T> nest(items: List<T>, max: Int = MAX_CHILDREN): Nested<T> =
        if (items.size <= max) Nested(items, null)
        else Nested(items.take(max - 1), nest(items.drop(max - 1), max))

    /**
     * SwiftUI truncates a one-line text so the items after a flexible spacer stay visible; a Glance
     * text keeps its full width and pushes them out. For rows like [rank, title, spacer, source] the
     * title takes the free space instead of the spacer (same look, but it truncates).
     * @return the index of that text, or null.
     */
    fun stretchedText(stack: StackNode): Int? {
        if (stack.dir == "v") return null
        val flexible = stack.children.withIndex().filter { (_, c) -> c is SpacerNode && c.length == null }
        val spacer = flexible.singleOrNull()?.index ?: return null
        val before = stack.children.getOrNull(spacer - 1) as? TextNode ?: return null
        return if (before.lineLimit >= 1) spacer - 1 else null
    }
}
