package kr.b1ink.scriptwidget.ui.preview

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.widget.render.ImageFileDecoder
import kr.b1ink.scriptwidget.widget.render.StackLayout
import kr.b1ink.scriptwidget.widget.render.Styles
import kr.b1ink.scriptwidget.domain.model.Background
import kr.b1ink.scriptwidget.domain.model.DateNode
import kr.b1ink.scriptwidget.domain.model.background
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot

/**
 * In-app preview of a widget tree using regular Compose. Mirrors `GlanceRenderer` in the widget
 * module so that what you see while editing matches the home screen.
 */
@Composable
fun WidgetPreview(
    root: WidgetRoot,
    family: WidgetFamily,
    /** With [onSelect], elements can be tapped (the designer); the [selected] path is outlined. */
    selected: List<Int>? = null,
    onSelect: ((List<Int>?) -> Unit)? = null,
) {
    val selection = Selection(selected, onSelect)
    val (w, h) = when (family) {
        WidgetFamily.Small -> 170 to 170
        WidgetFamily.Medium -> 345 to 170
        WidgetFamily.Large -> 345 to 360
    }
    val p = root.padding ?: Styles.DEFAULT_PADDING
    Column(
        modifier = Modifier
            .size(w.dp, h.dp)
            .clip(RoundedCornerShape(22.dp))
            .backgroundOf(root.background, Styles.DEFAULT_BACKGROUND)
            .then(if (onSelect != null) Modifier.clickable { onSelect(null) } else Modifier)
            .padding(top = p[0].dp, start = p[1].dp, bottom = p[2].dp, end = p[3].dp),
        verticalArrangement = Arrangement.Center,
    ) {
        ColumnChildren(root.children, root.spacing, emptyList(), selection)
    }
}

/** Which element is selected in the designer, and where taps go. */
private class Selection(val selected: List<Int>?, val onSelect: ((List<Int>?) -> Unit)?) {
    fun modifier(path: List<Int>): Modifier {
        val onSelect = onSelect ?: return Modifier
        val outline = if (path == selected) Modifier.border(2.dp, SELECTED, RoundedCornerShape(4.dp)) else Modifier
        return Modifier.testTag("preview-" + path.joinToString("-")).then(outline).clickable { onSelect(path) }
    }

    companion object {
        val NONE = Selection(null, null)
        private val SELECTED = Color(0xFF0A84FF)
    }
}

@Composable
private fun ColumnScope.ColumnChildren(children: List<WidgetNode>, spacing: Float, path: List<Int>, selection: Selection) {
    children.forEachIndexed { i, node ->
        if (i > 0 && spacing > 0) Spacer(Modifier.height(spacing.dp))
        if (node is SpacerNode) {
            Spacer(node.length?.let { Modifier.height(it.dp) } ?: Modifier.weight(1f))
        } else if (StackLayout.wantsHeight(node)) {
            // Children that grow vertically split the column's height, as on the home screen.
            Node(node, inColumn = true, outer = Modifier.weight(1f), weightedHeight = true, path = path + i, selection = selection)
        } else {
            Node(node, inColumn = true, path = path + i, selection = selection)
        }
    }
}

@Composable
private fun RowScope.RowChildren(children: List<WidgetNode>, spacing: Float, path: List<Int>, selection: Selection) {
    children.forEachIndexed { i, node ->
        if (i > 0 && spacing > 0) Spacer(Modifier.width(spacing.dp))
        if (node is SpacerNode) {
            Spacer(node.length?.let { Modifier.width(it.dp) } ?: Modifier.weight(1f))
        } else if (StackLayout.wantsWidth(node)) {
            Node(node, inColumn = false, outer = Modifier.weight(1f), weighted = true, path = path + i, selection = selection)
        } else {
            Node(node, inColumn = false, path = path + i, selection = selection)
        }
    }
}

@Composable
private fun Node(
    node: WidgetNode,
    inColumn: Boolean,
    outer: Modifier = Modifier,
    weighted: Boolean = false,
    weightedHeight: Boolean = false,
    path: List<Int> = emptyList(),
    selection: Selection = Selection.NONE,
) {
    val select = selection.modifier(path)
    when (node) {
        is TextNode -> Text(
            text = node.text,
            color = parse(node.color, Color.White).let { it.copy(alpha = it.alpha * node.opacity) },
            fontSize = node.size.sp,
            fontWeight = weight(node.weight),
            fontStyle = if (node.italic) FontStyle.Italic else FontStyle.Normal,
            textAlign = align(node.align),
            maxLines = if (node.lineLimit > 0) node.lineLimit else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            modifier = (if (inColumn && node.align != "left") Modifier.fillMaxWidth() else Modifier).then(select),
        )

        is DateNode -> {
            val text = Styles.dateText(LocalContext.current, node)
            Text(
                text = text,
                color = parse(node.color, Color.White),
                fontSize = node.size.sp,
                fontWeight = weight(node.weight),
                textAlign = align(node.align),
                maxLines = 1,
                modifier = (if (inColumn && node.align != "left") Modifier.fillMaxWidth() else Modifier).then(select),
            )
        }

        is ImageNode -> {
            val bitmap = remember(node.src) { ImageFileDecoder.decode(node.src, Styles.MAX_IMAGE_PX) } ?: return
            var modifier: Modifier = select
            node.size?.let { (w, h) -> modifier = modifier.size(w.dp, h.dp) }
            if (node.cornerRadius > 0) modifier = modifier.clip(RoundedCornerShape(node.cornerRadius.dp))
            val image = @Composable {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = if (node.contentMode == "fill") ContentScale.Crop else ContentScale.Fit,
                    colorFilter = node.tint?.let { ColorFilter.tint(parse(it, Color.White)) },
                    modifier = modifier.alpha(node.opacity),
                )
            }
            if (inColumn && node.align != "left") {
                Box(Modifier.fillMaxWidth(), contentAlignment = if (node.align == "right") Alignment.CenterEnd else Alignment.Center) {
                    image()
                }
            } else {
                image()
            }
        }

        is StackNode -> {
            var modifier: Modifier = outer.then(select)
            node.size?.let { (w, h) ->
                if (w > 0) modifier = modifier.width(w.dp)
                if (h > 0) modifier = modifier.height(h.dp)
            }
            // Same growth rules as GlanceRenderer: stacks with flexible spacers fill that axis.
            if (!weighted && StackLayout.wantsWidth(node)) modifier = modifier.fillMaxWidth()
            if (!weightedHeight && StackLayout.wantsHeight(node)) modifier = modifier.fillMaxHeight()
            val borderColor = node.borderColor
            if (borderColor != null && node.borderWidth > 0) {
                modifier = modifier.border(node.borderWidth.dp, parse(borderColor, Color.Transparent), RoundedCornerShape(node.cornerRadius.dp))
            }
            if (node.cornerRadius > 0) modifier = modifier.clip(RoundedCornerShape(node.cornerRadius.dp))
            modifier = modifier.backgroundOf(node.background, fallback = null)
            node.padding?.let { p ->
                modifier = modifier.padding(top = p[0].dp, start = p[1].dp, bottom = p[2].dp, end = p[3].dp)
            }
            if (node.dir == "v") {
                Column(modifier) { ColumnChildren(node.children, node.spacing, path, selection) }
            } else {
                val vertical = when (node.align) {
                    "top" -> Alignment.Top
                    "bottom" -> Alignment.Bottom
                    else -> Alignment.CenterVertically
                }
                Row(modifier, verticalAlignment = vertical) { RowChildren(node.children, node.spacing, path, selection) }
            }
        }

        is SpacerNode, is WidgetRoot -> Unit
    }
}

private fun parse(value: String?, fallback: Color): Color = Styles.color(value, fallback)

/** Mirrors GlanceRenderer's background rules (image > gradient > color). */
@Composable
private fun Modifier.backgroundOf(background: Background, fallback: Color?): Modifier = when (background) {
    Background.None -> fallback?.let { background(it) } ?: this
    is Background.Solid -> background(parse(background.color, fallback ?: Color.Transparent))
    is Background.Linear -> {
        val g = background.gradient
        val stops = g.stops().zip(g.colors.map { parse(it, Color.Transparent) }).toTypedArray()
        drawBehind {
            drawRect(
                Brush.linearGradient(
                    colorStops = stops,
                    start = Offset(g.start.getOrElse(0) { 0f } * size.width, g.start.getOrElse(1) { 0f } * size.height),
                    end = Offset(g.end.getOrElse(0) { 0f } * size.width, g.end.getOrElse(1) { 1f } * size.height),
                )
            )
        }
    }
    is Background.Image -> {
        val bitmap = remember(background.src) { ImageFileDecoder.decode(background.src, Styles.MAX_IMAGE_PX) }
        if (bitmap == null) {
            fallback?.let { background(it) } ?: this
        } else {
            val image = bitmap.asImageBitmap()
            drawBehind {
                val scale = maxOf(size.width / image.width, size.height / image.height)
                val w = image.width * scale
                val h = image.height * scale
                drawImage(
                    image = image,
                    dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
                    dstSize = IntSize(w.toInt(), h.toInt()),
                )
            }
        }
    }
}

private fun weight(w: String) = when (w) {
    "ultraLight" -> FontWeight.ExtraLight
    "thin" -> FontWeight.Thin
    "light" -> FontWeight.Light
    "medium" -> FontWeight.Medium
    "semibold" -> FontWeight.SemiBold
    "bold" -> FontWeight.Bold
    "heavy" -> FontWeight.ExtraBold
    "black" -> FontWeight.Black
    else -> FontWeight.Normal
}

private fun align(a: String) = when (a) {
    "center" -> TextAlign.Center
    "right" -> TextAlign.End
    else -> TextAlign.Start
}
