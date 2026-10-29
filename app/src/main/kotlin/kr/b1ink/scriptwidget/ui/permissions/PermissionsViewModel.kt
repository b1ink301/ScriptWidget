package kr.b1ink.scriptwidget.ui.permissions

import android.Manifest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kr.b1ink.scriptwidget.permissions.AppPermission
import kr.b1ink.scriptwidget.permissions.PermissionAction
import kr.b1ink.scriptwidget.permissions.PermissionState
import kr.b1ink.scriptwidget.permissions.PermissionStatusProvider

data class PermissionItem(
    val permission: AppPermission,
    val state: PermissionState,
    /** False while a prerequisite is missing (background location needs location). */
    val enabled: Boolean,
)

data class PermissionsUiState(
    val items: List<PermissionItem> = emptyList(),
    /** Missing permissions that make widgets less reliable; shown as a banner on the script list. */
    val widgetRecommendations: List<AppPermission> = emptyList(),
)

class PermissionsViewModel(private val status: PermissionStatusProvider) : ViewModel() {

    private val _uiState = MutableStateFlow(compute())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    private val _actions = Channel<PermissionAction>(Channel.BUFFERED)
    val actions: Flow<PermissionAction> = _actions.receiveAsFlow()

    /** Re-reads the states, e.g. when returning from a system settings screen. */
    fun refresh() {
        _uiState.value = compute()
    }

    fun request(permission: AppPermission) {
        val action = when (permission) {
            AppPermission.Notifications -> PermissionAction.RequestRuntime(listOf(Manifest.permission.POST_NOTIFICATIONS))
            AppPermission.Location -> LOCATION_REQUEST
            AppPermission.BackgroundLocation ->
                if (status.state(AppPermission.Location) != PermissionState.Granted) LOCATION_REQUEST
                else PermissionAction.RequestRuntime(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
            AppPermission.BatteryOptimization -> PermissionAction.OpenBatteryOptimizationRequest
            AppPermission.ExactAlarm -> PermissionAction.OpenExactAlarmSettings
            AppPermission.Calendar ->
                PermissionAction.RequestRuntime(listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
            AppPermission.Contacts ->
                PermissionAction.RequestRuntime(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS))
            // Mapped to READ_EXTERNAL_STORAGE on Android 12 and older by the UI.
            AppPermission.Photos -> PermissionAction.RequestRuntime(listOf(Manifest.permission.READ_MEDIA_IMAGES))
        }
        viewModelScope.launch { _actions.send(action) }
    }

    /** After a runtime request: if the system won't ask again, the user has to use the app settings. */
    fun onRuntimeResult(stillDenied: Boolean, canAskAgain: Boolean) {
        refresh()
        if (stillDenied && !canAskAgain) viewModelScope.launch { _actions.send(PermissionAction.OpenAppSettings) }
    }

    fun openAppSettings() {
        viewModelScope.launch { _actions.send(PermissionAction.OpenAppSettings) }
    }

    private fun compute(): PermissionsUiState {
        val states = AppPermission.entries.associateWith(status::state)
        val locationGranted = states[AppPermission.Location] == PermissionState.Granted
        val items = AppPermission.entries
            .filter { states[it] != PermissionState.NotApplicable }
            .map { PermissionItem(it, states.getValue(it), enabled = it != AppPermission.BackgroundLocation || locationGranted) }
        val recommendations = buildList {
            if (locationGranted && states[AppPermission.BackgroundLocation] == PermissionState.Denied) add(AppPermission.BackgroundLocation)
            if (states[AppPermission.BatteryOptimization] == PermissionState.Denied) add(AppPermission.BatteryOptimization)
        }
        return PermissionsUiState(items, recommendations)
    }

    private companion object {
        val LOCATION_REQUEST = PermissionAction.RequestRuntime(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
}
