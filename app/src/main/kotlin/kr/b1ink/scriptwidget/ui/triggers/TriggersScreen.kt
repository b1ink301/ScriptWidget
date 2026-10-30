package kr.b1ink.scriptwidget.ui.triggers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.model.EventTrigger
import kr.b1ink.scriptwidget.domain.model.TriggerAction
import kr.b1ink.scriptwidget.domain.model.TriggerEvent

class TriggersActions(
    val onBack: () -> Unit,
    val onAdd: (TriggerEvent, String, TriggerAction) -> Unit,
    val onSetEnabled: (String, Boolean) -> Unit,
    val onRemove: (String) -> Unit,
)

@Composable
fun TriggersRoute(container: AppContainer, onBack: () -> Unit) {
    val vm = viewModel { TriggersViewModel(container.eventTriggers, container.eventMonitor, container.observeScriptNames()) }
    val state by vm.state.collectAsStateWithLifecycle()
    TriggersScreen(state, TriggersActions(onBack, vm::add, vm::setEnabled, vm::remove))
}

/** "When this happens, refresh / run that script." */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriggersScreen(state: TriggersUiState, actions: TriggersActions) {
    var adding by remember { mutableStateOf(false) }
    if (adding) {
        NewTriggerDialog(
            scripts = state.scripts,
            onAdd = { event, script, action ->
                adding = false
                actions.onAdd(event, script, action)
            },
            onDismiss = { adding = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = actions.onBack) { Text("←") } },
                title = { Text("자동 실행 트리거") },
                actions = {
                    FilledTonalButton(onClick = { adding = true }, modifier = Modifier.padding(end = 8.dp).testTag("add-trigger")) {
                        Text("+ 트리거")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                Text(
                    "기기에서 일이 생기면 스크립트를 실행합니다. 위젯 갱신은 화면이 꺼져 있으면 화면이 켜질 때 실행되고, " +
                        "스크립트 실행은 백그라운드에서 바로 실행됩니다(스크립트에서는 args.trigger로 이벤트를 알 수 있습니다).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
            if (state.triggers.isEmpty()) {
                item { Text("아직 트리거가 없습니다. 오른쪽 위 + 트리거로 추가하세요.", modifier = Modifier.padding(16.dp)) }
            }
            items(state.triggers, key = { it.id }) { trigger -> TriggerRow(trigger, actions) }
        }
    }
}

@Composable
private fun TriggerRow(trigger: EventTrigger, actions: TriggersActions) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        leadingContent = { Text(trigger.event.emoji, style = MaterialTheme.typography.titleLarge) },
        headlineContent = { Text(trigger.event.whenText) },
        supportingContent = {
            Text(
                when (trigger.action) {
                    TriggerAction.RefreshWidgets -> "→ '${trigger.scriptName}' 위젯 갱신"
                    TriggerAction.RunScript -> "→ '${trigger.scriptName}' 스크립트 실행"
                },
            )
        },
        trailingContent = {
            Row {
                Switch(
                    checked = trigger.enabled,
                    onCheckedChange = { actions.onSetEnabled(trigger.id, it) },
                    modifier = Modifier.testTag("trigger-switch-${trigger.id}"),
                )
                TextButton(onClick = { actions.onRemove(trigger.id) }, modifier = Modifier.testTag("trigger-remove-${trigger.id}")) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NewTriggerDialog(scripts: List<String>, onAdd: (TriggerEvent, String, TriggerAction) -> Unit, onDismiss: () -> Unit) {
    var event by remember { mutableStateOf(TriggerEvent.CalendarChanged) }
    var script by remember { mutableStateOf(scripts.firstOrNull()) }
    var action by remember { mutableStateOf(TriggerAction.RefreshWidgets) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 트리거") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("언제", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TriggerEvent.entries.forEach { e ->
                        FilterChip(
                            selected = e == event,
                            onClick = { event = e },
                            label = { Text("${e.emoji} ${e.title}") },
                            modifier = Modifier.testTag("event-${e.name}"),
                        )
                    }
                }
                Text(event.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("어떤 스크립트", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    scripts.forEach { name ->
                        FilterChip(selected = name == script, onClick = { script = name }, label = { Text(name) }, modifier = Modifier.testTag("script-$name"))
                    }
                }
                Text("무엇을", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = action == TriggerAction.RefreshWidgets,
                        onClick = { action = TriggerAction.RefreshWidgets },
                        label = { Text("위젯 갱신") },
                        modifier = Modifier.testTag("action-RefreshWidgets"),
                    )
                    FilterChip(
                        selected = action == TriggerAction.RunScript,
                        onClick = { action = TriggerAction.RunScript },
                        label = { Text("스크립트 실행") },
                        modifier = Modifier.testTag("action-RunScript"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { script?.let { onAdd(event, it, action) } },
                enabled = script != null,
                modifier = Modifier.testTag("confirm-trigger"),
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private val TriggerEvent.emoji: String
    get() = when (this) {
        TriggerEvent.CalendarChanged -> "📅"
        TriggerEvent.ContactsChanged -> "👤"
        TriggerEvent.NewPhoto -> "🖼"
        TriggerEvent.DayChanged -> "🌅"
        TriggerEvent.TimeZoneChanged -> "🌐"
        TriggerEvent.Charging -> "🔌"
        TriggerEvent.WifiConnected -> "📶"
    }

private val TriggerEvent.title: String
    get() = when (this) {
        TriggerEvent.CalendarChanged -> "캘린더 변경"
        TriggerEvent.ContactsChanged -> "연락처 변경"
        TriggerEvent.NewPhoto -> "새 사진"
        TriggerEvent.DayChanged -> "날짜 바뀜"
        TriggerEvent.TimeZoneChanged -> "시간대 변경"
        TriggerEvent.Charging -> "충전 중"
        TriggerEvent.WifiConnected -> "Wi-Fi 연결"
    }

private val TriggerEvent.whenText: String
    get() = when (this) {
        TriggerEvent.CalendarChanged -> "캘린더가 바뀌면"
        TriggerEvent.ContactsChanged -> "연락처가 바뀌면"
        TriggerEvent.NewPhoto -> "새 사진이 생기면"
        TriggerEvent.DayChanged -> "날짜가 바뀌면 (자정)"
        TriggerEvent.TimeZoneChanged -> "시간대·시각이 바뀌면"
        TriggerEvent.Charging -> "충전 중일 때 (최대 1시간에 한 번)"
        TriggerEvent.WifiConnected -> "Wi-Fi에 연결돼 있을 때 (최대 1시간에 한 번)"
    }

private val TriggerEvent.note: String
    get() = when (this) {
        TriggerEvent.CalendarChanged, TriggerEvent.ContactsChanged ->
            "일정·연락처를 추가하거나 고치면 몇 초 안에 실행됩니다. 해당 권한이 있어야 합니다."
        TriggerEvent.NewPhoto -> "사진을 찍거나 저장하면 실행됩니다. 사진 권한이 있어야 합니다."
        TriggerEvent.DayChanged -> "자정에 실행됩니다. 화면이 꺼져 있으면 다음에 켜질 때 실행됩니다."
        TriggerEvent.TimeZoneChanged -> "여행 등으로 시간대가 바뀌거나 시각을 직접 바꾸면 실행됩니다."
        TriggerEvent.Charging -> "충전을 시작하면 실행되고, 계속 충전 중이면 1시간마다 다시 실행됩니다."
        TriggerEvent.WifiConnected -> "Wi-Fi(데이터 요금이 없는 네트워크)에 연결되면 실행되고, 연결이 유지되면 1시간마다 다시 실행됩니다."
    }
