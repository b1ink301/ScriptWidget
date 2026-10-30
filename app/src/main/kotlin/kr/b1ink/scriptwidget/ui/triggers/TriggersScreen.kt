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
import androidx.compose.ui.res.stringResource
import kr.b1ink.scriptwidget.R

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
                title = { Text(stringResource(R.string.triggers_title)) },
                actions = {
                    FilledTonalButton(onClick = { adding = true }, modifier = Modifier.padding(end = 8.dp).testTag("add-trigger")) {
                        Text(stringResource(R.string.triggers_add_button))
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
                    stringResource(R.string.triggers_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
            if (state.triggers.isEmpty()) {
                item { Text(stringResource(R.string.triggers_empty), modifier = Modifier.padding(16.dp)) }
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
        headlineContent = { Text(trigger.event.whenText()) },
        supportingContent = {
            Text(
                when (trigger.action) {
                    TriggerAction.RefreshWidgets -> stringResource(R.string.triggers_action_refresh_row, trigger.scriptName)
                    TriggerAction.RunScript -> stringResource(R.string.triggers_action_run_row, trigger.scriptName)
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
                    Text(stringResource(R.string.triggers_delete), color = MaterialTheme.colorScheme.error)
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
        title = { Text(stringResource(R.string.triggers_new_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.triggers_when), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TriggerEvent.entries.forEach { e ->
                        FilterChip(
                            selected = e == event,
                            onClick = { event = e },
                            label = { Text("${e.emoji} ${e.title()}") },
                            modifier = Modifier.testTag("event-${e.name}"),
                        )
                    }
                }
                Text(event.note(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.triggers_which_script), style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    scripts.forEach { name ->
                        FilterChip(selected = name == script, onClick = { script = name }, label = { Text(name) }, modifier = Modifier.testTag("script-$name"))
                    }
                }
                Text(stringResource(R.string.triggers_what), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = action == TriggerAction.RefreshWidgets,
                        onClick = { action = TriggerAction.RefreshWidgets },
                        label = { Text(stringResource(R.string.triggers_action_refresh)) },
                        modifier = Modifier.testTag("action-RefreshWidgets"),
                    )
                    FilterChip(
                        selected = action == TriggerAction.RunScript,
                        onClick = { action = TriggerAction.RunScript },
                        label = { Text(stringResource(R.string.triggers_action_run)) },
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
            ) { Text(stringResource(R.string.triggers_add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.triggers_cancel)) } },
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

@Composable
private fun TriggerEvent.title(): String = stringResource(
    when (this) {
        TriggerEvent.CalendarChanged -> R.string.triggers_event_calendar_title
        TriggerEvent.ContactsChanged -> R.string.triggers_event_contacts_title
        TriggerEvent.NewPhoto -> R.string.triggers_event_photo_title
        TriggerEvent.DayChanged -> R.string.triggers_event_day_title
        TriggerEvent.TimeZoneChanged -> R.string.triggers_event_timezone_title
        TriggerEvent.Charging -> R.string.triggers_event_charging_title
        TriggerEvent.WifiConnected -> R.string.triggers_event_wifi_title
    },
)

@Composable
private fun TriggerEvent.whenText(): String = stringResource(
    when (this) {
        TriggerEvent.CalendarChanged -> R.string.triggers_event_calendar_when
        TriggerEvent.ContactsChanged -> R.string.triggers_event_contacts_when
        TriggerEvent.NewPhoto -> R.string.triggers_event_photo_when
        TriggerEvent.DayChanged -> R.string.triggers_event_day_when
        TriggerEvent.TimeZoneChanged -> R.string.triggers_event_timezone_when
        TriggerEvent.Charging -> R.string.triggers_event_charging_when
        TriggerEvent.WifiConnected -> R.string.triggers_event_wifi_when
    },
)

@Composable
private fun TriggerEvent.note(): String = stringResource(
    when (this) {
        TriggerEvent.CalendarChanged, TriggerEvent.ContactsChanged -> R.string.triggers_event_calendar_contacts_note
        TriggerEvent.NewPhoto -> R.string.triggers_event_photo_note
        TriggerEvent.DayChanged -> R.string.triggers_event_day_note
        TriggerEvent.TimeZoneChanged -> R.string.triggers_event_timezone_note
        TriggerEvent.Charging -> R.string.triggers_event_charging_note
        TriggerEvent.WifiConnected -> R.string.triggers_event_wifi_note
    },
)
