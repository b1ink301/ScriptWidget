package kr.b1ink.scriptwidget.data.engine

import java.io.IOException

class FakeSymbolRenderer : SymbolRenderer {
    data class Call(val materialName: String, val sizePx: Int, val weight: String)

    val calls = mutableListOf<Call>()

    override fun render(materialName: String, sizePx: Int, weight: String): ByteArray {
        calls += Call(materialName, sizePx, weight)
        return byteArrayOf(1, 2, 3, 4)
    }
}

class FakeCanvasRenderer : CanvasRenderer {
    val specs = mutableListOf<CanvasSpec>()
    override fun render(spec: CanvasSpec, imageBytes: (String) -> ByteArray?): ByteArray {
        specs += spec
        return byteArrayOf(5, 6, 7)
    }
}

class FakeUrlOpener : UrlOpener {
    val opened = mutableListOf<String>()
    override fun open(url: String) {
        opened += url
    }
}

val TEST_DEVICE = DeviceInfo(
    name = "Pixel Test", systemVersion = "17", model = "Pixel 10 Pro", isPhone = true,
    screenWidth = 412f, screenHeight = 915f, screenScale = 3.5f, screenBrightness = 0.5,
    orientation = "portrait", batteryLevel = 0.8, isCharging = true, isFullyCharged = false,
    preferredLanguages = listOf("ko-KR", "en-US"), locale = "ko_KR", isUsingDarkAppearance = true, volume = 0.25,
)

/** Scripted UI: answers come from the queues; every request is recorded. */
class FakeUiPresenter : UiPresenter {
    val imagePicks = mutableListOf<String>()
    var imageAnswer: String? = "content://picked"

    override suspend fun pickImage(source: String): String? {
        imagePicks += source
        return imageAnswer
    }

    val dictations = mutableListOf<String?>()
    var dictationAnswer: String? = "hello world"

    override suspend fun dictate(locale: String?): String? {
        dictations += locale
        return dictationAnswer
    }

    val presentedPages = mutableListOf<Pair<WebPage, Boolean>>()
    val documentRequests = mutableListOf<DocumentRequest>()
    val documentAnswers = ArrayDeque<List<String>>()

    override suspend fun presentWebPage(page: WebPage, fullscreen: Boolean) {
        presentedPages += page to fullscreen
    }

    override suspend fun pickDocuments(request: DocumentRequest): List<String> {
        documentRequests += request
        return documentAnswers.removeFirstOrNull() ?: emptyList()
    }

    val alerts = mutableListOf<AlertSpec>()
    val alertAnswers = ArrayDeque<AlertResult>()
    val tables = mutableListOf<TableSpec>()
    val tableEvents = ArrayDeque<TableEvent>()
    var tableDismissals = 0
    val datePickers = mutableListOf<DatePickerSpec>()
    val dateAnswers = ArrayDeque<Long?>()
    val quickLooks = mutableListOf<QuickLookSpec>()
    var delayMillis = 0L

    override suspend fun alert(spec: AlertSpec): AlertResult {
        alerts += spec
        kotlinx.coroutines.delay(delayMillis)
        return alertAnswers.removeFirst()
    }

    override suspend fun table(spec: TableSpec): TableEvent {
        tables += spec
        return tableEvents.removeFirstOrNull() ?: TableEvent.Dismissed
    }

    override suspend fun dismissTable() {
        tableDismissals++
    }

    override suspend fun pickDate(spec: DatePickerSpec): Long? {
        datePickers += spec
        return dateAnswers.removeFirst()
    }

    override suspend fun quickLook(spec: QuickLookSpec) {
        quickLooks += spec
    }
}

/** In-memory web pages: HTML is kept as given; evaluate() answers from [answers]. */
class FakeWebPages : WebPageFactory {
    val pages = mutableListOf<FakePage>()
    val answers = mutableMapOf<String, String>()

    inner class FakePage : WebPage {
        val log = mutableListOf<String>()
        var html = ""
        var closed = false

        override suspend fun loadUrl(url: String) {
            log += "url:$url"
            html = "<html><body>$url</body></html>"
        }

        override suspend fun loadHtml(html: String, baseUrl: String?) {
            log += "html:${baseUrl ?: ""}"
            this.html = html
        }

        override suspend fun evaluate(script: String, useCallback: Boolean): String {
            log += "eval:$useCallback:$script"
            return answers[script] ?: "null"
        }

        override suspend fun html(): String = html

        override fun close() {
            closed = true
        }
    }

    override fun create(): WebPage = FakePage().also { pages += it }
}

/** Documents keyed by handle; imports write into the sandbox like the Android implementation. */
class FakeDocuments(private val files: () -> ScriptFileSystem) : DocumentStore {
    val contents = mutableMapOf<String, Pair<String, ByteArray>>()
    val written = mutableMapOf<String, ByteArray>()

    override fun importToSandbox(handle: String): String {
        val (name, bytes) = contents.getValue(handle)
        val path = "${files().temporaryDirectory}/picked/$name"
        files().writeBytes(path, bytes)
        return path
    }

    override fun write(handle: String, bytes: ByteArray) {
        written[handle] = bytes
    }
}

class FakeClipboard : Clipboard {
    var text: String? = null
    override fun copy(text: String) { this.text = text }
    override fun paste(): String? = text
}

class FakeComposer : Composer {
    val shares = mutableListOf<ShareSpec>()
    val mails = mutableListOf<MailSpec>()
    val messages = mutableListOf<MessageSpec>()
    override fun share(spec: ShareSpec) { shares += spec }
    override fun mail(spec: MailSpec) { mails += spec }
    override fun message(spec: MessageSpec) { messages += spec }
}

class FakeCallbackBroker : CallbackUrlBroker {
    val opened = mutableListOf<String>()
    var answer: CallbackResult = CallbackResult("success", mapOf("result" to "ok"))
    override suspend fun open(url: String, requestId: String): CallbackResult {
        opened += url
        return answer
    }
}

class FakeSpeaker : Speaker {
    val spoken = mutableListOf<String>()
    override suspend fun speak(text: String) { spoken += text }
}

class FakeCalendarStore : CalendarStore {
    val calendars = mutableListOf(CalendarInfo("1", "Personal", "#FF2196F3"), CalendarInfo("2", "Work", "#FFFF5722"))
    val events = mutableListOf<EventData>()
    val queries = mutableListOf<Triple<Long, Long, List<String>?>>()
    val edited = mutableListOf<String?>()
    private var nextId = 100

    override fun calendars() = calendars.toList()
    override fun defaultCalendar() = calendars.first()
    override fun events(start: Long, end: Long, calendarIds: List<String>?): List<EventData> {
        queries += Triple(start, end, calendarIds)
        return events.filter { it.start < end && it.end > start && (calendarIds == null || it.calendarId in calendarIds) }
    }
    override fun save(event: EventData): String {
        val id = event.identifier ?: (nextId++).toString()
        events.removeAll { it.identifier == id }
        events += event.copy(identifier = id)
        return id
    }
    override fun remove(identifier: String) { events.removeAll { it.identifier == identifier } }
    override fun openEditor(identifier: String?) { edited += identifier }
}

class FakeContactStore : ContactStore {
    val containers = mutableListOf(ContactContainer("local", "Phone"), ContactContainer("g", "Google"))
    val contacts = mutableListOf<ContactData>()
    val groups = mutableListOf<ContactGroup>()
    val memberships = mutableSetOf<Membership>()
    val applied = mutableListOf<ContactChanges>()
    private var nextId = 1

    override fun containers() = containers.toList()
    override fun defaultContainer() = containers.first()
    override fun contacts(containerIds: List<String>?) = contacts.filter { containerIds == null || it.containerId in containerIds }
    override fun contactsInGroups(groupIds: List<String>) =
        contacts.filter { c -> memberships.any { it.contactId == c.identifier && it.groupId in groupIds } }
    override fun image(contactId: String): ByteArray? = if (contactId == "with-photo") byteArrayOf(4, 2) else null
    override fun groups(containerIds: List<String>?) = groups.filter { containerIds == null || it.containerId in containerIds }
    override fun apply(changes: ContactChanges) {
        applied += changes
        changes.adds.forEach { contacts += it.copy(identifier = "c${nextId++}") }
        changes.updates.forEach { u -> contacts.replaceAll { if (it.identifier == u.identifier) u else it } }
        contacts.removeAll { it.identifier in changes.deletes }
        changes.groupAdds.forEach { groups += it.copy(identifier = "g${nextId++}") }
        groups.removeAll { it.identifier in changes.groupDeletes }
        memberships += changes.memberAdds
        memberships -= changes.memberRemoves.toSet()
    }
}

class FakePhotoLibrary : PhotoLibrary {
    val photos = mutableListOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))
    val screenshots = mutableListOf(byteArrayOf(9))
    val saved = mutableListOf<ByteArray>()
    override fun latest(count: Int, screenshots: Boolean) = (if (screenshots) this.screenshots else photos).take(count)
    override fun read(handle: String): ByteArray? = if (handle == "content://picked") byteArrayOf(7, 7) else null
    override fun save(bytes: ByteArray) { saved += bytes }
}

class FakeNotifier : Notifier {
    val scheduled = mutableListOf<NotificationSpec>()
    val removed = mutableListOf<List<String>>()
    var failure: Exception? = null

    override fun schedule(spec: NotificationSpec) {
        failure?.let { throw it }
        scheduled += spec
    }

    override fun remove(identifiers: List<String>) {
        removed += identifiers
    }

    var pendingSpecs = listOf<NotificationSpec>()
    var deliveredSpecs = listOf<NotificationSpec>()

    override fun pending(): List<NotificationSpec> = pendingSpecs

    override fun delivered(): List<NotificationSpec> = deliveredSpecs
}

class FakeHttpGateway : HttpGateway {
    val requests = mutableListOf<HttpRequestSpec>()
    var responses: (HttpRequestSpec) -> HttpResponseSpec = { HttpResponseSpec(it.url, 200, emptyMap(), "text/plain", "") }
    var bytes: (HttpRequestSpec) -> ByteArray = { byteArrayOf(1, 2, 3) }

    override suspend fun loadString(spec: HttpRequestSpec): HttpResponseSpec {
        requests += spec
        return responses(spec)
    }

    override suspend fun loadBytes(spec: HttpRequestSpec): ByteArray {
        requests += spec
        return bytes(spec)
    }

    companion object {
        fun failing(message: String) = FakeHttpGateway().apply { responses = { throw IOException(message) } }
    }
}

class FakeImageStore : ImageStore {
    data class Stored(val runKey: String, val index: Int, val size: Int)

    val stored = mutableListOf<Stored>()
    val retained = mutableListOf<Pair<String, String>>()
    val cleared = mutableListOf<String>()

    override fun store(runKey: String, index: Int, bytes: ByteArray): CachedImage {
        stored += Stored(runKey, index, bytes.size)
        return CachedImage(src = "/cache/$runKey/img_$index.png", width = 40, height = 30)
    }

    override fun retainOnly(cacheKey: String, runKey: String) {
        retained += cacheKey to runKey
    }

    override fun clear(key: String) {
        cleared += key
    }

    override fun bytes(src: String): ByteArray? = if (src.startsWith("/cache/")) PNG_BYTES else null

    companion object {
        val PNG_BYTES = byteArrayOf(9, 8, 7)
    }
}

class InMemorySecretStore : SecretStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String): String? = values[key]
    override fun set(key: String, value: String) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun contains(key: String): Boolean = key in values
}

class FakeLocationProvider(
    var location: GeoLocation? = GeoLocation(latitude = 37.5665, longitude = 126.978, altitude = 38.0, horizontalAccuracy = 5.0, verticalAccuracy = 3.0, timestamp = 1_000L),
    var failure: LocationUnavailableException? = null,
    var placemarks: List<Placemark> = emptyList(),
) : LocationProvider {
    val requestedAccuracies = mutableListOf<LocationAccuracy>()

    override suspend fun current(accuracy: LocationAccuracy): GeoLocation {
        requestedAccuracies += accuracy
        failure?.let { throw it }
        return location ?: throw LocationUnavailableException("no fix")
    }

    override suspend fun reverseGeocode(latitude: Double, longitude: Double, locale: String?): List<Placemark> = placemarks
}

class MapModuleSource(private val modules: Map<String, String>) : ModuleSource {
    override fun load(name: String): String? = modules[name]
}

/** Test helper with every service faked. */
fun fakeServices(
    http: HttpGateway = FakeHttpGateway(),
    images: ImageStore = FakeImageStore(),
    files: ScriptFileSystem,
    secrets: SecretStore = InMemorySecretStore(),
    location: LocationProvider = FakeLocationProvider(),
    modules: ModuleSource = MapModuleSource(emptyMap()),
    notifier: Notifier = FakeNotifier(),
    symbols: SymbolRenderer = FakeSymbolRenderer(),
    canvas: CanvasRenderer = FakeCanvasRenderer(),
    device: () -> DeviceInfo = { TEST_DEVICE },
    urls: UrlOpener = FakeUrlOpener(),
    ui: UiPresenter = FakeUiPresenter(),
    web: WebPageFactory = FakeWebPages(),
    documents: DocumentStore = FakeDocuments { files },
    clipboard: Clipboard = FakeClipboard(),
    composer: Composer = FakeComposer(),
    callbacks: CallbackUrlBroker = FakeCallbackBroker(),
    speaker: Speaker = FakeSpeaker(),
    calendar: CalendarStore = FakeCalendarStore(),
    reminders: ReminderStore = kr.b1ink.scriptwidget.data.personal.FileReminderStore(java.io.File.createTempFile("reminders", ".json").apply { delete() }, FakeNotifier()),
    contacts: ContactStore = FakeContactStore(),
    photos: PhotoLibrary = FakePhotoLibrary(),
    locale: java.util.Locale = java.util.Locale.US,
    timeZone: java.util.TimeZone = java.util.TimeZone.getTimeZone("Asia/Seoul"),
) = ScriptServices(
    http = http, images = images, files = files, secrets = secrets, location = location, modules = modules,
    notifier = notifier, symbols = symbols, canvas = canvas, device = device, urls = urls, ui = ui, web = web, documents = documents,
    clipboard = clipboard, composer = composer, callbacks = callbacks, speaker = speaker,
    calendar = calendar, reminders = reminders, contacts = contacts, photos = photos,
    locale = locale, timeZone = timeZone,
)
