package kr.b1ink.scriptwidget.ui.bookmarks

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.b1ink.scriptwidget.di.AppContainer

@Composable
fun FileBookmarksRoute(container: AppContainer, onBack: () -> Unit) {
    val vm = viewModel { FileBookmarksViewModel(container.fileBookmarks, container.scriptDocuments) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    // The bookmark name waiting for the file picker's answer.
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val name = pickingFor
        pickingFor = null
        if (uri != null && name != null) vm.add(name, uri.toString())
    }
    FileBookmarksScreen(
        state = state,
        onBack = onBack,
        onPick = { name -> pickingFor = name; picker.launch(arrayOf("*/*")) },
        onRemove = vm::remove,
        onMessageShown = vm::messageShown,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBookmarksScreen(
    state: FileBookmarksUiState,
    onBack: () -> Unit,
    onPick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onMessageShown: () -> Unit,
) {
    var naming by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                title = { Text("파일 북마크") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { naming = true }) { Text("+ 북마크 추가") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "스크립트가 FileManager.bookmarkedPath(\"이름\")으로 읽는 파일입니다. 고른 파일은 앱 안으로 복사되며, " +
                        "항목을 누르면 다른 파일로 바꿀 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (state.rows.isEmpty()) {
                item { Text("아직 북마크가 없습니다.", modifier = Modifier.padding(16.dp)) }
            }
            items(state.rows, key = { it.name }) { row ->
                ListItem(
                    headlineContent = { Text(row.name) },
                    supportingContent = { Text(row.size) },
                    trailingContent = { TextButton(onClick = { removing = row.name }) { Text("삭제") } },
                    modifier = Modifier.testTag("bookmark-${row.name}").clickable { onPick(row.name) },
                )
                HorizontalDivider()
            }
        }
    }

    if (naming) {
        var name by rememberSaveable { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text("북마크 이름") },
            text = {
                Column {
                    Text("스크립트에 적힌 이름과 똑같이 입력하세요 (예: still)", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.testTag("bookmark-name"))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { naming = false; onPick(name) }) { Text("파일 선택") }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text("취소") } },
        )
    }

    removing?.let { name ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("'$name' 북마크를 삭제할까요?") },
            text = { Text("앱 안에 복사해 둔 파일이 삭제됩니다. 원본 파일은 그대로입니다.") },
            confirmButton = { TextButton(onClick = { removing = null; onRemove(name) }) { Text("삭제") } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("취소") } },
        )
    }
}
