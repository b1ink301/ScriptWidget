package kr.b1ink.scriptwidget.ui.permissions

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kr.b1ink.scriptwidget.permissions.AppPermission
import kr.b1ink.scriptwidget.permissions.PermissionAction
import kr.b1ink.scriptwidget.permissions.PermissionState

/** Titles and explanations shown to the user. */
fun AppPermission.title(): String = when (this) {
    AppPermission.Notifications -> "알림"
    AppPermission.Location -> "위치"
    AppPermission.BackgroundLocation -> "백그라운드 위치 (항상 허용)"
    AppPermission.BatteryOptimization -> "배터리 최적화 제외"
    AppPermission.ExactAlarm -> "정확한 알람"
    AppPermission.Calendar -> "캘린더"
    AppPermission.Contacts -> "연락처"
    AppPermission.Photos -> "사진"
}

fun AppPermission.description(): String = when (this) {
    AppPermission.Notifications -> "스크립트가 Notification으로 알림을 보낼 수 있습니다."
    AppPermission.Location -> "Location.current()로 현재 위치를 가져옵니다."
    AppPermission.BackgroundLocation ->
        "위젯이 갱신될 때도 현재 위치를 씁니다. 허용하지 않으면 앱에서 마지막으로 얻은 위치를 사용합니다. " +
            "설정 화면에서 '항상 허용'을 선택하세요."
    AppPermission.BatteryOptimization -> "절전 상태에서도 위젯 갱신이 미뤄지지 않도록 합니다."
    AppPermission.ExactAlarm -> "refreshAfterDate에 지정한 시각에 정확히 갱신합니다."
    AppPermission.Calendar -> "Calendar·CalendarEvent로 일정을 읽고 추가합니다. (Reminder는 권한 없이 앱 안에 저장됩니다)"
    AppPermission.Contacts -> "Contact로 연락처를 읽고 수정합니다."
    AppPermission.Photos -> "Photos.latestPhoto()로 최근 사진·스크린샷을 읽습니다. (사진 선택·저장은 권한이 필요 없습니다)"
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
                title = { Text("권한") },
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
                    "ScriptWidget은 Google Play 밖(GitHub)에서 배포되므로 Play 정책상 제한되는 권한도 쓸 수 있습니다. " +
                        "필요한 것만 허용하세요.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(state.items, key = { it.permission }) { item -> PermissionCard(item, onRequest) }
            if (automationEnabled != null) {
                item { AutomationCard(automationEnabled, onAutomationChange) }
            }
            item {
                TextButton(onClick = onOpenAppSettings, modifier = Modifier.fillMaxWidth()) { Text("앱 설정 열기") }
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
                    label = { Text(if (item.state == PermissionState.Granted) "허용됨" else "허용 안 됨") },
                )
            }
            Text(item.permission.description(), style = MaterialTheme.typography.bodySmall)
            if (item.state != PermissionState.Granted) {
                FilledTonalButton(onClick = { onRequest(item.permission) }, enabled = item.enabled) {
                    Text(if (item.enabled) "허용하기" else "먼저 위치를 허용하세요")
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
                Text("자동화 앱에서 실행", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(checked = enabled, onCheckedChange = onChange)
            }
            Text(
                "Tasker·MacroDroid 같은 앱이 브로드캐스트 kr.b1ink.scriptwidget.action.RUN_SCRIPT " +
                    "(extra: scriptName, parameter)로 스크립트를 백그라운드에서 실행합니다. " +
                    "결과는 kr.b1ink.scriptwidget.action.SCRIPT_RESULT (output, error)로 돌려줍니다. " +
                    "켜 두면 다른 앱이 스크립트를 실행할 수 있으니 필요할 때만 켜세요.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
