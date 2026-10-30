package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.Serializable

/** Something that happens on the device and can start a script. [key] is what scripts see in `args.trigger`. */
@Serializable
enum class TriggerEvent(val key: String) {
    CalendarChanged("calendar"),
    ContactsChanged("contacts"),
    NewPhoto("photo"),

    /** Midnight: a new day starts. */
    DayChanged("day"),

    /** Time zone or clock changed (travel, manual time change). */
    TimeZoneChanged("timezone"),

    /** While charging; at most once an hour. */
    Charging("charging"),

    /** While on Wi-Fi (unmetered); at most once an hour. */
    WifiConnected("wifi"),
    ;

    companion object {
        fun fromKey(key: String): TriggerEvent? = entries.firstOrNull { it.key == key }
    }
}

@Serializable
enum class TriggerAction {
    /** Redraw the widgets showing the script (the screen-off policy still applies). */
    RefreshWidgets,

    /** Run the script in the background, e.g. to log something or post a notification. */
    RunScript,
}

/** "When [event] happens, [action] [scriptName]". */
@Serializable
data class EventTrigger(
    val id: String,
    val event: TriggerEvent,
    val scriptName: String,
    val action: TriggerAction = TriggerAction.RefreshWidgets,
    val enabled: Boolean = true,
)
