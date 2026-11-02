package kr.b1ink.scriptwidget.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.domain.model.JsonField
import kr.b1ink.scriptwidget.domain.model.JsonWidget
import kr.b1ink.scriptwidget.domain.model.WidgetDesign

/** Values a widget can show without getting crowded. */
const val MAX_JSON_PICKS = 6

private sealed interface JsonLoad {
    data object Idle : JsonLoad
    data object Loading : JsonLoad
    data class Failed(val message: String) : JsonLoad
    data class Loaded(val url: String, val fields: List<JsonField>) : JsonLoad
}

/**
 * "From a URL": load a JSON address, tick the values to show, and get a designer design whose
 * texts read those values live (DataBinding.Json).
 */
@Composable
fun JsonWidgetDialog(
    load: suspend (String) -> List<JsonField>,
    onCreate: (WidgetDesign) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("https://") }
    var title by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<JsonLoad>(JsonLoad.Idle) }
    val picked = remember { mutableStateListOf<JsonField>() }

    fun fetch() {
        val address = url.trim()
        state = JsonLoad.Loading
        picked.clear()
        scope.launch {
            state = try {
                JsonLoad.Loaded(address, load(address))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                JsonLoad.Failed(e.message ?: e::class.java.simpleName)
            }
            if (title.isBlank()) title = JsonWidget.defaultTitle(address)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.json_widget_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.json_widget_close)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text(stringResource(R.string.json_widget_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { fetch() }),
                        modifier = Modifier.weight(1f).testTag("json-url"),
                    )
                    Button(onClick = ::fetch, enabled = state != JsonLoad.Loading, modifier = Modifier.testTag("json-load")) {
                        Text(stringResource(R.string.json_widget_load))
                    }
                }
                when (val current = state) {
                    JsonLoad.Idle -> Unit
                    JsonLoad.Loading -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    is JsonLoad.Failed -> Text(stringResource(R.string.json_widget_error, current.message), color = MaterialTheme.colorScheme.error)
                    is JsonLoad.Loaded -> if (current.fields.isEmpty()) {
                        Text(stringResource(R.string.json_widget_empty))
                    } else {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text(stringResource(R.string.json_widget_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.json_widget_pick, MAX_JSON_PICKS), style = MaterialTheme.typography.labelLarge)
                        LazyColumn(Modifier.weight(1f)) {
                            items(current.fields, key = { it.path }) { field ->
                                val checked = field in picked
                                val canPick = checked || picked.size < MAX_JSON_PICKS
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = canPick) { if (checked) picked.remove(field) else picked.add(field) }
                                        .testTag("json-field-${field.path}"),
                                ) {
                                    Checkbox(checked = checked, onCheckedChange = null, enabled = canPick)
                                    Column(Modifier.padding(start = 8.dp)) {
                                        Text(field.path, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                                        Text(field.preview, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        Button(
                            onClick = { onCreate(JsonWidget.design(title.trim(), current.url, picked.toList())) },
                            enabled = picked.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("json-create"),
                        ) {
                            Text(stringResource(R.string.json_widget_create, picked.size))
                        }
                    }
                }
            }
        }
    }
}
