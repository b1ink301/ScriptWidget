package kr.b1ink.scriptwidget.ui.scriptui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kr.b1ink.scriptwidget.data.engine.AlertResult
import kr.b1ink.scriptwidget.data.engine.AlertSpec
import kr.b1ink.scriptwidget.data.engine.DatePickerSpec
import kr.b1ink.scriptwidget.data.engine.DocumentRequest
import kr.b1ink.scriptwidget.data.engine.QuickLookSpec
import kr.b1ink.scriptwidget.data.engine.TableEvent
import kr.b1ink.scriptwidget.data.engine.TableSpec
import kr.b1ink.scriptwidget.data.engine.UiPresenter
import kr.b1ink.scriptwidget.data.engine.WebPage
import java.util.concurrent.atomic.AtomicInteger

/** A small modal shown over the current screen. */
sealed interface UiDialog {
    data class Alert(val spec: AlertSpec, val respond: (AlertResult) -> Unit) : UiDialog
    data class DatePicker(val spec: DatePickerSpec, val respond: (Long?) -> Unit) : UiDialog

    /** The system document picker; answered with content URIs (empty when cancelled). */
    data class Documents(val request: DocumentRequest, val respond: (List<String>) -> Unit) : UiDialog

    /** System speech recognition; answered with the text, null when cancelled or unavailable. */
    data class Dictation(val locale: String?, val respond: (String?) -> Unit) : UiDialog

    /** Photo picker ("library") or camera ("camera"); answered with a content URI, null when cancelled. */
    data class ImagePicker(val source: String, val respond: (String?) -> Unit) : UiDialog
}

/** A full screen presented by a script. Dialogs can appear on top of it. */
sealed interface UiScreen {
    data class Table(val spec: TableSpec, val respond: (TableEvent) -> Unit) : UiScreen
    data class QuickLook(val spec: QuickLookSpec, val close: () -> Unit) : UiScreen
    data class Web(val page: WebPage, val fullscreen: Boolean, val close: () -> Unit) : UiScreen
}

/**
 * Bridges suspended script UI calls to Compose: requests are exposed as state, and the UI answers
 * through the callbacks. Requests fail while no screen is attached (e.g. the editor isn't visible).
 */
class ScriptUiHost : UiPresenter {
    private val _dialog = MutableStateFlow<UiDialog?>(null)
    val dialog: StateFlow<UiDialog?> = _dialog.asStateFlow()

    private val _screen = MutableStateFlow<UiScreen?>(null)
    val screen: StateFlow<UiScreen?> = _screen.asStateFlow()

    private val attached = AtomicInteger(0)

    /** Called by screens able to show script UI while they are visible. */
    fun attach() {
        attached.incrementAndGet()
    }

    /** When the last screen goes away, pending requests are answered as cancelled. */
    fun detach() {
        if (attached.decrementAndGet() > 0) return
        when (val d = _dialog.value) {
            is UiDialog.Alert -> d.respond(AlertResult(-1))
            is UiDialog.DatePicker -> d.respond(null)
            is UiDialog.Documents -> d.respond(emptyList())
            is UiDialog.Dictation -> d.respond(null)
            is UiDialog.ImagePicker -> d.respond(null)
            null -> Unit
        }
        when (val s = _screen.value) {
            is UiScreen.Table -> s.respond(TableEvent.Dismissed)
            is UiScreen.QuickLook -> s.close()
            is UiScreen.Web -> s.close()
            null -> Unit
        }
        _dialog.value = null
        _screen.value = null
    }

    override suspend fun alert(spec: AlertSpec): AlertResult = showDialog { respond -> UiDialog.Alert(spec, respond) }

    override suspend fun pickDate(spec: DatePickerSpec): Long? = showDialog { respond -> UiDialog.DatePicker(spec, respond) }

    override suspend fun table(spec: TableSpec): TableEvent {
        checkAttached()
        val event = CompletableDeferred<TableEvent>()
        lateinit var screen: UiScreen.Table
        screen = UiScreen.Table(spec) {
            // Nobody is waiting (the script is busy or has ended): closing must still close.
            if (!event.complete(it) && it == TableEvent.Dismissed) _screen.compareAndSet(screen, null)
        }
        _screen.value = screen
        try {
            val result = event.await()
            // Between events the table stays visible; the script decides when it is done.
            if (result == TableEvent.Dismissed) _screen.compareAndSet(screen, null)
            return result
        } catch (e: CancellationException) {
            _screen.compareAndSet(screen, null)
            throw e
        }
    }

    override suspend fun dismissTable() {
        if (_screen.value is UiScreen.Table) _screen.value = null
    }

    override suspend fun quickLook(spec: QuickLookSpec) {
        checkAttached()
        val closed = CompletableDeferred<Unit>()
        val screen = UiScreen.QuickLook(spec) { closed.complete(Unit) }
        _screen.value = screen
        try {
            closed.await()
        } finally {
            _screen.compareAndSet(screen, null)
        }
    }

    override suspend fun presentWebPage(page: WebPage, fullscreen: Boolean) {
        checkAttached()
        val closed = CompletableDeferred<Unit>()
        val screen = UiScreen.Web(page, fullscreen) { closed.complete(Unit) }
        _screen.value = screen
        try {
            closed.await()
        } finally {
            _screen.compareAndSet(screen, null)
        }
    }

    override suspend fun pickDocuments(request: DocumentRequest): List<String> =
        showDialog { respond -> UiDialog.Documents(request, respond) }

    override suspend fun dictate(locale: String?): String? = showDialog { respond -> UiDialog.Dictation(locale, respond) }

    override suspend fun pickImage(source: String): String? = showDialog { respond -> UiDialog.ImagePicker(source, respond) }

    private suspend fun <T> showDialog(create: ((T) -> Unit) -> UiDialog): T {
        checkAttached()
        val result = CompletableDeferred<T>()
        val dialog = create { result.complete(it) }
        _dialog.value = dialog
        try {
            return result.await()
        } finally {
            _dialog.compareAndSet(dialog, null)
        }
    }

    private fun checkAttached() {
        check(attached.get() > 0) { "No visible app screen to present UI (run the script from the editor)" }
    }
}
