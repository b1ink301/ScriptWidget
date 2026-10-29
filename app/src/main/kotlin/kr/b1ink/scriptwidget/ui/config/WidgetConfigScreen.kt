package kr.b1ink.scriptwidget.ui.config

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetConfigScreen(
    state: WidgetConfigUiState,
    onSelect: (String) -> Unit,
    onParameterChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("위젯 스크립트 선택") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.weight(1f)) {
                items(state.names, key = { it }) { name ->
                    ListItem(
                        modifier = Modifier.clickable { onSelect(name) },
                        leadingContent = { RadioButton(selected = state.selected == name, onClick = { onSelect(name) }) },
                        headlineContent = { Text(name) },
                    )
                }
            }
            OutlinedTextField(
                value = state.parameter,
                onValueChange = onParameterChange,
                label = { Text("파라미터 (args.widgetParameter)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Button(
                enabled = state.canConfirm,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                onClick = onConfirm,
            ) { Text("완료") }
        }
    }
}
