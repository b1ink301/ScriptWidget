package kr.b1ink.scriptwidget.ui.bookmarks

import androidx.compose.ui.platform.LocalContext
import kr.b1ink.scriptwidget.R
import androidx.compose.ui.res.stringResource
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
    val context = LocalContext.current
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it.resolve(context)); onMessageShown() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                title = { Text(stringResource(R.string.bookmarks_title)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { naming = true }) { Text(stringResource(R.string.bookmarks_add)) } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    stringResource(R.string.bookmarks_intro),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (state.rows.isEmpty()) {
                item { Text(stringResource(R.string.bookmarks_empty), modifier = Modifier.padding(16.dp)) }
            }
            items(state.rows, key = { it.name }) { row ->
                ListItem(
                    headlineContent = { Text(row.name) },
                    supportingContent = { Text(row.size) },
                    trailingContent = { TextButton(onClick = { removing = row.name }) { Text(stringResource(R.string.bookmarks_delete)) } },
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
            title = { Text(stringResource(R.string.bookmarks_name_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.bookmarks_name_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.testTag("bookmark-name"))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { naming = false; onPick(name) }) { Text(stringResource(R.string.bookmarks_pick_file)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.bookmarks_cancel)) } },
        )
    }

    removing?.let { name ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.bookmarks_delete_title, name)) },
            text = { Text(stringResource(R.string.bookmarks_delete_text)) },
            confirmButton = { TextButton(onClick = { removing = null; onRemove(name) }) { Text(stringResource(R.string.bookmarks_delete)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.bookmarks_cancel)) } },
        )
    }
}
