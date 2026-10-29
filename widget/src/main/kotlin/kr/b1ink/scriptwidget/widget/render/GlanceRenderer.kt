package kr.b1ink.scriptwidget.widget.render

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import kr.b1ink.scriptwidget.widget.RefreshAction
import kr.b1ink.scriptwidget.widget.WidgetActionCallback
import androidx.glance.action.actionParametersOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.LocalSize
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import kr.b1ink.scriptwidget.domain.model.Background
import kr.b1ink.scriptwidget.domain.model.DateNode
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.SpacerNode
import kr.b1ink.scriptwidget.domain.model.StackNode
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.domain.model.background

internal fun colorProvider(color: Color): ColorProvider = ColorProvider(day = color, night = color)

/** Day / night colors of a script color (switches with the system theme without re-running the script). */
internal fun themedProvider(value: String?, fallback: Color, opacity: Float = 1f): ColorProvider {
    fun Color.faded() = copy(alpha = alpha * opacity)
    return ColorProvider(day = Styles.color(value, fallback, night = false).faded(), night = Styles.color(value, fallback, night = true).faded())
}

/** Renders the JSON widget tree produced by scripts with Glance composables. */
object GlanceRenderer {
    /** Outline bitmap pixels per dp, assuming stacks around ~50dp. */
    private const val BORDER_SCALE = 2.5f

    @Composable
    fun Root(root: WidgetRoot) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .backgroundOf(root.background, Styles.DEFAULT_BACKGROUND)
                .paddingOf(root.padding ?: Styles.DEFAULT_PADDING)
                .clickable(
                    root.action?.let(::tapAction)
                        ?: root.url?.let { actionStartActivity(Intent(Intent.ACTION_VIEW, it.toUri())) }
                        ?: actionRunCallback<RefreshAction>()
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.Start,
        ) {
            val width = AvailableWidth.inside(LocalSize.current.width.value, root.padding ?: Styles.DEFAULT_PADDING)
            CompositionLocalProvider(
                LocalAvailableWidth provides width,
                LocalImageMaxPx provides BitmapBudget.maxImagePx(root),
            ) {
                ColumnChildren(root.children, root.spacing)
            }
        }
    }

    /** A child of a row/column: a script element, or the gap added by `spacing`. */
    private sealed interface Slot {
        data class Gap(val length: Float) : Slot
        /** [stretch]: a one-line text taking the row's free space (see StackLayout.stretchedText). */
        data class Item(val node: WidgetNode, val stretch: Boolean = false) : Slot
    }

    private fun slots(children: List<WidgetNode>, spacing: Float, stretched: Int? = null): List<Slot> = buildList {
        children.forEachIndexed { index, node ->
            // The stretched text replaces the flexible spacer right after it.
            if (stretched != null && index == stretched + 1) return@forEachIndexed
            if (index > 0 && spacing > 0) add(Slot.Gap(spacing))
            add(Slot.Item(node, stretch = index == stretched))
        }
    }

    private fun List<Slot>.hasFlexibleSpacer() = any { it is Slot.Item && it.node is SpacerNode && it.node.length == null }

    @Composable
    private fun ColumnScope.ColumnChildren(children: List<WidgetNode>, spacing: Float) =
        ColumnSlots(StackLayout.nest(slots(children, spacing)))

    @Composable
    private fun ColumnScope.ColumnSlots(slots: StackLayout.Nested<Slot>) {
        slots.direct.forEach { slot ->
            when (slot) {
                is Slot.Gap -> Spacer(GlanceModifier.height(slot.length.dp))
                is Slot.Item -> when (val node = slot.node) {
                    is SpacerNode -> Spacer(node.length?.let { GlanceModifier.height(it.dp) } ?: GlanceModifier.defaultWeight())
                    // Children that grow vertically split the column's height (SwiftUI VStack).
                    else -> if (StackLayout.wantsHeight(node)) {
                        Node(node, inColumn = true, outer = GlanceModifier.defaultWeight(), weightedHeight = true)
                    } else {
                        Node(node, inColumn = true)
                    }
                }
            }
        }
        // Over RemoteViews' 10-children limit: the rest goes into an inner column instead of being dropped.
        slots.rest?.let { rest ->
            val flat = rest.flatten()
            var modifier: GlanceModifier = GlanceModifier.fillMaxWidth()
            if (flat.hasFlexibleSpacer() || flat.any { it is Slot.Item && StackLayout.wantsHeight(it.node) }) modifier = modifier.defaultWeight()
            Column(modifier = modifier) { ColumnSlots(rest) }
        }
    }

    @Composable
    private fun RowScope.RowChildren(children: List<WidgetNode>, spacing: Float, stretched: Int? = null) =
        RowSlots(StackLayout.nest(slots(children, spacing, stretched)))

    @Composable
    private fun RowScope.RowSlots(slots: StackLayout.Nested<Slot>) {
        slots.direct.forEach { slot ->
            when (slot) {
                is Slot.Gap -> Spacer(GlanceModifier.width(slot.length.dp))
                is Slot.Item -> when (val node = slot.node) {
                    is SpacerNode -> Spacer(node.length?.let { GlanceModifier.width(it.dp) } ?: GlanceModifier.defaultWeight())
                    // Flexible children split the row (SwiftUI HStack) instead of the first one taking it all.
                    else -> if (slot.stretch) {
                        Node(node, inColumn = false, outer = GlanceModifier.defaultWeight())
                    } else if (StackLayout.wantsWidth(node)) {
                        Node(node, inColumn = false, outer = GlanceModifier.defaultWeight(), weighted = true)
                    } else {
                        Node(node, inColumn = false)
                    }
                }
            }
        }
        slots.rest?.let { rest ->
            val flat = rest.flatten()
            val flexible = flat.hasFlexibleSpacer() || flat.any { it is Slot.Item && (it.stretch || StackLayout.wantsWidth(it.node)) }
            Row(modifier = if (flexible) GlanceModifier.defaultWeight() else GlanceModifier, verticalAlignment = Alignment.CenterVertically) {
                RowSlots(rest)
            }
        }
    }

    @Composable
    private fun Node(
        node: WidgetNode,
        inColumn: Boolean,
        outer: GlanceModifier = GlanceModifier,
        weighted: Boolean = false,
        weightedHeight: Boolean = false,
    ) {
        when (node) {
            is TextNode -> TextElement(node, inColumn, outer)
            is DateNode -> DateElement(node, inColumn)
            is ImageNode -> ImageElement(node, inColumn)
            is StackNode -> StackElement(node, outer, weighted, weightedHeight)
            is SpacerNode, is WidgetRoot -> Unit
        }
    }

    @Composable
    private fun TextElement(node: TextNode, inColumn: Boolean, outer: GlanceModifier = GlanceModifier) {
        val plan = TextPlan.of(node)
        if (plan == TextPlan.Bitmap) return TextBitmapElement(node, inColumn, outer)
        val family = (plan as TextPlan.Native).family
        val color = themedProvider(node.color, Styles.DEFAULT_TEXT, node.opacity)
        Text(
            text = node.text,
            modifier = outer
                .alignFill(node.align, inColumn)
                .clickUrl(node.url, node.action),
            style = TextStyle(
                color = color,
                fontSize = node.size.sp,
                fontWeight = fontWeight(node.weight),
                fontStyle = if (node.italic) FontStyle.Italic else FontStyle.Normal,
                textAlign = textAlign(node.align),
                fontFamily = when (family) {
                    TextPlan.Family.Monospace -> FontFamily.Monospace
                    TextPlan.Family.Serif -> FontFamily.Serif
                    TextPlan.Family.SansSerif -> null
                },
            ),
            maxLines = if (node.lineLimit > 0) node.lineLimit else Int.MAX_VALUE,
        )
    }

    @Composable
    private fun DateElement(node: DateNode, inColumn: Boolean) {
        val text = Styles.dateText(LocalContext.current, node)
        Text(
            text = text,
            modifier = GlanceModifier.alignFill(node.align, inColumn).clickUrl(null, node.action),
            style = TextStyle(
                color = themedProvider(node.color, Styles.DEFAULT_TEXT),
                fontSize = node.size.sp,
                fontWeight = fontWeight(node.weight),
                textAlign = textAlign(node.align),
            ),
            maxLines = 1,
        )
    }

    /** Text Glance can't style (weights, shadows, font files, scale-to-fit), drawn as a bitmap. */
    @Composable
    private fun TextBitmapElement(node: TextNode, inColumn: Boolean, outer: GlanceModifier = GlanceModifier) {
        val context = LocalContext.current
        val density = context.resources.displayMetrics.density
        // Width inside the parents' paddings; siblings in a row can still make it an upper bound.
        val widthDp = LocalAvailableWidth.current.takeUnless { it.isNaN() } ?: LocalSize.current.width.value
        val maxWidthPx = (widthDp * density).toInt().coerceIn(1, Styles.MAX_IMAGE_PX)
        // Uncolored text is white, like Styles.DEFAULT_TEXT.
        val night = Styles.isNight(context)
        val bitmap = remember(node, maxWidthPx, night) { TextBitmap.render(node, maxWidthPx, density, night) }
        val height = (bitmap.height / density).dp
        // Centered / right-aligned text is drawn across the full width with that alignment.
        val spans = inColumn && node.align != "left" && LocalColumnFillsWidth.current
        val sized = if (!spans) GlanceModifier.size((bitmap.width / density).dp, height)
        else GlanceModifier.fillMaxWidth().height(height)
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = node.text,
            modifier = outer.then(sized).clickUrl(node.url, node.action),
            contentScale = ContentScale.Fit,
        )
    }

    @Composable
    private fun ImageElement(node: ImageNode, inColumn: Boolean) {
        val density = LocalContext.current.resources.displayMetrics.density
        val maxPx = node.size?.let { (it.max() * density).toInt() }
            ?.coerceIn(1, LocalImageMaxPx.current)
            ?: LocalImageMaxPx.current
        val bitmap = remember(node.src, maxPx) { ImageFileDecoder.decode(node.src, maxPx) } ?: return

        var modifier = GlanceModifier.clickUrl(node.url, node.action)
        node.size?.let { (w, h) -> modifier = modifier.size(w.dp, h.dp) }
        if (node.cornerRadius > 0) modifier = modifier.cornerRadius(node.cornerRadius.dp)
        val image = @Composable {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = null,
                modifier = modifier,
                contentScale = if (node.contentMode == "fill") ContentScale.Crop else ContentScale.Fit,
                colorFilter = node.tint?.let { ColorFilter.tint(themedProvider(it, Color.White)) },
            )
        }
        if (inColumn && node.align != "left" && LocalColumnFillsWidth.current) {
            // Column children can't be aligned individually; a full-width box places the image.
            val alignment = if (node.align == "right") Alignment.CenterEnd else Alignment.Center
            Box(modifier = GlanceModifier.fillMaxWidth(), contentAlignment = alignment) { image() }
        } else {
            image()
        }
    }

    @Composable
    private fun StackElement(
        node: StackNode,
        outer: GlanceModifier = GlanceModifier,
        weighted: Boolean = false,
        weightedHeight: Boolean = false,
    ) {
        var modifier = outer.clickUrl(node.url, node.action)
        modifier = modifier.backgroundOf(node.background, fallback = null)
        if (node.cornerRadius > 0) modifier = modifier.cornerRadius(node.cornerRadius.dp)
        node.size?.let { (w, h) ->
            if (w > 0) modifier = modifier.width(w.dp)
            if (h > 0) modifier = modifier.height(h.dp)
        }
        // A stack grows along an axis when it (or a descendant) has a flexible spacer there, like SwiftUI;
        // a weighted stack already gets its share from the parent.
        if (!weighted && StackLayout.wantsWidth(node)) modifier = modifier.fillMaxWidth()
        if (!weightedHeight && StackLayout.wantsHeight(node)) modifier = modifier.fillMaxHeight()
        node.padding?.let { modifier = modifier.paddingOf(it) }

        val parentWidth = LocalAvailableWidth.current.takeUnless { it.isNaN() } ?: LocalSize.current.width.value
        val innerWidth = AvailableWidth.inside(parentWidth, node.padding, node.size?.getOrNull(0))
        val content = @Composable {
          CompositionLocalProvider(
              LocalAvailableWidth provides innerWidth,
              LocalColumnFillsWidth provides (node.dir != "v" || StackLayout.columnFillsWidth(node, weighted)),
          ) {
            if (node.dir == "v") {
                Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
                    ColumnChildren(node.children, node.spacing)
                }
            } else {
                val vertical = when (node.align) {
                    "top" -> Alignment.Top
                    "bottom" -> Alignment.Bottom
                    else -> Alignment.CenterVertically
                }
                Row(modifier = modifier, verticalAlignment = vertical) {
                    RowChildren(node.children, node.spacing, StackLayout.stretchedText(node))
                }
            }
          }
        }
        val borderColor = node.borderColor?.let { Styles.color(it, Color.Transparent, Styles.isNight(LocalContext.current)) }
        if (borderColor == null || node.borderWidth <= 0) {
            content()
            return
        }
        // Glance has no border modifier.
        var outer: GlanceModifier = GlanceModifier
        if (StackLayout.fillsMainAxis(node)) outer = if (node.dir == "v") outer.fillMaxHeight() else outer.fillMaxWidth()
        if (node.cornerRadius > 0) outer = outer.cornerRadius(node.cornerRadius.dp)
        if (node.background is Background.Solid) {
            // Exact: the border color shows around the stack's own opaque background.
            Box(modifier = outer.background(borderColor).padding(node.borderWidth.dp)) { content() }
        } else {
            // Approximate: an outline bitmap stretched behind the stack.
            val bitmap = remember(borderColor, node.borderWidth, node.cornerRadius) {
                BorderBitmap.create(borderColor.toArgb(), node.borderWidth * BORDER_SCALE, node.cornerRadius * BORDER_SCALE)
            }
            Box(modifier = outer.background(ImageProvider(bitmap), ContentScale.FillBounds)) { content() }
        }
    }

    @Composable
    private fun GlanceModifier.backgroundOf(background: Background, fallback: Color?): GlanceModifier {
        val fallbackModifier = fallback?.let { background(it) } ?: this
        return when (background) {
            Background.None -> fallbackModifier
            is Background.Solid -> background(themedProvider(background.color, fallback ?: Color.Transparent))
            is Background.Linear -> {
                val bitmap = remember(background.gradient) { GradientBitmap.create(background.gradient) }
                background(ImageProvider(bitmap), ContentScale.FillBounds)
            }
            is Background.Image -> {
                val bitmap = remember(background.src) { ImageFileDecoder.decode(background.src, Styles.MAX_IMAGE_PX) }
                if (bitmap == null) fallbackModifier else background(ImageProvider(bitmap), ContentScale.Crop)
            }
        }
    }

    private fun GlanceModifier.paddingOf(p: List<Float>): GlanceModifier =
        padding(
            top = p.getOrElse(0) { 0f }.dp,
            start = p.getOrElse(1) { 0f }.dp,
            bottom = p.getOrElse(2) { 0f }.dp,
            end = p.getOrElse(3) { 0f }.dp,
        )

    private fun GlanceModifier.clickUrl(url: String?, action: String? = null): GlanceModifier = when {
        action != null -> clickable(tapAction(action))
        url != null -> clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, url.toUri())))
        else -> this
    }

    private fun tapAction(action: String) =
        actionRunCallback<WidgetActionCallback>(actionParametersOf(WidgetActionCallback.ACTION to action))

    /** TextAlign only has an effect when the text spans the full width of a (full-width) column. */
    @Composable
    private fun GlanceModifier.alignFill(align: String, inColumn: Boolean): GlanceModifier =
        if (inColumn && align != "left" && LocalColumnFillsWidth.current) fillMaxWidth() else this

    private fun textAlign(align: String) = when (align) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
    }

    private fun fontWeight(weight: String) = when (weight) {
        "medium", "semibold" -> FontWeight.Medium
        "bold", "heavy", "black" -> FontWeight.Bold
        else -> FontWeight.Normal
    }
}
