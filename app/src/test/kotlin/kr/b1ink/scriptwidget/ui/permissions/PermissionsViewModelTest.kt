package kr.b1ink.scriptwidget.ui.permissions

import android.Manifest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.permissions.AppPermission
import kr.b1ink.scriptwidget.permissions.PermissionAction
import kr.b1ink.scriptwidget.permissions.PermissionState
import kr.b1ink.scriptwidget.permissions.PermissionStatusProvider
import kr.b1ink.scriptwidget.ui.MainDispatcherRule
import org.junit.Rule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PermissionsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val states = mutableMapOf(
        AppPermission.Notifications to PermissionState.Denied,
        AppPermission.Location to PermissionState.Denied,
        AppPermission.BackgroundLocation to PermissionState.Denied,
        AppPermission.BatteryOptimization to PermissionState.Denied,
        AppPermission.ExactAlarm to PermissionState.Granted,
        AppPermission.Calendar to PermissionState.Denied,
        AppPermission.Contacts to PermissionState.Denied,
        AppPermission.Photos to PermissionState.Denied,
    )
    private val provider = PermissionStatusProvider { states.getValue(it) }
    private fun viewModel() = PermissionsViewModel(provider)

    @Test
    fun `lists every applicable permission with its state`() = runTest {
        states[AppPermission.ExactAlarm] = PermissionState.NotApplicable

        val items = viewModel().uiState.value.items

        assertEquals(
            listOf(
                AppPermission.Notifications, AppPermission.Location, AppPermission.BackgroundLocation, AppPermission.BatteryOptimization,
                AppPermission.Calendar, AppPermission.Contacts, AppPermission.Photos,
            ),
            items.map { it.permission },
        )
        assertTrue(items.all { it.state == PermissionState.Denied })
    }

    @Test
    fun `background location needs foreground location first`() = runTest {
        val vm = viewModel()
        assertFalse(vm.uiState.value.item(AppPermission.BackgroundLocation).enabled)

        states[AppPermission.Location] = PermissionState.Granted
        vm.refresh()

        assertTrue(vm.uiState.value.item(AppPermission.BackgroundLocation).enabled)
    }

    @Test
    fun `requests map to runtime permissions or settings screens`() = runTest {
        states[AppPermission.Location] = PermissionState.Granted
        val vm = viewModel()

        vm.request(AppPermission.Notifications)
        assertEquals(PermissionAction.RequestRuntime(listOf(Manifest.permission.POST_NOTIFICATIONS)), vm.actions.first())

        vm.request(AppPermission.Location)
        assertEquals(
            PermissionAction.RequestRuntime(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
            vm.actions.first(),
        )

        vm.request(AppPermission.BackgroundLocation)
        assertEquals(PermissionAction.RequestRuntime(listOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)), vm.actions.first())

        vm.request(AppPermission.BatteryOptimization)
        assertEquals(PermissionAction.OpenBatteryOptimizationRequest, vm.actions.first())

        vm.request(AppPermission.ExactAlarm)
        assertEquals(PermissionAction.OpenExactAlarmSettings, vm.actions.first())
    }

    @Test
    fun `requesting background location before location asks for location`() = runTest {
        val vm = viewModel()

        vm.request(AppPermission.BackgroundLocation)

        assertEquals(
            PermissionAction.RequestRuntime(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
            vm.actions.first(),
        )
    }

    @Test
    fun `a runtime request that stays denied without a system prompt points to app settings`() = runTest {
        val vm = viewModel()

        vm.onRuntimeResult(stillDenied = true, canAskAgain = false)
        assertEquals(PermissionAction.OpenAppSettings, vm.actions.first())
    }

    @Test
    fun `results refresh the states`() = runTest {
        val vm = viewModel()
        states[AppPermission.Notifications] = PermissionState.Granted

        vm.onRuntimeResult(stillDenied = false, canAskAgain = true)

        assertEquals(PermissionState.Granted, vm.uiState.value.item(AppPermission.Notifications).state)
    }

    // --- banner on the script list ---

    @Test
    fun `the widget banner asks for battery optimization and background location`() = runTest {
        states[AppPermission.Location] = PermissionState.Granted
        val vm = viewModel()

        assertEquals(listOf(AppPermission.BackgroundLocation, AppPermission.BatteryOptimization), vm.uiState.value.widgetRecommendations)
    }

    @Test
    fun `background location is not recommended when location was never granted`() = runTest {
        assertEquals(listOf(AppPermission.BatteryOptimization), viewModel().uiState.value.widgetRecommendations)
    }

    @Test
    fun `no banner when everything important is granted`() = runTest {
        states[AppPermission.Location] = PermissionState.Granted
        states[AppPermission.BackgroundLocation] = PermissionState.Granted
        states[AppPermission.BatteryOptimization] = PermissionState.Granted

        assertEquals(emptyList(), viewModel().uiState.value.widgetRecommendations)
    }

    private fun PermissionsUiState.item(p: AppPermission) = items.first { it.permission == p }

    @Test
    fun `personal data permissions are runtime requests`() = runTest {
        val vm = viewModel()

        vm.request(AppPermission.Calendar)
        assertEquals(PermissionAction.RequestRuntime(listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)), vm.actions.first())
        vm.request(AppPermission.Contacts)
        assertEquals(PermissionAction.RequestRuntime(listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS)), vm.actions.first())
        vm.request(AppPermission.Photos)
        assertEquals(PermissionAction.RequestRuntime(listOf(Manifest.permission.READ_MEDIA_IMAGES)), vm.actions.first())
    }
}
