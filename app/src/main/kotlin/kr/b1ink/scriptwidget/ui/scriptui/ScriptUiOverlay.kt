package kr.b1ink.scriptwidget.ui.scriptui

import kr.b1ink.scriptwidget.R
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.b1ink.scriptwidget.data.android.AndroidWebPage
import kr.b1ink.scriptwidget.data.engine.AlertResult
import kr.b1ink.scriptwidget.data.engine.DatePickerSpec
import kr.b1ink.scriptwidget.data.engine.QuickLookSpec
import kr.b1ink.scriptwidget.data.engine.TableCell
import kr.b1ink.scriptwidget.data.engine.TableEvent
import kr.b1ink.scriptwidget.data.engine.TableSpec
import kr.b1ink.scriptwidget.widget.render.ImageFileDecoder
import kr.b1ink.scriptwidget.widget.render.Styles
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Renders whatever UI the running script has requested (screens first, dialogs on top). */
@Composable
fun ScriptUiOverlay(host: ScriptUiHost) {
    val screen by host.screen.collectAsStateWithLifecycle()
    val dialog by host.dialog.collectAsStateWithLifecycle()

    when (val s = screen) {
        is UiScreen.Table -> TableScreen(s.spec, s.respond)
        is UiScreen.QuickLook -> QuickLookScreen(s.spec, s.close)
        is UiScreen.Web -> WebScreen(s)
        null -> Unit
    }
    when (val d = dialog) {
        is UiDialog.Alert -> if (d.spec.style == "sheet") AlertSheet(d) else AlertDialogContent(d)
        is UiDialog.DatePicker -> DatePickerContent(d.spec, d.respond)
        is UiDialog.Documents -> DocumentPickerLauncher(d)
        is UiDialog.Dictation -> DictationLauncher(d)
        is UiDialog.ImagePicker -> ImagePickerLauncher(d)
        null -> Unit
    }
}

// ---------- Alert ----------

@Composable
private fun AlertDialogContent(request: UiDialog.Alert) {
    val spec = request.spec
    val values = remember(request) { mutableStateListOf(*spec.textFields.map { it.text }.toTypedArray()) }
    fun answer(index: Int) = request.respond(AlertResult(index, values.toList()))

    AlertDialog(
        onDismissRequest = { answer(-1) },
        title = spec.title?.let { { Text(it) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                spec.message?.let { Text(it) }
                spec.textFields.forEachIndexed { i, field ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { values[i] = it },
                        placeholder = { Text(field.placeholder) },
                        singleLine = true,
                        visualTransformation = if (field.secure) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                        keyboardOptions = if (field.secure) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                spec.actions.forEachIndexed { i, action ->
                    TextButton(onClick = { answer(if (action.role == "cancel") -1 else i) }) {
                        Text(
                            action.title,
                            color = if (action.role == "destructive") MaterialTheme.colorScheme.error else Color.Unspecified,
                            fontWeight = if (action.role == "cancel") FontWeight.Bold else null,
                        )
                    }
                }
                if (spec.actions.isEmpty()) TextButton(onClick = { answer(-1) }) { Text("OK") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertSheet(request: UiDialog.Alert) {
    val spec = request.spec
    ModalBottomSheet(onDismissRequest = { request.respond(AlertResult(-1)) }) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            spec.title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            spec.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            spec.actions.forEachIndexed { i, action ->
                TextButton(
                    onClick = { request.respond(AlertResult(if (action.role == "cancel") -1 else i)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(action.title, color = if (action.role == "destructive") MaterialTheme.colorScheme.error else Color.Unspecified)
                }
            }
        }
    }
}

// ---------- DatePicker ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerContent(spec: DatePickerSpec, respond: (Long?) -> Unit) {
    val zone = ZoneId.systemDefault()
    val initial = spec.initial?.let { Instant.ofEpochMilli(it).atZone(zone) }
    var pickedDate by remember(spec) { mutableStateOf<LocalDate?>(null) }

    when {
        spec.mode == "countdown" -> CountdownDialog(spec.countdownSeconds ?: 0, respond)
        spec.mode == "time" || (spec.mode == "dateAndTime" && pickedDate != null) -> {
            val state = rememberTimePickerState(initialHour = initial?.hour ?: 9, initialMinute = initial?.minute ?: 0)
            AlertDialog(
                onDismissRequest = { respond(null) },
                text = { TimePicker(state = state) },
                confirmButton = {
                    TextButton(onClick = {
                        val date = pickedDate ?: LocalDate.now(zone)
                        respond(date.atTime(LocalTime.of(state.hour, state.minute)).atZone(zone).toInstant().toEpochMilli())
                    }) { Text(stringResource(R.string.scriptui_ok)) }
                },
                dismissButton = { TextButton(onClick = { respond(null) }) { Text(stringResource(R.string.scriptui_cancel)) } },
            )
        }
        else -> {
            val min = spec.min
            val max = spec.max
            val state = rememberDatePickerState(
                initialSelectedDateMillis = initial?.toLocalDate()?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val day = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        val afterMin = min == null || !day.isBefore(Instant.ofEpochMilli(min).atZone(zone).toLocalDate())
                        val beforeMax = max == null || !day.isAfter(Instant.ofEpochMilli(max).atZone(zone).toLocalDate())
                        return afterMin && beforeMax
                    }
                },
            )
            DatePickerDialog(
                onDismissRequest = { respond(null) },
                confirmButton = {
                    TextButton(onClick = {
                        val selected = state.selectedDateMillis ?: return@TextButton
                        val day = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                        if (spec.mode == "dateAndTime") pickedDate = day
                        else respond(day.atStartOfDay(zone).toInstant().toEpochMilli())
                    }) { Text(stringResource(if (spec.mode == "dateAndTime") R.string.scriptui_next else R.string.scriptui_ok)) }
                },
                dismissButton = { TextButton(onClick = { respond(null) }) { Text(stringResource(R.string.scriptui_cancel)) } },
            ) { DatePicker(state = state) }
        }
    }
}

@Composable
private fun CountdownDialog(initialSeconds: Int, respond: (Long?) -> Unit) {
    var hours by remember { mutableStateOf((initialSeconds / 3600).toString()) }
    var minutes by remember { mutableStateOf((initialSeconds % 3600 / 60).toString()) }
    var seconds by remember { mutableStateOf((initialSeconds % 60).toString()) }
    AlertDialog(
        onDismissRequest = { respond(null) },
        title = { Text(stringResource(R.string.scriptui_time)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.scriptui_hours to hours, R.string.scriptui_minutes to minutes, R.string.scriptui_seconds to seconds).forEachIndexed { i, (label, value) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { v ->
                            val digits = v.filter(Char::isDigit).take(3)
                            when (i) { 0 -> hours = digits; 1 -> minutes = digits; else -> seconds = digits }
                        },
                        label = { Text(stringResource(label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val total = (hours.toLongOrNull() ?: 0) * 3600 + (minutes.toLongOrNull() ?: 0) * 60 + (seconds.toLongOrNull() ?: 0)
                respond(total * 1000)
            }) { Text(stringResource(R.string.scriptui_ok)) }
        },
        dismissButton = { TextButton(onClick = { respond(null) }) { Text(stringResource(R.string.scriptui_cancel)) } },
    )
}

// ---------- Full screens ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FullScreen(onClose: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = { TopAppBar(title = {}, navigationIcon = { TextButton(onClick = onClose) { Text(stringResource(R.string.scriptui_close)) } }) },
            ) { padding -> Box(Modifier.fillMaxSize().padding(padding)) { content() } }
        }
    }
}

const val TABLE_IMAGE_TAG = "table-image"

@Composable
private fun TableScreen(spec: TableSpec, respond: (TableEvent) -> Unit) {
    FullScreen(onClose = { respond(TableEvent.Dismissed) }) {
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(spec.rows) { rowIndex, row ->
                var modifier = Modifier.fillMaxWidth().heightIn(min = (row.height ?: 44f).dp)
                row.backgroundColor?.let { modifier = modifier.background(Styles.color(it, Color.Transparent)) }
                if (row.selectable) modifier = modifier.clickable { respond(TableEvent.Selected(rowIndex)) }
                Row(modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.cells.forEachIndexed { cellIndex, cell ->
                        Box(Modifier.weight(cell.widthWeight ?: 1f), contentAlignment = cell.alignment()) {
                            TableCellContent(cell, row.isHeader, row.height) { respond(TableEvent.CellTapped(rowIndex, cellIndex)) }
                        }
                    }
                }
                if (spec.showSeparators) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TableCellContent(cell: TableCell, header: Boolean, rowHeight: Float?, onTap: () -> Unit) {
    val align = when (cell.align) { "center" -> TextAlign.Center; "right" -> TextAlign.End; else -> TextAlign.Start }
    when (cell.type) {
        "button" -> TextButton(onClick = onTap) { Text(cell.title.orEmpty()) }
        "image" -> {
            val bitmap = remember(cell.src) { cell.src?.let { ImageFileDecoder.decode(it, Styles.MAX_IMAGE_PX) } }
            // Rows with an explicit height (e.g. screenshots in settings tables) show the image that tall.
            val size = rowHeight?.takeIf { it > 52f }?.let { Modifier.fillMaxWidth().height((it - 12f).dp) } ?: Modifier.size(40.dp)
            bitmap?.let {
                Image(
                    it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = size.testTag(TABLE_IMAGE_TAG),
                )
            }
        }
        else -> Column {
            cell.title?.let {
                Text(
                    it,
                    textAlign = align,
                    fontWeight = if (header) FontWeight.Bold else null,
                    color = cell.titleColor?.let { c -> Styles.color(c, Color.Unspecified) } ?: Color.Unspecified,
                )
            }
            cell.subtitle?.let {
                Text(
                    it,
                    textAlign = align,
                    style = MaterialTheme.typography.bodySmall,
                    color = cell.subtitleColor?.let { c -> Styles.color(c, Color.Unspecified) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun TableCell.alignment() = when (align) {
    "center" -> Alignment.Center
    "right" -> Alignment.CenterEnd
    else -> Alignment.CenterStart
}

@Composable
private fun QuickLookScreen(spec: QuickLookSpec, close: () -> Unit) {
    FullScreen(onClose = close) {
        val src = spec.src
        if (src != null) {
            val bitmap = remember(src) { ImageFileDecoder.decode(src, 2048) }
            bitmap?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        } else {
            Text(
                spec.text.orEmpty(),
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            )
        }
    }
}

// ---------- WebView ----------

@Composable
private fun WebScreen(screen: UiScreen.Web) {
    val page = screen.page as? AndroidWebPage ?: return
    var view by remember(page) { mutableStateOf<android.webkit.WebView?>(null) }
    LaunchedEffect(page) { view = page.view() }
    FullScreen(onClose = screen.close) {
        view?.let { webView ->
            AndroidView(
                factory = { (webView.parent as? android.view.ViewGroup)?.removeView(webView); webView },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------- DocumentPicker ----------

/** Launches the system picker once per request and answers with the chosen content URIs. */
@Composable
private fun DocumentPickerLauncher(dialog: UiDialog.Documents) {
    val request = dialog.request
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        dialog.respond(listOfNotNull(uri?.toString()))
    }
    val openMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        dialog.respond(uris.map { it.toString() })
    }
    val create = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(request.mimeTypes.firstOrNull() ?: "application/octet-stream")
    ) { uri -> dialog.respond(listOfNotNull(uri?.toString())) }

    LaunchedEffect(dialog) {
        when {
            request.kind == "create" -> create.launch(request.suggestedName ?: "Untitled")
            request.multiple -> openMany.launch(request.mimeTypes.toTypedArray())
            else -> open.launch(request.mimeTypes.toTypedArray())
        }
    }
}

// ---------- Dictation ----------

@Composable
private fun DictationLauncher(dialog: UiDialog.Dictation) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        dialog.respond(text)
    }
    LaunchedEffect(dialog) {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        dialog.locale?.let { intent.putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, it) }
        try {
            launcher.launch(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            dialog.respond(null)
        }
    }
}

// ---------- Photos ----------

@Composable
private fun ImagePickerLauncher(dialog: UiDialog.ImagePicker) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val library = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        dialog.respond(uri?.toString())
    }
    // The camera writes into the script storage, exposed through the app's FileProvider.
    val cameraTarget = remember(dialog) {
        val file = java.io.File(context.filesDir, "script_storage/tmp/camera/photo_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.scriptfiles", file)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        dialog.respond(if (saved) cameraTarget.toString() else null)
    }
    LaunchedEffect(dialog) {
        try {
            if (dialog.source == "camera") camera.launch(cameraTarget)
            else library.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (e: android.content.ActivityNotFoundException) {
            dialog.respond(null)
        }
    }
}
