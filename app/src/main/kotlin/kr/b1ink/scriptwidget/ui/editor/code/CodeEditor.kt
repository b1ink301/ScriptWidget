package kr.b1ink.scriptwidget.ui.editor.code

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.b1ink.scriptwidget.data.api.ApiEntry

private val Background = Color(0xFF1E1E1E)
private val Plain = Color(0xFFD4D4D4)
private val Gutter = Color(0xFF6E7681)
private val Colors = mapOf(
    JsHighlighter.Kind.Comment to Color(0xFF6A9955),
    JsHighlighter.Kind.String to Color(0xFFCE9178),
    JsHighlighter.Kind.Number to Color(0xFFB5CEA8),
    JsHighlighter.Kind.Keyword to Color(0xFF569CD6),
    JsHighlighter.Kind.Api to Color(0xFF4EC9B0),
)
private val MatchBackground = Color(0x553A8FD6)
private val FieldText = TextStyle(color = Plain)

/** Code editor: line numbers, syntax colors, API completions above the keyboard, find / replace. */
@Composable
fun CodeEditor(
    code: String,
    onChange: (String) -> Unit,
    catalog: List<ApiEntry>,
    modifier: Modifier = Modifier,
    onOpenApiReference: () -> Unit = {},
) {
    var value by remember { mutableStateOf(TextFieldValue(code)) }
    val sync = remember { ExternalTextSync() }
    // Adopt outside changes (the file loading after the first composition), not echoes of our own typing.
    LaunchedEffect(code) { if (sync.shouldAdopt(code, value.text)) value = TextFieldValue(code, TextRange(0)) }
    val highlighter = remember(catalog) { JsHighlighter(catalog.mapTo(HashSet()) { it.name }) }
    val completion = remember(catalog) { CodeCompletion(catalog) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }

    fun update(next: TextFieldValue) {
        val changed = next.text != value.text
        value = next
        if (changed) {
            sync.edited(next.text)
            onChange(next.text)
        }
    }

    val matches = remember(value.text, query, searching) { if (searching) TextSearch.matches(value.text, query) else emptyList() }
    val suggestions = remember(value) {
        if (!value.selection.collapsed) null else completion.suggest(value.text, value.selection.start).takeIf { it.items.isNotEmpty() }
    }

    Column(modifier.fillMaxSize().background(Background).imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { searching = !searching }) { Text(if (searching) "찾기 닫기" else "찾기", color = Plain) }
            TextButton(onClick = onOpenApiReference) { Text("API", color = Plain) }
            if (searching) {
                val current = matches.indexOfFirst { it.first == value.selection.start && it.last + 1 == value.selection.end }
                Text(if (query.isEmpty()) "" else "${if (current >= 0) current + 1 else 0}/${matches.size}", color = Gutter, fontSize = 12.sp)
            }
        }
        if (searching) {
            SearchBar(
                query = query, onQuery = { query = it },
                replacement = replacement, onReplacement = { replacement = it },
                onPrevious = { TextSearch.previous(value.text, query, value.selection.start)?.let { value = value.copy(selection = TextRange(it.first, it.last + 1)) } },
                onNext = { TextSearch.next(value.text, query, value.selection.end)?.let { value = value.copy(selection = TextRange(it.first, it.last + 1)) } },
                onReplace = {
                    val selected = value.text.substring(value.selection.min, value.selection.max)
                    if (query.isNotEmpty() && selected.equals(query, ignoreCase = true)) {
                        val text = value.text.replaceRange(value.selection.min, value.selection.max, replacement)
                        val after = value.selection.min + replacement.length
                        val next = TextSearch.next(text, query, after)
                        update(TextFieldValue(text, next?.let { TextRange(it.first, it.last + 1) } ?: TextRange(after)))
                    } else {
                        TextSearch.next(value.text, query, value.selection.start)?.let { value = value.copy(selection = TextRange(it.first, it.last + 1)) }
                    }
                },
                onReplaceAll = {
                    val (text, count) = TextSearch.replaceAll(value.text, query, replacement)
                    if (count > 0) update(TextFieldValue(text, TextRange(0)))
                },
            )
        }

        val lines = remember(value.text) { value.text.count { it == '\n' } + 1 }
        val textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                text = (1..lines).joinToString("\n"),
                style = textStyle.copy(color = Gutter),
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = ::update,
                textStyle = textStyle.copy(color = Plain),
                cursorBrush = SolidColor(Color(0xFF569CD6)),
                visualTransformation = remember(highlighter, matches) { SyntaxColors(highlighter, matches) },
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 8.dp, end = 8.dp, bottom = 120.dp),
            )
        }

        suggestions?.let { result ->
            LazyRow(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF252526)),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(result.items, key = { it.label }) { item ->
                    AssistChip(
                        onClick = {
                            val end = value.selection.start
                            val text = value.text.replaceRange(result.replaceFrom, end, item.insert)
                            update(TextFieldValue(text, TextRange(result.replaceFrom + item.cursorOffset)))
                        },
                        label = { Text(item.detail?.takeIf { it.contains('(') } ?: item.label, color = Plain, fontSize = 12.sp) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    replacement: String,
    onReplacement: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    // Typing goes to the search field as soon as the bar opens.
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxWidth().background(Color(0xFF252526)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true, label = { Text("찾기") }, textStyle = FieldText,
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            TextButton(onClick = onPrevious) { Text("▲") }
            TextButton(onClick = onNext) { Text("▼") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = replacement, onValueChange = onReplacement, singleLine = true, label = { Text("바꾸기") }, textStyle = FieldText,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReplace) { Text("바꾸기") }
            TextButton(onClick = onReplaceAll) { Text("모두") }
        }
    }
}

/** Colors the code without changing it (offsets map one-to-one). */
private class SyntaxColors(private val highlighter: JsHighlighter, private val matches: List<IntRange>) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val colored = buildAnnotatedString {
            append(text.text)
            for (span in highlighter.spans(text.text)) addStyle(SpanStyle(color = Colors.getValue(span.kind)), span.start, span.end)
            for (m in matches) if (m.last < text.length) addStyle(SpanStyle(background = MatchBackground), m.first, m.last + 1)
        }
        return TransformedText(colored, OffsetMapping.Identity)
    }
}
