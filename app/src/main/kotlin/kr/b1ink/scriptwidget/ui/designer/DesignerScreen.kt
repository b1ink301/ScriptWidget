package kr.b1ink.scriptwidget.ui.designer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.domain.model.DataBinding
import kr.b1ink.scriptwidget.domain.model.DesignBackground
import kr.b1ink.scriptwidget.domain.model.DesignDate
import kr.b1ink.scriptwidget.domain.model.DesignElement
import kr.b1ink.scriptwidget.domain.model.DesignImage
import kr.b1ink.scriptwidget.domain.model.DesignSpacer
import kr.b1ink.scriptwidget.domain.model.DesignStack
import kr.b1ink.scriptwidget.domain.model.DesignSymbol
import kr.b1ink.scriptwidget.domain.model.DesignText
import kr.b1ink.scriptwidget.domain.model.WidgetDesign
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.ui.preview.WidgetPreview

class DesignerActions(
    val onBack: () -> Unit,
    val onUndo: () -> Unit,
    val onRedo: () -> Unit,
    val onShowCode: () -> Unit,
    val onPin: () -> Unit,
    val onSelect: (List<Int>?) -> Unit,
    val onFamily: (WidgetFamily) -> Unit,
    val onAdd: (DesignElement) -> Unit,
    /** `(coalesce, change)`: coalesce is set while typing, so keystrokes share one undo step. */
    val onUpdateSelected: (Boolean, (DesignElement) -> DesignElement) -> Unit,
    val onUpdateDesign: ((WidgetDesign) -> WidgetDesign) -> Unit,
    val onMove: (Int) -> Unit,
    /** `(from, toParent, index)`: an outline row dropped somewhere else, see `WidgetDesign.moveTo`. */
    val onDrop: (List<Int>, List<Int>, Int) -> Unit,
    val onWrap: (String) -> Unit,
    val onRemove: () -> Unit,
)

private enum class Panel(@StringRes val title: Int) { Outline(R.string.designer_panel_outline), Properties(R.string.designer_panel_properties) }

/** Widget designer: live preview on top, the element tree and the selected element's properties below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignerScreen(state: DesignerUiState, actions: DesignerActions) {
    var panel by remember { mutableStateOf(if (state.selected != null) Panel.Properties else Panel.Outline) }
    // Selecting something (in the preview or the outline) shows its properties.
    var lastSelected by remember { mutableStateOf(state.selected) }
    // ...except a row dropped in the outline, which stays there to keep arranging.
    var dropped by remember { mutableStateOf(false) }
    LaunchedEffect(state.selected) {
        if (state.selected != lastSelected && state.selected != null && !dropped) panel = Panel.Properties
        dropped = false
        lastSelected = state.selected
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = actions.onBack) { Text("←") } },
                title = { Text(state.name + if (state.dirty) " •" else "", maxLines = 1) },
                actions = {
                    TextButton(onClick = actions.onUndo, enabled = state.canUndo, modifier = Modifier.testTag("undo")) { Text("↶") }
                    TextButton(onClick = actions.onRedo, enabled = state.canRedo, modifier = Modifier.testTag("redo")) { Text("↷") }
                    TextButton(onClick = actions.onPin) { Text(stringResource(R.string.designer_pin)) }
                    TextButton(onClick = actions.onShowCode) { Text(stringResource(R.string.designer_code)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.editedByHand) {
                Notice(stringResource(R.string.designer_notice_edited_by_hand))
            }
            if (state.notDesigned) {
                Notice(stringResource(R.string.designer_notice_not_designed))
            }
            PreviewArea(state, actions)
            TabRow(selectedTabIndex = panel.ordinal) {
                Panel.entries.forEach { p ->
                    Tab(selected = panel == p, onClick = { panel = p }, text = { Text(stringResource(p.title)) }, modifier = Modifier.testTag("panel-${p.name}"))
                }
            }
            if (state.selected != null) Toolbar(actions)
            Box(Modifier.weight(1f)) {
                when (panel) {
                    Panel.Outline -> Outline(state, actions, onDropped = { dropped = true })
                    Panel.Properties -> Properties(state, actions)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
    }
}

@Composable
private fun PreviewArea(state: DesignerUiState, actions: DesignerActions) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                WidgetFamily.Small to R.string.designer_family_small,
                WidgetFamily.Medium to R.string.designer_family_medium,
                WidgetFamily.Large to R.string.designer_family_large,
            ).forEach { (family, label) ->
                FilterChip(selected = state.family == family, onClick = { actions.onFamily(family) }, label = { Text(stringResource(label)) })
            }
        }
        Spacer(Modifier.height(6.dp))
        val widget = state.preview?.widget
        when {
            widget != null -> WidgetPreview(widget, state.family, selected = state.selected, onSelect = actions.onSelect)
            state.preview?.error != null -> Text(
                stringResource(R.string.designer_preview_error, state.preview.error.toString()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            else -> Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.designer_rendering)) }
        }
        Text(
            stringResource(if (state.running) R.string.designer_updating else R.string.designer_preview_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun Toolbar(actions: DesignerActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = { actions.onMove(-1) }, modifier = Modifier.testTag("tool-up")) { Text(stringResource(R.string.designer_move_up)) }
        TextButton(onClick = { actions.onMove(1) }, modifier = Modifier.testTag("tool-down")) { Text(stringResource(R.string.designer_move_down)) }
        TextButton(onClick = { actions.onWrap("v") }, modifier = Modifier.testTag("tool-wrap")) { Text(stringResource(R.string.designer_wrap_in_stack)) }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = actions.onRemove, modifier = Modifier.testTag("tool-remove")) {
            Text(stringResource(R.string.designer_delete), color = MaterialTheme.colorScheme.error)
        }
    }
}

// --- outline ---

@Composable
private fun Outline(state: DesignerUiState, actions: DesignerActions, onDropped: () -> Unit) {
    // Row positions inside the scrolling column, for working out where a dragged row lands.
    val bounds = remember { mutableStateMapOf<List<Int>, Pair<Float, Float>>() }
    var dragged by remember { mutableStateOf<List<Int>?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }

    fun rows(list: List<DesignElement>, path: List<Int>): List<Pair<List<Int>, DesignElement>> =
        list.flatMapIndexed { i, e -> listOf((path + i) to e) + if (e is DesignStack) rows(e.children, path + i) else emptyList() }
    val all = rows(state.design.children, emptyList())
    fun target(): OutlineDrop.Target? {
        val from = dragged ?: return null
        val dropRows = all.mapNotNull { (path, e) ->
            bounds[path]?.let { (top, bottom) -> OutlineDrop.Row(path, top, bottom, (e as? DesignStack)?.children?.size) }
        }
        return OutlineDrop.at(dropRows, from, dragY)
    }
    val target = target()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        OutlineRow(stringResource(R.string.designer_outline_widget), depth = 0, selected = state.selected == null, tag = "outline-widget") { actions.onSelect(null) }
        all.forEach { (path, element) ->
            val key = path.joinToString("-")
            val drag = OutlineDrag(
                tag = "drag-$key",
                onStart = { y ->
                    dragged = path
                    dragY = (bounds[path]?.first ?: 0f) + y
                },
                onMove = { y -> dragY = (bounds[path]?.first ?: 0f) + y },
                onEnd = {
                    target()?.let {
                        onDropped()
                        actions.onDrop(path, it.parent, it.index)
                    }
                    dragged = null
                },
                onCancel = { dragged = null },
            )
            OutlineRow(
                label(element),
                depth = path.size,
                selected = state.selected == path,
                tag = "outline-$key",
                marker = target?.takeIf { it.row == path }?.place,
                lifted = dragged == path,
                drag = drag,
                modifier = Modifier.onGloballyPositioned { bounds[path] = it.positionInParent().y to it.positionInParent().y + it.size.height },
            ) {
                actions.onSelect(path)
            }
        }
        if (state.design.children.isEmpty()) {
            Text(
                stringResource(R.string.designer_outline_empty),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
        AddMenu(actions, inside = (state.selected?.let(state.design::elementAt) as? DesignStack) != null)
    }
}

/** Callbacks of a row's drag handle; [onStart] and [onMove] get the finger's y inside the row. */
private class OutlineDrag(
    val tag: String,
    val onStart: (Float) -> Unit,
    val onMove: (Float) -> Unit,
    val onEnd: () -> Unit,
    val onCancel: () -> Unit,
)

@Composable
private fun OutlineRow(
    text: String,
    depth: Int,
    selected: Boolean,
    tag: String,
    modifier: Modifier = Modifier,
    marker: OutlineDrop.Place? = null,
    /** Being dragged; it stays in place (moving it would move the handle's touch coordinates too). */
    lifted: Boolean = false,
    drag: OutlineDrag? = null,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (lifted) 0.6f else 1f }
            .background(
                when {
                    marker == OutlineDrop.Place.Inside -> MaterialTheme.colorScheme.tertiaryContainer
                    lifted -> MaterialTheme.colorScheme.surfaceContainerHighest
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> Color.Transparent
                },
            )
            .drawWithContent {
                drawContent()
                val y = when (marker) {
                    OutlineDrop.Place.Before -> 0f
                    OutlineDrop.Place.After -> size.height
                    else -> null
                }
                if (y != null) drawLine(accent, Offset((16 + depth * 20).dp.toPx(), y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            maxLines = 1,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .testTag(tag)
                .clickable(onClick = onClick)
                .padding(start = (16 + depth * 20).dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        )
        if (drag != null) {
            val description = stringResource(R.string.designer_drag_handle)
            var handleTop by remember { mutableFloatStateOf(0f) }
            // Starting a drag recomposes the outline; the gesture must survive that, so it is keyed on
            // the row and reads the newest callbacks.
            val current by rememberUpdatedState(drag)
            Text(
                "≡",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .testTag(drag.tag)
                    .onGloballyPositioned { handleTop = it.positionInParent().y }
                    .semantics { contentDescription = description }
                    .pointerInput(drag.tag) {
                        detectDragGestures(
                            onDragStart = { current.onStart(handleTop + it.y) },
                            onDrag = { change, _ ->
                                change.consume()
                                // The row stays put while dragged, so the finger's own position is exact.
                                current.onMove(handleTop + change.position.y)
                            },
                            onDragEnd = { current.onEnd() },
                            onDragCancel = { current.onCancel() },
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun label(e: DesignElement): String = when (e) {
    is DesignText -> (e.data?.let { "📊 ${dataLabel(it)}" } ?: "🔤 ${stringResource(R.string.designer_element_text)}") + " · " + e.text.replace("\n", " ").take(24)
    is DesignDate -> "🕒 " + (dateStyles().firstOrNull { it.first == e.style }?.second ?: stringResource(R.string.designer_date_style_time))
    is DesignSymbol -> "⭐ ${stringResource(R.string.designer_element_symbol)} · ${e.name}"
    is DesignImage -> "🖼 " + stringResource(R.string.designer_element_image)
    is DesignSpacer -> "↕ " + stringResource(R.string.designer_element_spacer) +
        (e.length?.let { " ${it.toInt()}" } ?: (" " + stringResource(R.string.designer_label_spacer_flex)))
    is DesignStack -> if (e.dir == "v") {
        "▤ " + stringResource(R.string.designer_element_vstack)
    } else {
        "▥ " + stringResource(R.string.designer_element_hstack)
    }
}

@Composable
private fun dataLabel(b: DataBinding): String = stringResource(
    when (b) {
        is DataBinding.DateText -> R.string.designer_data_date_text
        DataBinding.Battery -> R.string.designer_data_battery
        is DataBinding.NextEvent -> R.string.designer_data_next_event
        is DataBinding.Reminders -> R.string.designer_data_reminders
        is DataBinding.Weather -> R.string.designer_data_weather
        is DataBinding.Json -> R.string.designer_data_json
        is DataBinding.Parameter -> R.string.designer_data_parameter
    },
)

@Composable
private fun AddMenu(actions: DesignerActions, inside: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(16.dp)) {
        FilledTonalButton(onClick = { open = true }, modifier = Modifier.testTag("add-element")) {
            Text(stringResource(if (inside) R.string.designer_add_element_inside else R.string.designer_add_element))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text(stringResource(R.string.designer_menu_elements), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            elements().forEach { (title, element) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { open = false; actions.onAdd(element) })
            }
            HorizontalDivider()
            Text(stringResource(R.string.designer_menu_data_blocks), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            dataBlocks().forEach { (title, element) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { open = false; actions.onAdd(element) })
            }
        }
    }
}

@Composable
private fun elements(): List<Pair<String, DesignElement>> = listOf(
    stringResource(R.string.designer_element_text) to DesignText(stringResource(R.string.designer_default_text)),
    stringResource(R.string.designer_element_time_live) to DesignDate(style = "time", size = 28f, weight = "bold"),
    stringResource(R.string.designer_element_symbol) to DesignSymbol("star.fill", color = "#FFFFCC00"),
    stringResource(R.string.designer_element_image_url) to DesignImage("https://", size = 60f, cornerRadius = 8f),
    stringResource(R.string.designer_element_spacer) to DesignSpacer(),
    stringResource(R.string.designer_element_hstack) to DesignStack(dir = "h", spacing = 6f),
    stringResource(R.string.designer_element_vstack) to DesignStack(dir = "v", spacing = 4f),
)

@Composable
private fun dataBlocks(): List<Pair<String, DesignElement>> = listOf(
    stringResource(R.string.designer_data_date_text) to
        DesignText("{value}", data = DataBinding.DateText(stringResource(R.string.designer_default_date_pattern))),
    stringResource(R.string.designer_data_battery) to DesignText("{value}%", data = DataBinding.Battery, size = 24f, weight = "bold"),
    stringResource(R.string.designer_data_next_event) to
        DesignText("{value}", data = DataBinding.NextEvent(), weight = "semibold", lineLimit = 2),
    stringResource(R.string.designer_data_reminders) to
        DesignText(stringResource(R.string.designer_default_reminders_text), data = DataBinding.Reminders()),
    stringResource(R.string.designer_data_weather) to DesignText("{value}°", data = DataBinding.Weather(), size = 32f, weight = "bold"),
    stringResource(R.string.designer_data_json_url) to DesignText("{value}", data = DataBinding.Json("https://", "")),
    stringResource(R.string.designer_data_parameter) to
        DesignText("{value}", data = DataBinding.Parameter(stringResource(R.string.designer_default_parameter_fallback))),
)

// --- properties ---

@Composable
private fun Properties(state: DesignerUiState, actions: DesignerActions) {
    val element = state.selected?.let(state.design::elementAt)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val update: ((DesignElement) -> DesignElement) -> Unit = { actions.onUpdateSelected(false, it) }
        val type: ((DesignElement) -> DesignElement) -> Unit = { actions.onUpdateSelected(true, it) }
        when (element) {
            null -> WidgetProperties(state.design, actions.onUpdateDesign)
            is DesignText -> TextProperties(element, update, type)
            is DesignDate -> {
                Chips(stringResource(R.string.designer_prop_display), dateStyles(), element.style, "prop-date") { s -> update { (it as DesignDate).copy(style = s) } }
                FontProperties(element.size, element.weight) { size, weight -> update { (it as DesignDate).copy(size = size, weight = weight) } }
                ColorProperty(stringResource(R.string.designer_prop_color), element.color) { c -> update { (it as DesignDate).copy(color = c) } }
                Chips(stringResource(R.string.designer_prop_align), aligns(), element.align, "prop-align") { a -> update { (it as DesignDate).copy(align = a) } }
            }
            is DesignSymbol -> {
                Field(stringResource(R.string.designer_prop_symbol_name), element.name, "prop-symbol") { v -> type { (it as DesignSymbol).copy(name = v) } }
                Chips(stringResource(R.string.designer_prop_common_symbols), SYMBOLS.map { it to it }, element.name, "prop-symbol-pick") { v -> update { (it as DesignSymbol).copy(name = v) } }
                Slide(stringResource(R.string.designer_prop_size), element.size, 8f..80f) { v -> update { (it as DesignSymbol).copy(size = v) } }
                ColorProperty(stringResource(R.string.designer_prop_color), element.color) { c -> update { (it as DesignSymbol).copy(color = c) } }
            }
            is DesignImage -> {
                Field(stringResource(R.string.designer_prop_image_url), element.url, "prop-image") { v -> type { (it as DesignImage).copy(url = v) } }
                Slide(stringResource(R.string.designer_prop_size), element.size ?: 60f, 16f..200f) { v -> update { (it as DesignImage).copy(size = v) } }
                Slide(stringResource(R.string.designer_prop_corner), element.cornerRadius, 0f..40f) { v -> update { (it as DesignImage).copy(cornerRadius = v) } }
            }
            is DesignSpacer -> {
                Chips(
                    stringResource(R.string.designer_prop_spacer),
                    listOf("flex" to stringResource(R.string.designer_spacer_flex), "fixed" to stringResource(R.string.designer_spacer_fixed)),
                    if (element.length == null) "flex" else "fixed",
                    "prop-spacer",
                ) { v ->
                    update { DesignSpacer(if (v == "flex") null else 8f) }
                }
                element.length?.let { length -> Slide(stringResource(R.string.designer_prop_length), length, 0f..80f) { v -> update { DesignSpacer(v) } } }
            }
            is DesignStack -> {
                Chips(
                    stringResource(R.string.designer_prop_direction),
                    listOf("h" to stringResource(R.string.designer_dir_h), "v" to stringResource(R.string.designer_dir_v)),
                    element.dir, "prop-dir") { v -> update { (it as DesignStack).copy(dir = v) } }
                if (element.dir == "h") {
                    Chips(
                        stringResource(R.string.designer_prop_vertical_align),
                        listOf(
                            "top" to stringResource(R.string.designer_align_top),
                            "center" to stringResource(R.string.designer_align_center),
                            "bottom" to stringResource(R.string.designer_align_bottom),
                        ),
                        element.align,
                        "prop-stack-align",
                    ) { v ->
                        update { (it as DesignStack).copy(align = v) }
                    }
                }
                Slide(stringResource(R.string.designer_prop_item_spacing), element.spacing, 0f..24f) { v -> update { (it as DesignStack).copy(spacing = v) } }
                Slide(stringResource(R.string.designer_prop_inner_padding), element.padding ?: 0f, 0f..24f) { v -> update { (it as DesignStack).copy(padding = v.takeIf { p -> p > 0 }) } }
                ColorProperty(stringResource(R.string.designer_prop_background), element.bg) { c -> update { (it as DesignStack).copy(bg = c) } }
                Slide(stringResource(R.string.designer_prop_corner), element.cornerRadius, 0f..30f) { v -> update { (it as DesignStack).copy(cornerRadius = v) } }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun TextProperties(
    element: DesignText,
    update: ((DesignElement) -> DesignElement) -> Unit,
    type: ((DesignElement) -> DesignElement) -> Unit,
) {
    Field(
        stringResource(if (element.data != null) R.string.designer_prop_text_with_data else R.string.designer_prop_text),
        element.text,
        "prop-text",
        singleLine = false,
    ) { v -> type { (it as DesignText).copy(text = v) } }
    DataProperty(element.data, update, type)
    FontProperties(element.size, element.weight) { size, weight -> update { (it as DesignText).copy(size = size, weight = weight) } }
    ColorProperty(stringResource(R.string.designer_prop_color), element.color) { c -> update { (it as DesignText).copy(color = c) } }
    Chips(stringResource(R.string.designer_prop_align), aligns(), element.align, "prop-align") { a -> update { (it as DesignText).copy(align = a) } }
    Chips(
        stringResource(R.string.designer_prop_line_limit),
        listOf(
            "0" to stringResource(R.string.designer_lines_unlimited),
            "1" to stringResource(R.string.designer_lines_1),
            "2" to stringResource(R.string.designer_lines_2),
            "3" to stringResource(R.string.designer_lines_3),
        ),
        element.lineLimit.toString(),
        "prop-lines",
    ) { v ->
        update { (it as DesignText).copy(lineLimit = v.toInt()) }
    }
    Chips(
        stringResource(R.string.designer_prop_overflow),
        listOf("1.0" to stringResource(R.string.designer_overflow_ellipsis), "0.5" to stringResource(R.string.designer_overflow_shrink)),
        if (element.minimumScaleFactor < 1f) "0.5" else "1.0",
        "prop-shrink",
    ) { v ->
        update { (it as DesignText).copy(minimumScaleFactor = v.toFloat()) }
    }
}

@Composable
private fun DataProperty(
    data: DataBinding?,
    update: ((DesignElement) -> DesignElement) -> Unit,
    type: ((DesignElement) -> DesignElement) -> Unit,
) {
    val options = listOf(
        "none" to stringResource(R.string.designer_data_opt_none),
        "date" to stringResource(R.string.designer_data_opt_date),
        "battery" to stringResource(R.string.designer_data_battery),
        "event" to stringResource(R.string.designer_data_next_event),
        "reminders" to stringResource(R.string.designer_data_reminders),
        "weather" to stringResource(R.string.designer_data_weather),
        "json" to "JSON",
        "parameter" to stringResource(R.string.designer_data_opt_parameter),
    )
    val defaultDatePattern = stringResource(R.string.designer_default_date_pattern)
    val current = when (data) {
        null -> "none"
        is DataBinding.DateText -> "date"
        DataBinding.Battery -> "battery"
        is DataBinding.NextEvent -> "event"
        is DataBinding.Reminders -> "reminders"
        is DataBinding.Weather -> "weather"
        is DataBinding.Json -> "json"
        is DataBinding.Parameter -> "parameter"
    }
    Chips(stringResource(R.string.designer_prop_data), options, current, "prop-data") { key ->
        val binding = when (key) {
            "date" -> DataBinding.DateText(defaultDatePattern)
            "battery" -> DataBinding.Battery
            "event" -> DataBinding.NextEvent()
            "reminders" -> DataBinding.Reminders()
            "weather" -> DataBinding.Weather()
            "json" -> DataBinding.Json("https://", "")
            "parameter" -> DataBinding.Parameter()
            else -> null
        }
        update { e ->
            val t = e as DesignText
            t.copy(data = binding, text = if (binding != null && "{value}" !in t.text) "{value}" else t.text)
        }
    }
    when (data) {
        is DataBinding.DateText -> {
            Field(stringResource(R.string.designer_prop_date_format), data.format, "prop-date-format") { v -> type { (it as DesignText).copy(data = DataBinding.DateText(v)) } }
        }
        is DataBinding.NextEvent -> Chips(
            stringResource(R.string.designer_prop_show_value),
            listOf(DataBinding.NextEvent.Field.Title.name to stringResource(R.string.designer_event_title), DataBinding.NextEvent.Field.Time.name to stringResource(R.string.designer_event_time)),
            data.field.name,
            "prop-event",
        ) { v -> update { (it as DesignText).copy(data = DataBinding.NextEvent(DataBinding.NextEvent.Field.valueOf(v))) } }
        is DataBinding.Reminders -> Chips(
            stringResource(R.string.designer_prop_show_value),
            listOf(DataBinding.Reminders.Field.Count.name to stringResource(R.string.designer_reminders_count), DataBinding.Reminders.Field.Next.name to stringResource(R.string.designer_reminders_next)),
            data.field.name,
            "prop-reminders",
        ) { v -> update { (it as DesignText).copy(data = DataBinding.Reminders(DataBinding.Reminders.Field.valueOf(v))) } }
        is DataBinding.Weather -> Chips(
            stringResource(R.string.designer_prop_show_value),
            listOf(DataBinding.Weather.Field.Temperature.name to stringResource(R.string.designer_weather_temperature), DataBinding.Weather.Field.Condition.name to stringResource(R.string.designer_weather_condition)),
            data.field.name,
            "prop-weather",
        ) { v -> update { (it as DesignText).copy(data = DataBinding.Weather(DataBinding.Weather.Field.valueOf(v))) } }
        is DataBinding.Json -> {
            Field(stringResource(R.string.designer_prop_json_url), data.url, "prop-json-url") { v -> type { (it as DesignText).copy(data = data.copy(url = v)) } }
            Field(stringResource(R.string.designer_prop_json_path), data.path, "prop-json-path") { v -> type { (it as DesignText).copy(data = data.copy(path = v)) } }
        }
        is DataBinding.Parameter -> {
            Field(stringResource(R.string.designer_prop_parameter_fallback), data.fallback, "prop-parameter") { v -> type { (it as DesignText).copy(data = DataBinding.Parameter(v)) } }
        }
        DataBinding.Battery, null -> Unit
    }
}

@Composable
private fun WidgetProperties(design: WidgetDesign, update: ((WidgetDesign) -> WidgetDesign) -> Unit) {
    Text(stringResource(R.string.designer_widget_title), style = MaterialTheme.typography.titleSmall)
    ColorProperty(stringResource(R.string.designer_prop_background), design.background.color, noneLabel = stringResource(R.string.designer_default)) { c -> update { it.copy(background = DesignBackground(color = c)) } }
    Labeled(stringResource(R.string.designer_prop_gradient)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GRADIENTS.forEach { colors ->
                Box(
                    Modifier
                        .size(36.dp)
                        .border(if (design.background.gradient == colors) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(3.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(colors.map(::parseColor)),
                            CircleShape,
                        )
                        .clickable { update { it.copy(background = DesignBackground(gradient = colors)) } },
                )
            }
        }
    }
    Slide(stringResource(R.string.designer_prop_edge_padding), design.padding ?: 16f, 0f..24f) { v -> update { it.copy(padding = v) } }
    Slide(stringResource(R.string.designer_prop_item_spacing), design.spacing, 0f..16f) { v -> update { it.copy(spacing = v) } }
    Chips(
        stringResource(R.string.designer_prop_refresh),
        listOf(
            "0" to stringResource(R.string.designer_default),
            "5" to stringResource(R.string.designer_refresh_minutes, 5),
            "15" to stringResource(R.string.designer_refresh_minutes, 15),
            "30" to stringResource(R.string.designer_refresh_minutes, 30),
            "60" to stringResource(R.string.designer_refresh_1h),
        ),
        (design.refreshMinutes ?: 0).toString(),
        "prop-refresh",
    ) { v -> update { it.copy(refreshMinutes = v.toInt().takeIf { m -> m > 0 }) } }
    Field(stringResource(R.string.designer_prop_tap_url), design.url.orEmpty(), "prop-url") { v -> update { it.copy(url = v.ifBlank { null }) } }
}

// --- controls ---

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun Field(label: String, value: String, tag: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    )
}

@Composable
private fun Chips(label: String, options: List<Pair<String, String>>, selected: String, tag: String, onSelect: (String) -> Unit) {
    Labeled(label) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, title) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(title) },
                    modifier = Modifier.testTag("$tag-$value"),
                )
            }
        }
    }
}

@Composable
private fun Slide(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    // The slider moves locally; the design (and its undo step) changes once the finger lifts.
    var local by remember(value) { mutableStateOf(value) }
    Labeled("$label · ${local.toInt()}") {
        Slider(
            value = local,
            onValueChange = { local = it.toInt().toFloat() },
            onValueChangeFinished = { onChange(local) },
            valueRange = range,
        )
    }
}

@Composable
private fun FontProperties(size: Float, weight: String, onChange: (Float, String) -> Unit) {
    Slide(stringResource(R.string.designer_prop_font_size), size, 8f..72f) { onChange(it, weight) }
    Chips(stringResource(R.string.designer_prop_weight), weights(), weight, "prop-weight") { onChange(size, it) }
}

@Composable
private fun ColorProperty(label: String, color: String?, noneLabel: String = stringResource(R.string.designer_default), onChange: (String?) -> Unit) {
    Labeled(label) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = color == null, onClick = { onChange(null) }, label = { Text(noneLabel) })
            COLORS.forEach { c ->
                Box(
                    Modifier
                        .size(32.dp)
                        .border(if (c == color) 3.dp else 1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .padding(3.dp)
                        .background(parseColor(c), CircleShape)
                        .clickable { onChange(c) }
                        .testTag("color-$c"),
                )
            }
        }
    }
}

private fun parseColor(argb: String): Color = Color(argb.removePrefix("#").toLong(16).toInt())

@Composable
private fun aligns(): List<Pair<String, String>> = listOf(
    "left" to stringResource(R.string.designer_align_left),
    "center" to stringResource(R.string.designer_align_center),
    "right" to stringResource(R.string.designer_align_right),
)

@Composable
private fun dateStyles(): List<Pair<String, String>> = listOf(
    "time" to stringResource(R.string.designer_date_style_time),
    "date" to stringResource(R.string.designer_date_style_date),
    "relative" to stringResource(R.string.designer_date_style_relative),
    "timer" to stringResource(R.string.designer_date_style_timer),
)

@Composable
private fun weights(): List<Pair<String, String>> = listOf(
    "light" to stringResource(R.string.designer_weight_light),
    "regular" to stringResource(R.string.designer_weight_regular),
    "medium" to stringResource(R.string.designer_weight_medium),
    "semibold" to stringResource(R.string.designer_weight_semibold),
    "bold" to stringResource(R.string.designer_weight_bold),
    "heavy" to stringResource(R.string.designer_weight_heavy),
)
private val SYMBOLS = listOf("star.fill", "heart.fill", "bolt.fill", "battery.100", "calendar", "clock", "cloud.sun.fill", "sun.max.fill", "moon.fill", "checkmark.circle.fill", "bell.fill", "house.fill")
private val COLORS = listOf(
    "#FFFFFFFF", "#FF8E8E93", "#FF000000", "#FFFF3B30", "#FFFF9500", "#FFFFCC00",
    "#FF34C759", "#FF00C7BE", "#FF0A84FF", "#FF5856D6", "#FFAF52DE", "#FFFF2D55", "#FF1C1C1E",
)
private val GRADIENTS = listOf(
    listOf("#FF2F80ED", "#FF56CCF2"),
    listOf("#FFFF5F6D", "#FFFFC371"),
    listOf("#FF11998E", "#FF38EF7D"),
    listOf("#FF8E2DE2", "#FF4A00E0"),
    listOf("#FF232526", "#FF414345"),
    listOf("#FF0F2027", "#FF2C5364"),
)
