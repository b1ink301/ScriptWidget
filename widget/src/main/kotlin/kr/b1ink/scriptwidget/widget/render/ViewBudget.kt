package kr.b1ink.scriptwidget.widget.render

import kr.b1ink.scriptwidget.domain.model.DateNode
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot

/**
 * Glance draws a widget with at most 500 views and throws past that, so the widget isn't drawn at
 * all. Scripts that lay out a dot per day (a year in 365 dots) go over it; their largest plain
 * stacks are drawn as one bitmap each ([StackBitmap]) until the rest fits.
 */
object ViewBudget {
    /** Below Glance's 500, leaving room for views it adds itself (click targets, sizing boxes). */
    const val MAX_VIEWS = 450

    /** Views around the script's tree (content box, root column, ...); fitted to where Glance gave up on a device. */
    private const val ROOT_VIEWS = 3

    /** The views Glance needs for [node], with the stacks in [rasterized] drawn as one image each. */
    fun views(node: WidgetNode, rasterized: Set<StackNode> = emptySet()): Int = when (node) {
        is WidgetRoot -> ROOT_VIEWS + container(node.children, node.spacing, rasterized)
        is StackNode -> when {
            node in rasterized -> 1
            else -> 1 + container(node.children, node.spacing, rasterized) + if (hasBorder(node)) 1 else 0
        }
        is TextNode, is DateNode, is ImageNode, is SpacerNode -> 1
    }

    /** Children, the gaps `spacing` adds between them, and inner containers past ten children. */
    private fun container(children: List<WidgetNode>, spacing: Float, rasterized: Set<StackNode>): Int {
        val gaps = if (spacing > 0) (children.size - 1).coerceAtLeast(0) else 0
        return children.sumOf { views(it, rasterized) } + gaps + nestedContainers(children.size + gaps)
    }

    /** Matches [StackLayout.nest]: each inner container holds the slots past the first nine. */
    private fun nestedContainers(slots: Int): Int =
        if (slots <= StackLayout.MAX_CHILDREN) 0 else 1 + nestedContainers(slots - (StackLayout.MAX_CHILDREN - 1))

    /** The stacks to draw as bitmaps so [root] fits; empty when it already does. */
    fun rasterized(root: WidgetRoot): Set<StackNode> {
        val chosen = mutableSetOf<StackNode>()
        while (views(root, chosen) >= MAX_VIEWS) {
            // The stack whose bitmap saves the most views; an outer stack beats the rows inside it.
            val best = candidates(root.children, chosen).maxByOrNull { views(it, chosen) } ?: break
            if (views(best, chosen) <= 1) break
            chosen += best
        }
        return chosen
    }

    private fun candidates(nodes: List<WidgetNode>, chosen: Set<StackNode>): List<StackNode> = nodes.flatMap { node ->
        when {
            node !is StackNode || node in chosen -> emptyList()
            canRasterize(node) -> listOf(node)
            else -> candidates(node.children, chosen)
        }
    }

    /**
     * Only what a bitmap can show the same way: text, fixed spacers and stacks with a solid (or no)
     * background. Taps need their own views, images their own decoding, and flexible spacers the
     * space Glance gives the stack.
     */
    fun canRasterize(stack: StackNode): Boolean =
        stack.url == null && stack.action == null && stack.gradient == null && stack.backgroundImage == null &&
            !hasBorder(stack) &&
            stack.children.all { child ->
                when (child) {
                    is TextNode -> child.url == null && child.action == null
                    is SpacerNode -> child.length != null
                    is StackNode -> canRasterize(child)
                    is ImageNode, is DateNode, is WidgetRoot -> false
                }
            }

    private fun hasBorder(stack: StackNode) = stack.borderColor != null && stack.borderWidth > 0
}
