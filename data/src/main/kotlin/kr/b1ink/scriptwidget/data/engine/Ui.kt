package kr.b1ink.scriptwidget.data.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AlertAction(val title: String, val role: String = "default")

@Serializable
data class AlertTextField(val placeholder: String = "", val text: String = "", val secure: Boolean = false)

/** `Alert`: [style] is "alert" or "sheet". */
@Serializable
data class AlertSpec(
    val title: String? = null,
    val message: String? = null,
    val actions: List<AlertAction> = emptyList(),
    val textFields: List<AlertTextField> = emptyList(),
    val style: String = "alert",
)

/** [index] of the chosen action, -1 when cancelled; [texts] are the text field values. */
@Serializable
data class AlertResult(val index: Int, val texts: List<String> = emptyList())

@Serializable
data class TableCell(
    /** "text" | "image" | "button" */
    val type: String,
    val title: String? = null,
    val subtitle: String? = null,
    val titleColor: String? = null,
    val subtitleColor: String? = null,
    val src: String? = null,
    val align: String = "left",
    val widthWeight: Float? = null,
)

@Serializable
data class TableRow(
    val cells: List<TableCell> = emptyList(),
    val height: Float? = null,
    val isHeader: Boolean = false,
    val backgroundColor: String? = null,
    val selectable: Boolean = false,
)

@Serializable
data class TableSpec(
    val rows: List<TableRow> = emptyList(),
    val showSeparators: Boolean = false,
    val fullscreen: Boolean = false,
)

/** What happened while a `UITable` was shown. The table stays visible until dismissed. */
@Serializable
sealed interface TableEvent {
    @Serializable @SerialName("selected") data class Selected(val row: Int) : TableEvent
    @Serializable @SerialName("tapped") data class CellTapped(val row: Int, val cell: Int) : TableEvent
    @Serializable @SerialName("dismissed") data object Dismissed : TableEvent
}

/** `DatePicker`: mode is "date" | "time" | "dateAndTime" | "countdown". */
@Serializable
data class DatePickerSpec(
    val mode: String,
    val initial: Long? = null,
    val min: Long? = null,
    val max: Long? = null,
    val countdownSeconds: Int? = null,
)

/** `QuickLook`: either an image file ([src]) or [text]. */
@Serializable
data class QuickLookSpec(val text: String? = null, val src: String? = null, val fullscreen: Boolean = false)

/**
 * Presents in-app UI for scripts run from the app and suspends until the user responds.
 * Implementations throw when no screen is available.
 */
interface UiPresenter {
    suspend fun alert(spec: AlertSpec): AlertResult

    /** Shows (or updates) the table and waits for the next [TableEvent]. */
    suspend fun table(spec: TableSpec): TableEvent

    suspend fun dismissTable()

    /** Epoch millis (or the duration in millis for "countdown"); null when cancelled. */
    suspend fun pickDate(spec: DatePickerSpec): Long?

    suspend fun quickLook(spec: QuickLookSpec)

    /** Shows the page until the user closes it. */
    suspend fun presentWebPage(page: WebPage, fullscreen: Boolean)

    /** Opens the system document picker; returns handles of the chosen documents (empty when cancelled). */
    suspend fun pickDocuments(request: DocumentRequest): List<String>

    /** Speech recognition; returns the recognized text, null when cancelled. */
    suspend fun dictate(locale: String?): String?

    /** Photo picker ("library") or camera ("camera"); returns a handle, null when cancelled. */
    suspend fun pickImage(source: String): String?
}

/** For contexts without a screen. */
object NoUiPresenter : UiPresenter {
    private fun unavailable(): Nothing = throw IllegalStateException("No screen is available to present UI")
    override suspend fun alert(spec: AlertSpec): AlertResult = unavailable()
    override suspend fun table(spec: TableSpec): TableEvent = unavailable()
    override suspend fun dismissTable() = Unit
    override suspend fun pickDate(spec: DatePickerSpec): Long? = unavailable()
    override suspend fun quickLook(spec: QuickLookSpec) = unavailable()
    override suspend fun presentWebPage(page: WebPage, fullscreen: Boolean) = unavailable()
    override suspend fun pickDocuments(request: DocumentRequest): List<String> = unavailable()
    override suspend fun dictate(locale: String?): String? = unavailable()
    override suspend fun pickImage(source: String): String? = unavailable()
}
