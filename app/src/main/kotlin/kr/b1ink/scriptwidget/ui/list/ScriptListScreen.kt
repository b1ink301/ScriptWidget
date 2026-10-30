package kr.b1ink.scriptwidget.ui.list

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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

@Composable
fun ScriptListRoute(
    container: AppContainer,
    onOpen: (String) -> Unit,
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
                is ScriptListEvent.ShowMessage -> snackbar.showSnackbar(event.text, withDismissAction = true, duration = SnackbarDuration.Long)
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
            if (!pinned) scope.launch { snackbar.showSnackbar("이 런처는 바로가기 추가를 지원하지 않습니다.") }
        },
        state = state,
        onOpen = onOpen,
        onCreate = vm::createScript,
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
) {
    var styling by remember { mutableStateOf<ScriptSummary?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 스크립트") },
                actions = {
                    FilledTonalButton(onClick = onCreate, modifier = Modifier.padding(end = 8.dp).testTag("new-script")) {
                        Text("+ 새 스크립트")
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
                    placeholder = { Text("스크립트 검색") },
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
                            Text("위젯이 더 정확하게 갱신되도록 설정하세요", style = MaterialTheme.typography.titleSmall)
                            Text(
                                widgetRecommendations.joinToString(" · ") { it.title() } + " →",
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
                        Text("홈 화면에 위젯 추가하기", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "편집기에서 '고정'을 누르거나, 홈 화면 위젯 목록에서 ScriptWidget → Script를 추가하세요. " +
                                "위젯을 탭하면 즉시 새로고침되고, 화면이 켜져 있는 동안 자동으로 갱신됩니다.",
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
                    supportingContent = { Text(row.subtitle, style = MaterialTheme.typography.bodySmall) },
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
            title = { Text("스크립트 삭제") },
            text = { Text("'$name'을(를) 삭제할까요? 이 스크립트를 쓰는 위젯은 에러를 표시합니다.") },
            confirmButton = { TextButton(onClick = onConfirmDelete) { Text("삭제") } },
            dismissButton = { TextButton(onClick = onCancelDelete) { Text("취소") } },
        )
    }
}

@Composable
private fun ScriptMenu(onExport: () -> Unit, onAddShortcut: () -> Unit, onStyle: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("⋮") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("내보내기") }, onClick = { open = false; onExport() })
            DropdownMenuItem(text = { Text("홈 화면에 바로가기") }, onClick = { open = false; onAddShortcut() })
            DropdownMenuItem(text = { Text("아이콘·색상") }, onClick = { open = false; onStyle() })
            DropdownMenuItem(text = { Text("삭제") }, onClick = { open = false; onDelete() })
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
        title = { Text("아이콘·색상") },
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
        confirmButton = { TextButton(onClick = { onPick(ScriptAppearance(color, glyph)) }) { Text("적용") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
