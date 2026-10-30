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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val onWrap: (String) -> Unit,
    val onRemove: () -> Unit,
)

private enum class Panel(val title: String) { Outline("구조"), Properties("속성") }

/** Widget designer: live preview on top, the element tree and the selected element's properties below. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignerScreen(state: DesignerUiState, actions: DesignerActions) {
    var panel by remember { mutableStateOf(if (state.selected != null) Panel.Properties else Panel.Outline) }
    // Selecting something (in the preview or the outline) shows its properties.
    var lastSelected by remember { mutableStateOf(state.selected) }
    LaunchedEffect(state.selected) {
        if (state.selected != lastSelected && state.selected != null) panel = Panel.Properties
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
                    TextButton(onClick = actions.onPin) { Text("고정") }
                    TextButton(onClick = actions.onShowCode) { Text("코드") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.editedByHand) {
                Notice("코드를 직접 고친 스크립트입니다. 여기서 바꾸면 코드가 디자인대로 다시 만들어져 직접 고친 부분은 사라집니다.")
            }
            if (state.notDesigned) {
                Notice("디자이너로 만든 스크립트가 아닙니다. 여기서 저장하면 기존 코드가 디자인으로 바뀝니다.")
            }
            PreviewArea(state, actions)
            TabRow(selectedTabIndex = panel.ordinal) {
                Panel.entries.forEach { p ->
                    Tab(selected = panel == p, onClick = { panel = p }, text = { Text(p.title) }, modifier = Modifier.testTag("panel-${p.name}"))
                }
            }
            if (state.selected != null) Toolbar(actions)
            Box(Modifier.weight(1f)) {
                when (panel) {
                    Panel.Outline -> Outline(state, actions)
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
            listOf(WidgetFamily.Small to "소", WidgetFamily.Medium to "중", WidgetFamily.Large to "대").forEach { (family, label) ->
                FilterChip(selected = state.family == family, onClick = { actions.onFamily(family) }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(6.dp))
        val widget = state.preview?.widget
        when {
            widget != null -> WidgetPreview(widget, state.family, selected = state.selected, onSelect = actions.onSelect)
            state.preview?.error != null -> Text(
                "미리보기 오류: ${state.preview.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
            else -> Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) { Text("그리는 중…") }
        }
        Text(
            if (state.running) "갱신 중…" else "미리보기는 실제 위젯 엔진으로 그립니다 · 요소를 탭해 선택",
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
        TextButton(onClick = { actions.onMove(-1) }, modifier = Modifier.testTag("tool-up")) { Text("↑ 위로") }
        TextButton(onClick = { actions.onMove(1) }, modifier = Modifier.testTag("tool-down")) { Text("↓ 아래로") }
        TextButton(onClick = { actions.onWrap("v") }, modifier = Modifier.testTag("tool-wrap")) { Text("스택으로 묶기") }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = actions.onRemove, modifier = Modifier.testTag("tool-remove")) {
            Text("삭제", color = MaterialTheme.colorScheme.error)
        }
    }
}

// --- outline ---

@Composable
private fun Outline(state: DesignerUiState, actions: DesignerActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        OutlineRow("🧩 위젯 (배경·여백·갱신)", depth = 0, selected = state.selected == null, tag = "outline-widget") { actions.onSelect(null) }
        fun rows(list: List<DesignElement>, path: List<Int>): List<Pair<List<Int>, DesignElement>> =
            list.flatMapIndexed { i, e -> listOf((path + i) to e) + if (e is DesignStack) rows(e.children, path + i) else emptyList() }
        rows(state.design.children, emptyList()).forEach { (path, element) ->
            OutlineRow(label(element), depth = path.size, selected = state.selected == path, tag = "outline-" + path.joinToString("-")) {
                actions.onSelect(path)
            }
        }
        if (state.design.children.isEmpty()) {
            Text(
                "아래 + 요소로 텍스트·데이터·심볼을 추가하세요.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
        AddMenu(actions, inside = (state.selected?.let(state.design::elementAt) as? DesignStack) != null)
    }
}

@Composable
private fun OutlineRow(text: String, depth: Int, selected: Boolean, tag: String, onClick: () -> Unit) {
    Text(
        text,
        maxLines = 1,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = (16 + depth * 20).dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
    )
}

private fun label(e: DesignElement): String = when (e) {
    is DesignText -> (e.data?.let { "📊 ${dataLabel(it)}" } ?: "🔤 텍스트") + " · " + e.text.replace("\n", " ").take(24)
    is DesignDate -> "🕒 " + (DATE_STYLES.firstOrNull { it.first == e.style }?.second ?: "시각")
    is DesignSymbol -> "⭐ 심볼 · ${e.name}"
    is DesignImage -> "🖼 이미지"
    is DesignSpacer -> "↕ 간격" + (e.length?.let { " ${it.toInt()}" } ?: " (늘어남)")
    is DesignStack -> if (e.dir == "v") "▤ 세로 스택" else "▥ 가로 스택"
}

private fun dataLabel(b: DataBinding): String = when (b) {
    is DataBinding.DateText -> "날짜 문구"
    DataBinding.Battery -> "배터리"
    is DataBinding.NextEvent -> "다음 일정"
    is DataBinding.Reminders -> "할 일"
    is DataBinding.Weather -> "날씨"
    is DataBinding.Json -> "JSON 값"
    is DataBinding.Parameter -> "위젯 파라미터"
}

@Composable
private fun AddMenu(actions: DesignerActions, inside: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(16.dp)) {
        FilledTonalButton(onClick = { open = true }, modifier = Modifier.testTag("add-element")) {
            Text(if (inside) "+ 요소 (선택한 스택 안에)" else "+ 요소")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Text("요소", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            ELEMENTS.forEach { (title, element) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { open = false; actions.onAdd(element) })
            }
            HorizontalDivider()
            Text("데이터 블록", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            DATA_BLOCKS.forEach { (title, element) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { open = false; actions.onAdd(element) })
            }
        }
    }
}

private val ELEMENTS: List<Pair<String, DesignElement>> = listOf(
    "텍스트" to DesignText("텍스트"),
    "시각 (실시간)" to DesignDate(style = "time", size = 28f, weight = "bold"),
    "심볼" to DesignSymbol("star.fill", color = "#FFFFCC00"),
    "이미지 (URL)" to DesignImage("https://", size = 60f, cornerRadius = 8f),
    "간격" to DesignSpacer(),
    "가로 스택" to DesignStack(dir = "h", spacing = 6f),
    "세로 스택" to DesignStack(dir = "v", spacing = 4f),
)

private val DATA_BLOCKS: List<Pair<String, DesignElement>> = listOf(
    "날짜 문구" to DesignText("{value}", data = DataBinding.DateText()),
    "배터리" to DesignText("{value}%", data = DataBinding.Battery, size = 24f, weight = "bold"),
    "다음 일정" to DesignText("{value}", data = DataBinding.NextEvent(), weight = "semibold", lineLimit = 2),
    "할 일" to DesignText("{value}개 남음", data = DataBinding.Reminders()),
    "날씨" to DesignText("{value}°", data = DataBinding.Weather(), size = 32f, weight = "bold"),
    "JSON 값 (URL)" to DesignText("{value}", data = DataBinding.Json("https://", "")),
    "위젯 파라미터" to DesignText("{value}", data = DataBinding.Parameter("파라미터 없음")),
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
                Chips("표시", DATE_STYLES, element.style, "prop-date") { s -> update { (it as DesignDate).copy(style = s) } }
                FontProperties(element.size, element.weight) { size, weight -> update { (it as DesignDate).copy(size = size, weight = weight) } }
                ColorProperty("색", element.color) { c -> update { (it as DesignDate).copy(color = c) } }
                Chips("정렬", ALIGNS, element.align, "prop-align") { a -> update { (it as DesignDate).copy(align = a) } }
            }
            is DesignSymbol -> {
                Field("심볼 이름 (SF Symbols)", element.name, "prop-symbol") { v -> type { (it as DesignSymbol).copy(name = v) } }
                Chips("자주 쓰는 심볼", SYMBOLS.map { it to it }, element.name, "prop-symbol-pick") { v -> update { (it as DesignSymbol).copy(name = v) } }
                Slide("크기", element.size, 8f..80f) { v -> update { (it as DesignSymbol).copy(size = v) } }
                ColorProperty("색", element.color) { c -> update { (it as DesignSymbol).copy(color = c) } }
            }
            is DesignImage -> {
                Field("이미지 URL", element.url, "prop-image") { v -> type { (it as DesignImage).copy(url = v) } }
                Slide("크기", element.size ?: 60f, 16f..200f) { v -> update { (it as DesignImage).copy(size = v) } }
                Slide("모서리", element.cornerRadius, 0f..40f) { v -> update { (it as DesignImage).copy(cornerRadius = v) } }
            }
            is DesignSpacer -> {
                Chips("간격", listOf("flex" to "남는 공간 채우기", "fixed" to "고정"), if (element.length == null) "flex" else "fixed", "prop-spacer") { v ->
                    update { DesignSpacer(if (v == "flex") null else 8f) }
                }
                element.length?.let { length -> Slide("길이", length, 0f..80f) { v -> update { DesignSpacer(v) } } }
            }
            is DesignStack -> {
                Chips("방향", listOf("h" to "가로", "v" to "세로"), element.dir, "prop-dir") { v -> update { (it as DesignStack).copy(dir = v) } }
                if (element.dir == "h") {
                    Chips("세로 정렬", listOf("top" to "위", "center" to "가운데", "bottom" to "아래"), element.align, "prop-stack-align") { v ->
                        update { (it as DesignStack).copy(align = v) }
                    }
                }
                Slide("요소 사이 간격", element.spacing, 0f..24f) { v -> update { (it as DesignStack).copy(spacing = v) } }
                Slide("안쪽 여백", element.padding ?: 0f, 0f..24f) { v -> update { (it as DesignStack).copy(padding = v.takeIf { p -> p > 0 }) } }
                ColorProperty("배경색", element.bg) { c -> update { (it as DesignStack).copy(bg = c) } }
                Slide("모서리", element.cornerRadius, 0f..30f) { v -> update { (it as DesignStack).copy(cornerRadius = v) } }
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
        if (element.data != null) "문구 ({value} 자리에 데이터가 들어갑니다)" else "문구",
        element.text,
        "prop-text",
        singleLine = false,
    ) { v -> type { (it as DesignText).copy(text = v) } }
    DataProperty(element.data, update, type)
    FontProperties(element.size, element.weight) { size, weight -> update { (it as DesignText).copy(size = size, weight = weight) } }
    ColorProperty("색", element.color) { c -> update { (it as DesignText).copy(color = c) } }
    Chips("정렬", ALIGNS, element.align, "prop-align") { a -> update { (it as DesignText).copy(align = a) } }
    Chips("줄 수", listOf("0" to "제한 없음", "1" to "1줄", "2" to "2줄", "3" to "3줄"), element.lineLimit.toString(), "prop-lines") { v ->
        update { (it as DesignText).copy(lineLimit = v.toInt()) }
    }
    Chips("넘치면", listOf("1.0" to "말줄임", "0.5" to "글자 줄이기"), if (element.minimumScaleFactor < 1f) "0.5" else "1.0", "prop-shrink") { v ->
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
        "none" to "없음", "date" to "날짜", "battery" to "배터리", "event" to "다음 일정",
        "reminders" to "할 일", "weather" to "날씨", "json" to "JSON", "parameter" to "파라미터",
    )
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
    Chips("데이터", options, current, "prop-data") { key ->
        val binding = when (key) {
            "date" -> DataBinding.DateText()
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
            Field("형식 (예: M월 d일 EEEE, HH:mm)", data.format, "prop-date-format") { v -> type { (it as DesignText).copy(data = DataBinding.DateText(v)) } }
        }
        is DataBinding.NextEvent -> Chips(
            "보여 줄 값",
            listOf(DataBinding.NextEvent.Field.Title.name to "제목", DataBinding.NextEvent.Field.Time.name to "시간"),
            data.field.name,
            "prop-event",
        ) { v -> update { (it as DesignText).copy(data = DataBinding.NextEvent(DataBinding.NextEvent.Field.valueOf(v))) } }
        is DataBinding.Reminders -> Chips(
            "보여 줄 값",
            listOf(DataBinding.Reminders.Field.Count.name to "남은 개수", DataBinding.Reminders.Field.Next.name to "가장 급한 할 일"),
            data.field.name,
            "prop-reminders",
        ) { v -> update { (it as DesignText).copy(data = DataBinding.Reminders(DataBinding.Reminders.Field.valueOf(v))) } }
        is DataBinding.Weather -> Chips(
            "보여 줄 값",
            listOf(DataBinding.Weather.Field.Temperature.name to "기온", DataBinding.Weather.Field.Condition.name to "날씨"),
            data.field.name,
            "prop-weather",
        ) { v -> update { (it as DesignText).copy(data = DataBinding.Weather(DataBinding.Weather.Field.valueOf(v))) } }
        is DataBinding.Json -> {
            Field("JSON 주소", data.url, "prop-json-url") { v -> type { (it as DesignText).copy(data = data.copy(url = v)) } }
            Field("값 경로 (예: data.0.name)", data.path, "prop-json-path") { v -> type { (it as DesignText).copy(data = data.copy(path = v)) } }
        }
        is DataBinding.Parameter -> {
            Field("파라미터가 없을 때", data.fallback, "prop-parameter") { v -> type { (it as DesignText).copy(data = DataBinding.Parameter(v)) } }
        }
        DataBinding.Battery, null -> Unit
    }
}

@Composable
private fun WidgetProperties(design: WidgetDesign, update: ((WidgetDesign) -> WidgetDesign) -> Unit) {
    Text("위젯 전체", style = MaterialTheme.typography.titleSmall)
    ColorProperty("배경색", design.background.color, noneLabel = "기본") { c -> update { it.copy(background = DesignBackground(color = c)) } }
    Labeled("그라데이션 배경") {
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
    Slide("가장자리 여백", design.padding ?: 16f, 0f..24f) { v -> update { it.copy(padding = v) } }
    Slide("요소 사이 간격", design.spacing, 0f..16f) { v -> update { it.copy(spacing = v) } }
    Chips(
        "자동 갱신 요청",
        listOf("0" to "기본", "5" to "5분", "15" to "15분", "30" to "30분", "60" to "1시간"),
        (design.refreshMinutes ?: 0).toString(),
        "prop-refresh",
    ) { v -> update { it.copy(refreshMinutes = v.toInt().takeIf { m -> m > 0 }) } }
    Field("탭하면 열 주소 (선택)", design.url.orEmpty(), "prop-url") { v -> update { it.copy(url = v.ifBlank { null }) } }
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
    Slide("글자 크기", size, 8f..72f) { onChange(it, weight) }
    Chips("굵기", WEIGHTS, weight, "prop-weight") { onChange(size, it) }
}

@Composable
private fun ColorProperty(label: String, color: String?, noneLabel: String = "기본", onChange: (String?) -> Unit) {
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

private val ALIGNS = listOf("left" to "왼쪽", "center" to "가운데", "right" to "오른쪽")
private val DATE_STYLES = listOf("time" to "시각", "date" to "날짜", "relative" to "상대 시간", "timer" to "타이머")
private val WEIGHTS = listOf("light" to "가늘게", "regular" to "보통", "medium" to "중간", "semibold" to "약간 굵게", "bold" to "굵게", "heavy" to "아주 굵게")
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
