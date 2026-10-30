package kr.b1ink.scriptwidget.ui.list

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import kr.b1ink.scriptwidget.ui.main.LocalBottomBarSpace
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.model.ScriptAppearance
import kr.b1ink.scriptwidget.domain.model.ScriptSummary
import kr.b1ink.scriptwidget.permissions.AppPermission
import kr.b1ink.scriptwidget.ui.permissions.title
import androidx.compose.ui.res.stringResource
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.domain.model.DesignTemplates
import kr.b1ink.scriptwidget.ui.text.asString

@Composable
fun ScriptListRoute(
    container: AppContainer,
    onOpen: (String) -> Unit,
    onNewDesign: (kr.b1ink.scriptwidget.domain.model.DesignTemplates) -> Unit = {},
    widgetRecommendations: List<AppPermission> = emptyList(),
    onOpenPermissions: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenApiReference: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    incomingFiles: List<String> = emptyList(),
    onIncomingFilesConsumed: () -> Unit = {},
    /** Import / backup / restore asked for in settings; run here, where the result shows. */
    listAction: kr.b1ink.scriptwidget.ui.main.ListAction? = null,
    onListActionDone: () -> Unit = {},
) {
    val vm = viewModel {
        ScriptListViewModel(
            seedSamples = container.seedSamples,
            createScript = container.createScript,
            deleteScript = container.deleteScript,
            importScripts = container.importScripts,
            exportScript = container.exportScript,
            summaries = container.scriptSummaries,
            setAppearance = container.setScriptAppearance,
            backupScripts = container.backupScripts,
            restoreScripts = container.restoreScripts,
            activity = container.scriptActivity(),
        )
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val shortcutUnsupported = androidx.compose.ui.res.stringResource(R.string.list_shortcut_unsupported)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var exporting by rememberSaveable { mutableStateOf<String?>(null) }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.importFiles(uris.map(Uri::toString))
    }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backupTo(uri.toString())
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreFrom(uri.toString())
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/javascript")) { uri ->
        val name = exporting
        exporting = null
        if (uri != null && name != null) vm.exportTo(name, uri.toString())
    }

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is ScriptListEvent.OpenEditor -> onOpen(event.name)
                is ScriptListEvent.ShowMessage -> snackbar.showSnackbar(
                    event.lines.joinToString("\n") { it.resolve(context) },
                    withDismissAction = true, duration = SnackbarDuration.Long)
                is ScriptListEvent.ChooseBackupTarget -> backupPicker.launch(event.fileName)
                is ScriptListEvent.ChooseExportTarget -> {
                    exporting = event.scriptName
                    exportPicker.launch(event.fileName)
                }
            }
        }
    }
    LaunchedEffect(listAction) {
        when (listAction) {
            kr.b1ink.scriptwidget.ui.main.ListAction.Import -> importPicker.launch(arrayOf("*/*"))
            kr.b1ink.scriptwidget.ui.main.ListAction.Backup -> vm.requestBackup()
            kr.b1ink.scriptwidget.ui.main.ListAction.Restore ->
                restorePicker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed"))
            null -> return@LaunchedEffect
        }
        onListActionDone()
    }
    LaunchedEffect(incomingFiles) {
        if (incomingFiles.isNotEmpty()) {
            vm.importFiles(incomingFiles)
            onIncomingFilesConsumed()
        }
    }
    ScriptListScreen(
        snackbarHost = { SnackbarHost(snackbar) },
        // .scriptable exports have no registered MIME type, so accept anything; non-scripts are rejected.
        onImport = { importPicker.launch(arrayOf("*/*")) },
        onExport = vm::requestExport,
        onBackup = vm::requestBackup,
        onRestore = { restorePicker.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) },
        onOpenApiReference = onOpenApiReference,
        onOpenBookmarks = onOpenBookmarks,
        onOpenGallery = onOpenGallery,
        onSetAppearance = vm::setAppearance,
        onSearch = vm::search,
        onAddShortcut = { name ->
            val pinned = kr.b1ink.scriptwidget.automation.ScriptShortcuts.requestPin(context, name)
            if (!pinned) scope.launch { snackbar.showSnackbar(shortcutUnsupported) }
        },
        state = state,
        onOpen = onOpen,
        onCreate = vm::createScript,
        onNewDesign = onNewDesign,
        onRequestDelete = vm::requestDelete,
        onConfirmDelete = vm::confirmDelete,
        onCancelDelete = vm::cancelDelete,
        widgetRecommendations = widgetRecommendations,
        onOpenPermissions = onOpenPermissions,
        onOpenHistory = onOpenHistory,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptListScreen(
    state: ScriptListUiState,
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    onRequestDelete: (String) -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    widgetRecommendations: List<AppPermission> = emptyList(),
    onOpenPermissions: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onImport: () -> Unit = {},
    onExport: (String) -> Unit = {},
    onAddShortcut: (String) -> Unit = {},
    onBackup: () -> Unit = {},
    onRestore: () -> Unit = {},
    onOpenApiReference: () -> Unit = {},
    onOpenBookmarks: () -> Unit = {},
    onOpenGallery: () -> Unit = {},
    onSetAppearance: (String, ScriptAppearance) -> Unit = { _, _ -> },
    onSearch: (String) -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    onNewDesign: (kr.b1ink.scriptwidget.domain.model.DesignTemplates) -> Unit = {},
) {
    var styling by remember { mutableStateOf<ScriptSummary?>(null) }
    var choosingNew by remember { mutableStateOf(false) }
    if (choosingNew) {
        NewScriptDialog(
            onCode = { choosingNew = false; onCreate() },
            onTemplate = { choosingNew = false; onNewDesign(it) },
            onDismiss = { choosingNew = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_title)) },
                actions = {
                    FilledTonalButton(onClick = { choosingNew = true }, modifier = Modifier.padding(end = 8.dp).testTag("new-script")) {
                        Text(stringResource(R.string.list_new_script_button))
                    }
                },
            )
        },
        snackbarHost = snackbarHost,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = LocalBottomBarSpace.current + 16.dp),
        ) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    placeholder = { Text(stringResource(R.string.list_search_placeholder)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("script-search"),
                )
            }
            if (widgetRecommendations.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp).clickable(onClick = onOpenPermissions),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.list_recommendations_title), style = MaterialTheme.typography.titleSmall)
                            Text(
                                widgetRecommendations.map { it.title() }.joinToString(" · ") + " →",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.list_add_widget_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            stringResource(R.string.list_add_widget_body),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            items(state.visible, key = { it.summary.name }) { row ->
                val script = row.summary
                val name = script.name
                ListItem(
                    modifier = Modifier.clickable { onOpen(name) },
                    leadingContent = { ScriptIcon(script) },
                    headlineContent = { Text(name) },
                    supportingContent = { Text(row.subtitle.asString(), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        ScriptMenu(
                            onExport = { onExport(name) },
                            onAddShortcut = { onAddShortcut(name) },
                            onStyle = { styling = script },
                            onDelete = { onRequestDelete(name) },
                        )
                    },
                )
            }
        }
    }

    styling?.let { script ->
        AppearanceDialog(
            script = script,
            onDismiss = { styling = null },
            onPick = { appearance ->
                onSetAppearance(script.name, appearance)
                styling = null
            },
        )
    }

    state.pendingDelete?.let { name ->
        AlertDialog(
            onDismissRequest = onCancelDelete,
            title = { Text(stringResource(R.string.list_delete_title)) },
            text = { Text(stringResource(R.string.list_delete_message, name)) },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text(stringResource(R.string.list_delete)) } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text(stringResource(R.string.list_cancel)) } },
        )
    }
}

@Composable
private fun ScriptMenu(onExport: () -> Unit, onAddShortcut: () -> Unit, onStyle: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("⋮") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.list_menu_export)) }, onClick = { open = false; onExport() })
            DropdownMenuItem(text = { Text(stringResource(R.string.list_menu_shortcut)) }, onClick = { open = false; onAddShortcut() })
            DropdownMenuItem(text = { Text(stringResource(R.string.list_menu_appearance)) }, onClick = { open = false; onStyle() })
            DropdownMenuItem(text = { Text(stringResource(R.string.list_delete)) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
private fun ScriptIcon(script: ScriptSummary, size: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(androidx.compose.ui.graphics.Color(ScriptIcons.color(script.appearance)), androidx.compose.foundation.shape.RoundedCornerShape(size / 4)),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(ScriptIcons.symbol(script.appearance, script.name), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

/** Pick an icon color and glyph; saved in the script's header like Scriptable. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AppearanceDialog(script: ScriptSummary, onDismiss: () -> Unit, onPick: (ScriptAppearance) -> Unit) {
    var color by remember { mutableStateOf(script.appearance?.color ?: "blue") }
    var glyph by remember { mutableStateOf(script.appearance?.glyph) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_menu_appearance)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ScriptIcon(ScriptSummary(script.name, ScriptAppearance(color, glyph)), size = 48.dp)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ScriptIcons.COLORS.forEach { (name, argb) ->
                        Box(
                            Modifier.size(28.dp)
                                .background(androidx.compose.ui.graphics.Color(argb), androidx.compose.foundation.shape.CircleShape)
                                .clickable { color = name },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) { if (name == color) Text("✓", color = androidx.compose.ui.graphics.Color.White) }
                    }
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    ScriptIcons.GLYPHS.entries.distinctBy { it.value }.forEach { (name, emoji) ->
                        Box(
                            Modifier.size(34.dp)
                                .background(
                                    if (name == glyph) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                )
                                .clickable { glyph = name },
                            contentAlignment = androidx.compose.ui.Alignment.Center,
                        ) { Text(emoji) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(ScriptAppearance(color, glyph)) }) { Text(stringResource(R.string.list_apply)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) } },
    )
}

/** "+ New script": write code, or start the widget designer from a template. */
@Composable
private fun NewScriptDialog(
    onCode: () -> Unit,
    onTemplate: (kr.b1ink.scriptwidget.domain.model.DesignTemplates) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.list_new_script_title)) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                androidx.compose.material3.ListItem(
                    headlineContent = { Text(stringResource(R.string.list_new_code)) },
                    supportingContent = { Text(stringResource(R.string.list_new_code_description)) },
                    modifier = Modifier.clickable(onClick = onCode),
                )
                Text(
                    stringResource(R.string.list_new_designer),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                kr.b1ink.scriptwidget.domain.model.DesignTemplates.all.forEach { template ->
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("🎨 ${stringResource(template.titleRes)}") },
                        modifier = Modifier.testTag("template-${template.name}").clickable { onTemplate(template) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.list_cancel)) } },
    )
}

/** The template's name in the app's language (the domain title is Korean only). */
private val DesignTemplates.titleRes: Int
    get() = when (this) {
        DesignTemplates.Blank -> R.string.list_template_blank
        DesignTemplates.Clock -> R.string.list_template_clock
        DesignTemplates.Battery -> R.string.list_template_battery
        DesignTemplates.NextEvent -> R.string.list_template_next_event
        DesignTemplates.Reminders -> R.string.list_template_reminders
        DesignTemplates.Weather -> R.string.list_template_weather
        DesignTemplates.GitHubStars -> R.string.list_template_json
        DesignTemplates.PhotoFrame -> R.string.list_template_photo
    }
