package kr.b1ink.scriptwidget.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.permissions.AppPermission
import kr.b1ink.scriptwidget.permissions.PermissionAction
import kr.b1ink.scriptwidget.permissions.PermissionState

/** Titles and explanations shown to the user. */
@Composable
fun AppPermission.title(): String = stringResource(titleRes())

@Composable
fun AppPermission.description(): String = stringResource(descriptionRes())

@StringRes
fun AppPermission.titleRes(): Int = when (this) {
    AppPermission.Notifications -> R.string.permissions_notifications_title
    AppPermission.Location -> R.string.permissions_location_title
    AppPermission.BackgroundLocation -> R.string.permissions_background_location_title
    AppPermission.BatteryOptimization -> R.string.permissions_battery_title
    AppPermission.ExactAlarm -> R.string.permissions_exact_alarm_title
    AppPermission.Calendar -> R.string.permissions_calendar_title
    AppPermission.Contacts -> R.string.permissions_contacts_title
    AppPermission.Photos -> R.string.permissions_photos_title
}

@StringRes
fun AppPermission.descriptionRes(): Int = when (this) {
    AppPermission.Notifications -> R.string.permissions_notifications_description
    AppPermission.Location -> R.string.permissions_location_description
    AppPermission.BackgroundLocation -> R.string.permissions_background_location_description
    AppPermission.BatteryOptimization -> R.string.permissions_battery_description
    AppPermission.ExactAlarm -> R.string.permissions_exact_alarm_description
    AppPermission.Calendar -> R.string.permissions_calendar_description
    AppPermission.Contacts -> R.string.permissions_contacts_description
    AppPermission.Photos -> R.string.permissions_photos_description
}

/** READ_MEDIA_IMAGES only exists on Android 13+; older versions use READ_EXTERNAL_STORAGE. */
internal fun forThisVersion(permission: String): String =
    if (permission == android.Manifest.permission.READ_MEDIA_IMAGES && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    } else {
        permission
    }

/** Runs [PermissionAction]s: runtime prompts and system settings screens. */
@Composable
fun PermissionActionHandler(vm: PermissionsViewModel) {
    val context = LocalContext.current
    val runtime = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val denied = results.filterValues { !it }.keys
        val activity = context as? Activity
        val canAskAgain = activity != null && denied.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
        vm.onRuntimeResult(stillDenied = denied.isNotEmpty(), canAskAgain = canAskAgain)
    }
    LaunchedEffect(vm) {
        vm.actions.collect { action ->
            val packageUri = Uri.parse("package:${context.packageName}")
            when (action) {
                is PermissionAction.RequestRuntime -> runtime.launch(action.permissions.map(::forThisVersion).toTypedArray())
                PermissionAction.OpenBatteryOptimizationRequest ->
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri))
                PermissionAction.OpenExactAlarmSettings ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri))
                    }
                PermissionAction.OpenAppSettings ->
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri))
            }
        }
    }
    // Settings screens return without a result: re-read the states whenever we come back.
    LifecycleResumeEffect(vm) {
        vm.refresh()
        onPauseOrDispose { }
    }
}

@Composable
fun PermissionsRoute(
    vm: PermissionsViewModel,
    onBack: () -> Unit,
    automation: kr.b1ink.scriptwidget.automation.AutomationSettings? = null,
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    PermissionActionHandler(vm)
    var automationEnabled by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(automation?.enabled == true) }
    PermissionsScreen(
        state = state,
        onBack = onBack,
        onRequest = vm::request,
        onOpenAppSettings = vm::openAppSettings,
        automationEnabled = automationEnabled.takeIf { automation != null },
        onAutomationChange = { enabled ->
            automation?.enabled = enabled
            automationEnabled = enabled
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    state: PermissionsUiState,
    onBack: () -> Unit,
    onRequest: (AppPermission) -> Unit,
    onOpenAppSettings: () -> Unit,
    automationEnabled: Boolean? = null,
    onAutomationChange: (Boolean) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                title = { Text(stringResource(R.string.permissions_title)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.permissions_intro),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(state.items, key = { it.permission }) { item -> PermissionCard(item, onRequest) }
            if (automationEnabled != null) {
                item { AutomationCard(automationEnabled, onAutomationChange) }
            }
            item {
                TextButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.permissions_open_app_settings)) }
            }
        }
    }
}

@Composable
private fun PermissionCard(item: PermissionItem, onRequest: (AppPermission) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.permission.title(), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text(stringResource(if (item.state == PermissionState.Granted) R.string.permissions_granted else R.string.permissions_denied)) },
                )
            }
            Text(item.permission.description(), style = MaterialTheme.typography.bodySmall)
            if (item.state != PermissionState.Granted) {
                FilledTonalButton(onClick = { onRequest(item.permission) }, enabled = item.enabled) {
                    Text(stringResource(if (item.enabled) R.string.permissions_allow else R.string.permissions_location_first))
                }
            }
        }
    }
}

@Composable
private fun AutomationCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.material3.Card(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(stringResource(R.string.permissions_automation_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = enabled, onCheckedChange = onChange)
            }
            Text(
                stringResource(R.string.permissions_automation_description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
