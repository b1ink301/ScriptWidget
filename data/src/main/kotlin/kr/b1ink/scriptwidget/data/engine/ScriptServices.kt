package kr.b1ink.scriptwidget.data.engine

import java.util.Locale
import java.util.TimeZone

/** Native capabilities exposed to scripts. Each one is a port with a JVM fake and a platform implementation. */
class ScriptServices(
    val http: HttpGateway,
    val images: ImageStore,
    val files: ScriptFileSystem,
    val secrets: SecretStore,
    val location: LocationProvider,
    val modules: ModuleSource,
    val notifier: Notifier,
    val symbols: SymbolRenderer,
    val canvas: CanvasRenderer,
    /** Called once per run that uses `Device`. */
    val device: () -> DeviceInfo,
    val urls: UrlOpener,
    /** In-app UI (Alert, UITable, …); only usable for scripts run from the app. */
    val ui: UiPresenter = NoUiPresenter,
    val web: WebPageFactory = NoWebPages,
    val documents: DocumentStore = NoDocuments,
    val clipboard: Clipboard = NoClipboard,
    val composer: Composer = NoComposer,
    val callbacks: CallbackUrlBroker = NoCallbacks,
    val speaker: Speaker = NoSpeaker,
    val calendar: CalendarStore = NoCalendar,
    val reminders: ReminderStore,
    val contacts: ContactStore = NoContacts,
    val photos: PhotoLibrary = NoPhotos,
    /** Defaults for `toLocaleString()`, `Intl` and `DateFormatter`. */
    val locale: Locale = Locale.getDefault(),
    val timeZone: TimeZone = TimeZone.getDefault(),
)
