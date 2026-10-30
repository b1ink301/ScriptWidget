package kr.b1ink.scriptwidget.ui.editor

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.data.android.AndroidLocationProvider
import kr.b1ink.scriptwidget.data.android.AndroidNotifier
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.permissions.AndroidPermissionStatus
import kr.b1ink.scriptwidget.permissions.AppPermission
import kr.b1ink.scriptwidget.permissions.PermissionState
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.pin.WidgetPinner
import kr.b1ink.scriptwidget.ui.preview.WidgetPreview
import kr.b1ink.scriptwidget.ui.scriptui.ScriptUiOverlay
import androidx.compose.ui.res.stringResource
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.ui.text.asString

@Composable
fun EditorRoute(
    container: AppContainer,
    scriptName: String,
    onBack: () -> Unit,
    autoRun: RunRequest? = null,
    onAutoRunConsumed: () -> Unit = {},
    onOpenApiReference: () -> Unit = {},
    onOpenDesigner: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm = viewModel(key = "editor-$scriptName") {
        EditorViewModel(
            scriptName,
            container.getScript,
            container.saveScript,
            container.renameScript,
            container.runScript,
            PermissionChecker { permission ->
                when (permission) {
                    ScriptPermission.Location -> AndroidLocationProvider.hasLocationPermission(context)
                    ScriptPermission.Notifications -> AndroidNotifier.canPost(context)
                    ScriptPermission.Calendar -> AndroidPermissionStatus(context).state(AppPermission.Calendar) == PermissionState.Granted
                    ScriptPermission.Contacts -> AndroidPermissionStatus(context).state(AppPermission.Contacts) == PermissionState.Granted
                    ScriptPermission.Photos -> AndroidPermissionStatus(context).state(AppPermission.Photos) == PermissionState.Granted
                }
            },
        )
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionsResult()
    }
    // While the editor is visible, scripts it runs may present UI (Alert, UITable, …).
    DisposableEffect(container.scriptUi) {
        container.scriptUi.attach()
        onDispose { container.scriptUi.detach() }
    }

    LaunchedEffect(autoRun) {
        // Opened from a link, a notification or the share sheet: run right away with that context.
        autoRun?.let {
            vm.run(it)
            onAutoRunConsumed()
        }
    }
    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is EditorEvent.RequestPermissions -> {
                    val androidPermissions = event.permissions.flatMap { it.androidPermissions() }
                    if (androidPermissions.isEmpty()) {
                        vm.onPermissionsResult()
                    } else {
                        permissionLauncher.launch(androidPermissions.toTypedArray())
                    }
                }
            }
        }
    }

    val catalog by androidx.compose.runtime.produceState(emptyList<kr.b1ink.scriptwidget.data.api.ApiEntry>(), container) {
        value = container.apiCatalog()
    }
    // Scripts made in the designer can go back to it.
    val designed = state.code?.let { kr.b1ink.scriptwidget.domain.model.DesignHeader.read(it) !is kr.b1ink.scriptwidget.domain.model.DesignRead.Plain } == true
    EditorScreen(
        state = state,
        catalog = catalog,
        onOpenDesigner = if (designed) ({ scope.launch { vm.saveIfDirty(); onOpenDesigner() } }) else null,
        onOpenApiReference = onOpenApiReference,
        onBack = { scope.launch { vm.saveIfDirty(); onBack() } },
        onCodeChange = vm::onCodeChange,
        onSave = vm::save,
        onRun = vm::run,
        onRename = vm::rename,
        onDismissMessage = vm::dismissMessage,
        onSelectTab = vm::selectTab,
        onSelectFamily = vm::selectFamily,
        onParameterChange = vm::onParameterChange,
        overlay = { ScriptUiOverlay(container.scriptUi) },
        onPin = {
            scope.launch {
                vm.saveIfDirty()
                if (!WidgetPinner.pin(context, state.name, state.parameter.ifBlank { null })) {
                    Toast.makeText(context, context.getString(R.string.editor_pin_unsupported), Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}

private fun ScriptPermission.androidPermissions(): List<String> = when (this) {
    ScriptPermission.Location -> listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    ScriptPermission.Notifications ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    ScriptPermission.Calendar -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    ScriptPermission.Contacts -> listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)
    ScriptPermission.Photos ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) listOf(Manifest.permission.READ_MEDIA_IMAGES)
        else listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    state: EditorUiState,
    onBack: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSave: () -> Unit,
    onRun: () -> Unit,
    onRename: (String) -> Unit,
    onDismissMessage: () -> Unit,
    onSelectTab: (EditorTab) -> Unit,
    onSelectFamily: (WidgetFamily) -> Unit,
    onParameterChange: (String) -> Unit,
    onPin: () -> Unit,
    overlay: @Composable () -> Unit = {},
    catalog: List<kr.b1ink.scriptwidget.data.api.ApiEntry> = emptyList(),
    onOpenApiReference: () -> Unit = {},
    onOpenDesigner: (() -> Unit)? = null,
) {
    var renaming by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                title = { Text(text = state.name + if (state.dirty) " •" else "", maxLines = 1) },
                actions = {
                    onOpenDesigner?.let { TextButton(onClick = it, modifier = Modifier.testTag("open-designer")) { Text(stringResource(R.string.editor_design)) } }
                    TextButton(onClick = { renaming = true }) { Text(stringResource(R.string.editor_rename)) }
                    TextButton(onClick = onPin) { Text(stringResource(R.string.editor_pin)) }
                    TextButton(onClick = onSave, enabled = state.dirty) { Text(stringResource(R.string.editor_save)) }
                    TextButton(onClick = onRun, enabled = !state.running && state.code != null) { Text(stringResource(R.string.editor_run)) }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.running) LinearProgressIndicator(Modifier.fillMaxWidth())
            PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
                Tab(selected = state.tab == EditorTab.Code, onClick = { onSelectTab(EditorTab.Code) }, text = { Text(stringResource(R.string.editor_tab_code)) })
                Tab(selected = state.tab == EditorTab.Result, onClick = { onSelectTab(EditorTab.Result) }, text = { Text(stringResource(R.string.editor_tab_result)) })
            }
            when (state.tab) {
                EditorTab.Code -> kr.b1ink.scriptwidget.ui.editor.code.CodeEditor(
                    code = state.code.orEmpty(), onChange = onCodeChange, catalog = catalog, modifier = Modifier.weight(1f),
                    onOpenApiReference = onOpenApiReference,
                )
                EditorTab.Result -> ResultPanel(
                    result = state.result,
                    family = state.family,
                    onFamilyChange = onSelectFamily,
                    parameter = state.parameter,
                    onParameterChange = onParameterChange,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }

    overlay()

    if (renaming) {
        var newName by remember { mutableStateOf(state.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(stringResource(R.string.editor_rename_title)) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    onRename(newName)
                    renaming = false
                }) { Text(stringResource(R.string.editor_rename_confirm)) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.editor_cancel)) } },
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissMessage,
            text = { Text(message.asString()) },
            confirmButton = { TextButton(onClick = onDismissMessage) { Text(stringResource(R.string.editor_ok)) } },
        )
    }
}

@Composable
private fun ResultPanel(
    result: ScriptRunResult?,
    family: WidgetFamily,
    onFamilyChange: (WidgetFamily) -> Unit,
    parameter: String,
    onParameterChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetFamily.entries.forEach {
                FilterChip(selected = family == it, onClick = { onFamilyChange(it) }, label = { Text(it.jsName) })
            }
        }
        OutlinedTextField(
            value = parameter,
            onValueChange = onParameterChange,
            label = { Text("args.widgetParameter") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (result == null) {
            Text(stringResource(R.string.editor_run_hint), style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        result.widget?.let { root ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                WidgetPreview(root = root, family = family)
            }
        }
        result.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }

        val t = result.timings
        Text(
            stringResource(R.string.editor_timings, t.createMs, t.preludeMs, t.scriptMs, t.totalMs),
            style = MaterialTheme.typography.labelMedium,
        )

        HorizontalDivider()
        Text(stringResource(R.string.editor_console), style = MaterialTheme.typography.titleSmall)
        if (result.logs.isEmpty()) {
            Text(stringResource(R.string.editor_no_output), style = MaterialTheme.typography.bodySmall)
        }
        result.logs.forEach { line ->
            Text(
                text = line.message,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = when (line.level) {
                    "error" -> MaterialTheme.colorScheme.error
                    "warn" -> Color(0xFFE0A100)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
