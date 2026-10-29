package kr.b1ink.scriptwidget.ui.gallery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.model.EntryStatus
import kr.b1ink.scriptwidget.domain.model.GalleryItem
import kr.b1ink.scriptwidget.domain.model.GallerySection

class GalleryActions(
    val onInstall: (sourceId: String, entryId: String) -> Unit,
    val onUpdate: (sourceId: String, entryId: String) -> Unit,
    val onOpen: (scriptName: String) -> Unit,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
    val onCategory: (String?) -> Unit,
    val onAddUrl: (String) -> Unit,
    val onRemoveUrl: (String) -> Unit,
    val onMessageShown: () -> Unit = {},
)

@Composable
fun GalleryRoute(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val vm = viewModel {
        GalleryViewModel(container.builtinCatalog, container::remoteCatalog, container.catalogUrls, container.installedScripts, container.scriptRepositoryForGallery)
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    GalleryScreen(
        state,
        GalleryActions(
            onInstall = vm::install, onUpdate = vm::update, onOpen = onOpen, onConfirm = vm::confirmInstall,
            onCancel = vm::cancelPreview, onCategory = vm::selectCategory, onAddUrl = vm::addUrl, onRemoveUrl = vm::removeUrl,
            onMessageShown = vm::messageShown,
        ),
        onBack = onBack,
    )
}

private val PERMISSION_LABELS = mapOf(
    "calendar" to "캘린더", "contacts" to "연락처", "location" to "위치", "network" to "인터넷",
    "notification" to "알림", "photos" to "사진",
)
private val SIZE_LABELS = mapOf("small" to "S", "medium" to "M", "large" to "L")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(state: GalleryUiState, actions: GalleryActions, onBack: () -> Unit) {
    var addingUrl by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); actions.onMessageShown() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                title = { Text("갤러리") },
                actions = { TextButton(onClick = { addingUrl = true }) { Text("+ 카탈로그") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.category == null, onClick = { actions.onCategory(null) }, label = { Text("전체") })
                    state.categories.forEach { c ->
                        FilterChip(selected = state.category == c, onClick = { actions.onCategory(c) }, label = { Text(c) })
                    }
                }
            }
            state.visibleSections.forEach { section ->
                item(key = "header-${section.sourceId}") { SectionHeader(section, onRemove = { removing = section.sourceId }) }
                items(section.items, key = { "${section.sourceId}/${it.entry.id}" }) { item ->
                    EntryCard(item, busy = state.busy, actions = actions, sourceId = section.sourceId)
                }
            }
        }
    }

    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = actions.onCancel,
            title = { Text("'${preview.entry.title}' 설치") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("다른 곳에서 받은 스크립트입니다. 코드를 확인한 뒤 설치하세요.", style = MaterialTheme.typography.bodySmall)
                    if (preview.entry.permissions.isNotEmpty()) {
                        Text("필요 권한: " + preview.entry.permissions.joinToString(", ") { PERMISSION_LABELS[it] ?: it }, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        preview.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = actions.onConfirm, modifier = Modifier.testTag("install-confirm")) { Text("설치") } },
            dismissButton = { TextButton(onClick = actions.onCancel) { Text("취소") } },
        )
    }

    if (addingUrl) {
        // Cursor after the prefilled scheme, so typing continues the address.
        var url by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("https://", androidx.compose.ui.text.TextRange(8))) }
        AlertDialog(
            onDismissRequest = { addingUrl = false },
            title = { Text("카탈로그 추가") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("스크립트 목록(index.json)의 주소를 입력하세요. 예) GitHub 저장소의 raw 주소", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.testTag("catalog-url"),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { addingUrl = false; actions.onAddUrl(url.text) }) { Text("추가") } },
            dismissButton = { TextButton(onClick = { addingUrl = false }) { Text("취소") } },
        )
    }

    removing?.let { url ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("카탈로그를 삭제할까요?") },
            text = { Text("$url\n\n이 카탈로그에서 설치한 스크립트는 그대로 남습니다.") },
            confirmButton = { TextButton(onClick = { removing = null; actions.onRemoveUrl(url) }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("취소") } },
        )
    }
}

@Composable
private fun SectionHeader(section: GallerySection, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(section.title, style = MaterialTheme.typography.titleMedium)
            section.error?.let { Text("불러오지 못했습니다: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (section.removable) TextButton(onClick = onRemove) { Text("삭제") }
    }
}

@Composable
private fun EntryCard(item: GalleryItem, busy: Boolean, actions: GalleryActions, sourceId: String) {
    val entry = item.entry
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                Text(entry.description, style = MaterialTheme.typography.bodySmall)
                val meta = buildList {
                    if (entry.sizes.isNotEmpty()) add("크기 " + entry.sizes.joinToString("·") { SIZE_LABELS[it] ?: it })
                    if (entry.permissions.isNotEmpty()) add(entry.permissions.joinToString(" · ") { PERMISSION_LABELS[it] ?: it })
                    entry.author?.let { add(it) }
                }
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val tag = Modifier.testTag("gallery-action-${entry.id}")
            when (val status = item.status) {
                EntryStatus.NotInstalled -> Button(enabled = !busy, onClick = { actions.onInstall(sourceId, entry.id) }, modifier = tag) { Text("설치") }
                is EntryStatus.Installed -> OutlinedButton(onClick = { actions.onOpen(status.scriptName) }, modifier = tag) { Text("열기") }
                is EntryStatus.UpdateAvailable -> FilledTonalButton(enabled = !busy, onClick = { actions.onUpdate(sourceId, entry.id) }, modifier = tag) { Text("업데이트") }
            }
        }
    }
}
