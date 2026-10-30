package kr.b1ink.scriptwidget.ui.history

import kr.b1ink.scriptwidget.ui.text.asString
import kr.b1ink.scriptwidget.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.b1ink.scriptwidget.di.AppContainer
import java.text.DateFormat
import java.util.Date

@Composable
fun RunHistoryRoute(container: AppContainer, onBack: (() -> Unit)? = null) {
    val vm = viewModel { RunHistoryViewModel(container.runHistory) }
    val state by vm.uiState.collectAsStateWithLifecycle()
    RunHistoryScreen(state, onBack, vm::setFailuresOnly, vm::clear)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunHistoryScreen(
    state: RunHistoryUiState,
    onBack: (() -> Unit)?,
    onFailuresOnly: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    val timeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { onBack?.let { TextButton(onClick = it) { Text("←") } } },
                title = { Text(stringResource(R.string.history_title)) },
                actions = {
                    FilterChip(selected = state.failuresOnly, onClick = { onFailuresOnly(!state.failuresOnly) }, label = { Text(stringResource(R.string.history_failures_only)) })
                    TextButton(onClick = onClear) { Text(stringResource(R.string.history_clear)) }
                },
            )
        },
    ) { padding ->
        if (state.rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = kr.b1ink.scriptwidget.ui.main.LocalBottomBarSpace.current)) {
            items(state.rows, key = { it.key }) { row ->
                ListItem(
                    modifier = Modifier.clickable { expanded = if (expanded == row.key) null else row.key },
                    overlineContent = { Text("${timeFormat.format(Date(row.startedAt))} · ${row.sourceLabel.asString()}") },
                    headlineContent = { Text((if (row.success) "✓ " else "✗ ") + row.scriptName, color = if (row.success) Color.Unspecified else MaterialTheme.colorScheme.error) },
                    supportingContent = {
                        Column {
                            Text(row.summary.asString(), maxLines = if (expanded == row.key) Int.MAX_VALUE else 1)
                            if (expanded == row.key && row.details.isNotEmpty()) {
                                Text(row.details, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    },
                    trailingContent = { Text(row.duration, style = MaterialTheme.typography.labelMedium) },
                )
                HorizontalDivider()
            }
        }
    }
}
