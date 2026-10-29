package kr.b1ink.scriptwidget.permissions

/** Capabilities the user can grant. Several are only possible because the app isn't distributed on Google Play. */
enum class AppPermission {
    /** `Notification.schedule()`. */
    Notifications,

    /** `Location.current()` while the app is open. */
    Location,

    /** `Location.current()` during widget refreshes ("Allow all the time"). */
    BackgroundLocation,

    /** Exempts the app from battery optimization so widget refreshes aren't deferred. */
    BatteryOptimization,

    /** Exact alarms for `refreshAfterDate` (USE_EXACT_ALARM is granted at install time). */
    ExactAlarm,

    /** `Calendar` / `CalendarEvent`. */
    Calendar,

    /** `Contact`, `ContactsContainer`, `ContactsGroup`. */
    Contacts,

    /** `Photos.latestPhoto(s)` / `latestScreenshot(s)`. */
    Photos,
}

enum class PermissionState { Granted, Denied, NotApplicable }

fun interface PermissionStatusProvider {
    fun state(permission: AppPermission): PermissionState
}

/** What the UI must do to request a permission. */
sealed interface PermissionAction {
    data class RequestRuntime(val permissions: List<String>) : PermissionAction
    data object OpenBatteryOptimizationRequest : PermissionAction
    data object OpenExactAlarmSettings : PermissionAction
    data object OpenAppSettings : PermissionAction
}
