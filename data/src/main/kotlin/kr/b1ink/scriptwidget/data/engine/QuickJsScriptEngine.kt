package kr.b1ink.scriptwidget.data.engine

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import com.dokar.quickjs.quickJs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kr.b1ink.scriptwidget.data.format.NativeFormatter
import kr.b1ink.scriptwidget.data.symbols.SfSymbols
import kr.b1ink.scriptwidget.domain.engine.ScriptEngine
import kr.b1ink.scriptwidget.domain.model.LogLine
import kr.b1ink.scriptwidget.domain.model.RunTimings
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.domain.model.WidgetTree
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.typeOf

/**
 * Runs scripts on QuickJS. Every run gets a fresh runtime, so scripts can't leak state into
 * each other. Script errors are returned in [ScriptRunResult.error], never thrown.
 */
class QuickJsScriptEngine(
    private val prelude: String,
    private val services: ScriptServices,
    /** Maximum script *activity* time; time spent waiting for the user in in-app UI doesn't count. */
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
    /** Hard cap on the total duration of interactive (in-app) runs, including UI waits. */
    private val interactiveTimeoutMillis: Long = DEFAULT_INTERACTIVE_TIMEOUT_MS,
    private val memoryLimitBytes: Long = DEFAULT_MEMORY_LIMIT_BYTES,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ScriptEngine {

    override suspend fun execute(script: String, env: ScriptEnvironment, scriptName: String): ScriptRunResult =
        withContext(dispatcher) {
            val logs = mutableListOf<LogLine>()
            var widgetJson: String? = null
            var shortcutOutput: String? = null
            val imageIndex = AtomicInteger(0)
            val runKey = "${env.cacheKey}/${System.currentTimeMillis()}"
            val start = System.nanoTime()
            var createdAt = start
            var preludeDoneAt = start
            var scriptStart = 0L

            val activity = ActivityClock()
            val webPages = mutableMapOf<Int, WebPage>()
            val tableShown = AtomicBoolean(false)
            var timedOut = false
            val error: String? = try {
                withTimeout(if (env.runsInWidget) timeoutMillis else interactiveTimeoutMillis) {
                  coroutineScope {
                    val run = async {
                    quickJs(dispatcher) {
                        memoryLimit = memoryLimitBytes
                        defineFileAndKeychainBindings(runKey, imageIndex::getAndIncrement)
                        defineLocationAndModuleBindings()
                        defineFormatBindings()
                        defineUtilityBindings(env)
                        defineUiBindings(env, activity, tableShown)
                        defineWebAndDocumentBindings(env, activity, webPages, runKey)
                        defineSystemBindings(env, activity)
                        definePersonalDataBindings(env, activity, runKey, imageIndex::getAndIncrement)
                        defineBindings(
                            runKey = runKey,
                            onLog = { level, message -> synchronized(logs) { logs += LogLine(level, message) } },
                            onSetWidget = { widgetJson = it },
                            onSetOutput = { shortcutOutput = it },
                            nextImageIndex = imageIndex::getAndIncrement,
                        )
                        createdAt = System.nanoTime()

                        evaluate<Any?>(envScript(env, scriptName), typeOf<Any?>(), "env.js")
                        evaluate<Any?>(prelude, typeOf<Any?>(), "prelude.js")
                        preludeDoneAt = System.nanoTime()

                        scriptStart = System.nanoTime()
                        // Keep the user's first line on line 1 so error locations stay meaningful.
                        evaluate<Any?>("(async () => {$script\n})()", typeOf<Any?>(), "$scriptName.js")
                    }
                    }
                    // Watchdog: cancels the run (which interrupts QuickJS) once its activity exceeds the limit.
                    val watchdog = launch {
                        while (isActive) {
                            delay(WATCHDOG_INTERVAL_MS)
                            if (activity.elapsed() > timeoutMillis) {
                                timedOut = true
                                run.cancel()
                            }
                        }
                    }
                    try {
                        run.await()
                    } finally {
                        watchdog.cancel()
                    }
                    null
                  }
                }
            } catch (e: TimeoutCancellationException) {
                timeoutMessage()
            } catch (e: QuickJsInterruptedException) {
                timeoutMessage()
            } catch (e: kotlinx.coroutines.CancellationException) {
                if (!timedOut) throw e
                timeoutMessage()
            } catch (e: QuickJsException) {
                e.message ?: e.toString()
            } catch (e: Exception) {
                "${e::class.simpleName}: ${e.message}"
            }

            webPages.values.forEach { runCatching { it.close() } }
            // A table still on screen after the script ended (e.g. a handler threw) would be stuck.
            if (tableShown.get()) withContext(NonCancellable) { runCatching { services.ui.dismissTable() } }

            val end = System.nanoTime()
            val timings = RunTimings(
                createMs = (createdAt - start).toMillis(),
                preludeMs = (preludeDoneAt - createdAt).coerceAtLeast(0).toMillis(),
                scriptMs = if (scriptStart > 0) (end - scriptStart).toMillis() else 0,
                totalMs = (end - start).toMillis(),
            )

            var finalError = error
            var widget: WidgetRoot? = null
            if (finalError == null) {
                widget = widgetJson?.let { json ->
                    runCatching { WidgetTree.decode(json) }
                        .onFailure { finalError = "Invalid widget tree: ${it.message}" }
                        .getOrNull()
                }
            }

            if (finalError == null) services.images.retainOnly(env.cacheKey, runKey) else services.images.clear(runKey)
            ScriptRunResult(widget, logs.toList(), finalError, timings, shortcutOutput)
        }

    private fun QuickJs.defineBindings(
        runKey: String,
        onLog: (String, String) -> Unit,
        onSetWidget: (String) -> Unit,
        onSetOutput: (String) -> Unit,
        nextImageIndex: () -> Int,
    ) {
        function("__setOutput") { args ->
            onSetOutput(args[0] as String)
            null
        }
        function("__log") { args ->
            onLog(args.getOrNull(0)?.toString() ?: "log", args.getOrNull(1)?.toString() ?: "")
            null
        }
        function("__setWidget") { args ->
            onSetWidget(args[0] as String)
            null
        }
        asyncFunction("__sleep") { args ->
            delay((args.getOrNull(0) as? Number)?.toLong() ?: 0L)
            null
        }
        asyncFunction("__http") { args ->
            val spec = Json.decodeFromString<HttpRequestSpec>(args[0] as String)
            Json.encodeToString(HttpResponseSpec.serializer(), services.http.loadString(spec))
        }
        asyncFunction("__httpBytes") { args ->
            val spec = Json.decodeFromString<HttpRequestSpec>(args[0] as String)
            base64.encodeToString(services.http.loadBytes(spec))
        }
        asyncFunction("__loadImage") { args ->
            val spec = Json.decodeFromString<HttpRequestSpec>(args[0] as String)
            val bytes = services.http.loadBytes(spec)
            val image = withContext(ioDispatcher) { services.images.store(runKey, nextImageIndex(), bytes) }
            Json.encodeToString(CachedImage.serializer(), image)
        }
    }

    private fun QuickJs.defineFileAndKeychainBindings(runKey: String, nextImageIndex: () -> Int) {
        val files = services.files
        function("__fs") { args ->
            val path = args.getOrNull(1) as? String
            when (args[0] as String) {
                "dirs" -> Json.encodeToString(
                    listOf(files.documentsDirectory, files.cacheDirectory, files.temporaryDirectory, files.bookmarksDirectory)
                )
                "readString" -> files.readString(path!!)
                "writeString" -> files.writeString(path!!, args[2] as String).let { null }
                "exists" -> files.exists(path!!)
                "isDirectory" -> files.isDirectory(path!!)
                "createDirectory" -> files.createDirectory(path!!, args.getOrNull(2) == true).let { null }
                "list" -> Json.encodeToString(files.list(path!!))
                "remove" -> files.remove(path!!).let { null }
                "modificationDate" -> files.modificationDate(path!!)?.toDouble()
                "size" -> files.size(path!!)?.toDouble()
                "readImage" -> files.readBytes(path!!)?.let { bytes ->
                    Json.encodeToString(CachedImage.serializer(), services.images.store(runKey, nextImageIndex(), bytes))
                }
                "readBase64" -> files.readBytes(path!!)?.let(base64::encodeToString)
                "writeBase64" -> files.writeBytes(path!!, decodeBase64(args[2] as String)).let { null }
                "writeImage" -> {
                    val bytes = services.images.bytes(args[2] as String)
                        ?: throw IllegalArgumentException("Image data is no longer available")
                    files.writeBytes(path!!, bytes)
                    null
                }
                else -> throw IllegalArgumentException("Unknown FileManager operation: ${args[0]}")
            }
        }
        function("__data") { args ->
            val arg = args[1] as String
            when (args[0] as String) {
                "fromString" -> base64.encodeToString(arg.toByteArray(Charsets.UTF_8))
                "toString" -> String(decodeBase64(arg), Charsets.UTF_8)
                "fromBytes" -> base64.encodeToString(
                    Json.decodeFromString<List<Int>>(arg).map { it.toByte() }.toByteArray()
                )
                "toBytes" -> Json.encodeToString(decodeBase64(arg).map { it.toInt() and 0xFF })
                "validate" -> base64.encodeToString(decodeBase64(arg))
                "toImage" -> Json.encodeToString(
                    CachedImage.serializer(),
                    services.images.store(runKey, nextImageIndex(), decodeBase64(arg)),
                )
                "fromImage" -> services.images.bytes(arg)?.let(base64::encodeToString)
                    ?: throw IllegalArgumentException("Image data is no longer available")
                else -> throw IllegalArgumentException("Unknown Data operation: ${args[0]}")
            }
        }
        function("__symbol") { args ->
            val resolved = SfSymbols.resolve(args[0] as String)
            val sizePx = (args[1] as Number).toInt().coerceIn(8, 512)
            val png = services.symbols.render(resolved.materialName, sizePx, args[2] as String)
                ?: services.symbols.render(SfSymbols.FALLBACK, sizePx, args[2] as String)
                ?: throw IllegalStateException("Symbol rendering is unavailable")
            val image = services.images.store(runKey, nextImageIndex(), png)
            Json.encodeToString(SymbolImage.serializer(), SymbolImage(image.src, image.width, image.height, resolved.isFallback))
        }
        function("__canvas") { args ->
            val spec = CanvasJson.decodeFromString(CanvasSpec.serializer(), args[0] as String)
            val png = services.canvas.render(spec, services.images::bytes)
            Json.encodeToString(CachedImage.serializer(), services.images.store(runKey, nextImageIndex(), png))
        }
        function("__keychain") { args ->
            val key = args[1] as String
            when (args[0] as String) {
                "get" -> services.secrets.get(key)
                "set" -> services.secrets.set(key, args[2] as String).let { null }
                "contains" -> services.secrets.contains(key)
                "remove" -> services.secrets.remove(key).let { null }
                else -> throw IllegalArgumentException("Unknown Keychain operation: ${args[0]}")
            }
        }
    }

    private fun QuickJs.defineLocationAndModuleBindings() {
        asyncFunction("__location") { args ->
            when (args[0] as String) {
                "current" -> {
                    val accuracy = LocationAccuracy.valueOf(args[1] as String)
                    Json.encodeToString(GeoLocation.serializer(), services.location.current(accuracy))
                }
                "reverse" -> {
                    val request = Json.decodeFromString<ReverseGeocodeRequest>(args[1] as String)
                    val placemarks = services.location.reverseGeocode(request.lat, request.lon, request.locale)
                    Json.encodeToString(placemarks)
                }
                else -> throw IllegalArgumentException("Unknown Location operation: ${args[0]}")
            }
        }
        function("__module") { args -> services.modules.load(args[0] as String) }
        function("__notify") { args ->
            when (args[0] as String) {
                "schedule" -> services.notifier.schedule(Json.decodeFromString<NotificationSpec>(args[1] as String)).let { null }
                "remove" -> services.notifier.remove(Json.decodeFromString<List<String>>(args[1] as String)).let { null }
                "pending" -> Json.encodeToString(services.notifier.pending())
                "delivered" -> Json.encodeToString(services.notifier.delivered())
                else -> throw IllegalArgumentException("Unknown Notification operation: ${args[0]}")
            }
        }
    }

    private val formatter by lazy { NativeFormatter(services.locale, services.timeZone) }

    private fun QuickJs.defineFormatBindings() {
        function("__fmt") { args ->
            val json = args[1] as String
            when (args[0] as String) {
                "number" -> formatter.number(Json.decodeFromString<NativeFormatter.NumberSpec>(json))
                "date" -> formatter.date(Json.decodeFromString<NativeFormatter.DateSpec>(json))
                "parse" -> formatter.parse(Json.decodeFromString<NativeFormatter.ParseSpec>(json))
                "defaults" -> formatter.defaults()
                else -> throw IllegalArgumentException("Unknown format operation: ${args[0]}")
            }
        }
    }

    private val UiJson = Json { classDiscriminator = "type"; ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    private fun QuickJs.defineUiBindings(env: ScriptEnvironment, activity: ActivityClock, tableShown: AtomicBoolean) {
        asyncFunction("__ui") { args ->
            if (env.runsInWidget) throw IllegalStateException("UI can't be presented during a widget refresh")
            val json = args.getOrNull(1) as? String ?: "{}"
            val ui = services.ui
            // Waiting for the user doesn't count as script activity.
            activity.paused {
                when (args[0] as String) {
                    "alert" -> UiJson.encodeToString(AlertResult.serializer(), ui.alert(UiJson.decodeFromString(AlertSpec.serializer(), json)))
                    "table" -> {
                        tableShown.set(true)
                        val event = ui.table(UiJson.decodeFromString(TableSpec.serializer(), json))
                        if (event == TableEvent.Dismissed) tableShown.set(false)
                        UiJson.encodeToString(TableEvent.serializer(), event)
                    }
                    "tableDismiss" -> ui.dismissTable().also { tableShown.set(false) }.let { null }
                    "date" -> ui.pickDate(UiJson.decodeFromString(DatePickerSpec.serializer(), json))?.toDouble()
                    "quickLook" -> ui.quickLook(UiJson.decodeFromString(QuickLookSpec.serializer(), json)).let { null }
                    "dictate" -> ui.dictate(UiJson.parseToJsonElement(json).let { (it as? kotlinx.serialization.json.JsonObject)?.get("locale") }
                        ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { p -> p.isString }?.content })
                    else -> throw IllegalArgumentException("Unknown UI operation: ${args[0]}")
                }
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class WebArgs(
        val url: String? = null,
        val html: String? = null,
        val baseUrl: String? = null,
        val script: String? = null,
        val callback: Boolean = false,
        val fullscreen: Boolean = false,
    )

    @kotlinx.serialization.Serializable
    private data class ExportArgs(
        val name: String,
        val mimeType: String,
        val text: String? = null,
        val path: String? = null,
        val src: String? = null,
        val base64: String? = null,
    )

    private fun QuickJs.defineWebAndDocumentBindings(
        env: ScriptEnvironment,
        activity: ActivityClock,
        pages: MutableMap<Int, WebPage>,
        runKey: String,
    ) {
        val nextPage = AtomicInteger(1)
        function("__webCreate") {
            val id = nextPage.getAndIncrement()
            synchronized(pages) { pages[id] = services.web.create() }
            id
        }
        asyncFunction("__web") { args ->
            val op = args[0] as String
            val page = synchronized(pages) { pages[(args[1] as Number).toInt()] } ?: throw IllegalStateException("Unknown WebView")
            val a = UiJson.decodeFromString(WebArgs.serializer(), args.getOrNull(2) as? String ?: "{}")
            when (op) {
                "loadUrl" -> page.loadUrl(requireNotNull(a.url)).let { null }
                "loadHtml" -> page.loadHtml(a.html.orEmpty(), a.baseUrl).let { null }
                "eval" -> page.evaluate(requireNotNull(a.script), a.callback)
                "html" -> page.html()
                "present" -> {
                    if (env.runsInWidget) throw IllegalStateException("WebView can't be presented during a widget refresh")
                    activity.paused { services.ui.presentWebPage(page, a.fullscreen) }
                    null
                }
                else -> throw IllegalArgumentException("Unknown WebView operation: $op")
            }
        }
        asyncFunction("__doc") { args ->
            if (env.runsInWidget) throw IllegalStateException("DocumentPicker can't be used during a widget refresh")
            val json = args.getOrNull(1) as? String ?: "{}"
            when (args[0] as String) {
                "open" -> {
                    val request = UiJson.decodeFromString(DocumentRequest.serializer(), json)
                    val handles = activity.paused { services.ui.pickDocuments(request.copy(kind = "open")) }
                    Json.encodeToString(handles.map(services.documents::importToSandbox))
                }
                "export" -> {
                    val export = UiJson.decodeFromString(ExportArgs.serializer(), json)
                    val bytes = when {
                        export.text != null -> export.text.toByteArray(Charsets.UTF_8)
                        export.path != null -> services.files.readBytes(export.path)
                            ?: throw IllegalArgumentException("File not found: ${export.path}")
                        export.src != null -> services.images.bytes(export.src)
                            ?: throw IllegalArgumentException("Image data is no longer available")
                        export.base64 != null -> decodeBase64(export.base64)
                        else -> throw IllegalArgumentException("Nothing to export")
                    }
                    val handles = activity.paused {
                        services.ui.pickDocuments(DocumentRequest("create", listOf(export.mimeType), suggestedName = export.name))
                    }
                    val handle = handles.firstOrNull() ?: throw IllegalStateException("DocumentPicker was cancelled")
                    services.documents.write(handle, bytes)
                    Json.encodeToString(listOf(handle))
                }
                else -> throw IllegalArgumentException("Unknown DocumentPicker operation: ${args[0]}")
            }
        }
    }

    @kotlinx.serialization.Serializable
    private data class RangeArgs(val start: Long = 0, val end: Long = 0, val ids: List<String>? = null)

    @kotlinx.serialization.Serializable
    private data class IdArgs(val identifier: String? = null, val title: String? = null, val ids: List<String>? = null)

    @kotlinx.serialization.Serializable
    private data class PhotoArgs(val count: Int = 1, val screenshots: Boolean = false, val src: String? = null)

    private fun QuickJs.definePersonalDataBindings(env: ScriptEnvironment, activity: ActivityClock, runKey: String, nextImageIndex: () -> Int) {
        val json = UiJson
        fun storeImage(bytes: ByteArray) = json.encodeToString(CachedImage.serializer(), services.images.store(runKey, nextImageIndex(), bytes))
        fun calendars(list: List<CalendarInfo>) = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(CalendarInfo.serializer()), list)

        function("__calendar") { args ->
            val a = args.getOrNull(1) as? String ?: "{}"
            val cal = services.calendar
            when (args[0] as String) {
                "calendars" -> calendars(cal.calendars())
                "default" -> json.encodeToString(CalendarInfo.serializer(), cal.defaultCalendar())
                "events" -> {
                    val r = json.decodeFromString(RangeArgs.serializer(), a)
                    json.encodeToString(kotlinx.serialization.builtins.ListSerializer(EventData.serializer()), cal.events(r.start, r.end, r.ids))
                }
                "save" -> cal.save(json.decodeFromString(EventData.serializer(), a))
                "remove" -> cal.remove(requireNotNull(json.decodeFromString(IdArgs.serializer(), a).identifier)).let { null }
                "edit" -> {
                    if (env.runsInWidget) throw IllegalStateException("The calendar editor can't be opened during a widget refresh")
                    cal.openEditor(json.decodeFromString(IdArgs.serializer(), a).identifier).let { null }
                }
                else -> throw IllegalArgumentException("Unknown calendar operation: ${args[0]}")
            }
        }
        function("__reminders") { args ->
            val a = args.getOrNull(1) as? String ?: "{}"
            val store = services.reminders
            val id = { json.decodeFromString(IdArgs.serializer(), a) }
            when (args[0] as String) {
                "lists" -> calendars(store.lists())
                "default" -> json.encodeToString(CalendarInfo.serializer(), store.defaultList())
                "createList" -> json.encodeToString(CalendarInfo.serializer(), store.createList(requireNotNull(id().title)))
                "renameList" -> id().let { store.renameList(requireNotNull(it.identifier), requireNotNull(it.title)) }.let { null }
                "removeList" -> store.removeList(requireNotNull(id().identifier)).let { null }
                "all" -> json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ReminderData.serializer()), store.reminders(id().ids))
                "save" -> store.save(json.decodeFromString(ReminderData.serializer(), a))
                "remove" -> store.remove(requireNotNull(id().identifier)).let { null }
                else -> throw IllegalArgumentException("Unknown reminder operation: ${args[0]}")
            }
        }
        function("__contacts") { args ->
            val a = args.getOrNull(1) as? String ?: "{}"
            val store = services.contacts
            val ids = { json.decodeFromString(IdArgs.serializer(), a) }
            val contactList = kotlinx.serialization.builtins.ListSerializer(ContactData.serializer())
            when (args[0] as String) {
                "containers" -> json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ContactContainer.serializer()), store.containers())
                "defaultContainer" -> json.encodeToString(ContactContainer.serializer(), store.defaultContainer())
                "all" -> json.encodeToString(contactList, store.contacts(ids().ids))
                "inGroups" -> json.encodeToString(contactList, store.contactsInGroups(ids().ids.orEmpty()))
                "groups" -> json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ContactGroup.serializer()), store.groups(ids().ids))
                "image" -> store.image(requireNotNull(ids().identifier))?.let(::storeImage)
                "apply" -> store.apply(json.decodeFromString(ContactChanges.serializer(), a)).let { null }
                else -> throw IllegalArgumentException("Unknown contacts operation: ${args[0]}")
            }
        }
        function("__photos") { args ->
            val a = json.decodeFromString(PhotoArgs.serializer(), args.getOrNull(1) as? String ?: "{}")
            when (args[0] as String) {
                "latest" -> "[" + services.photos.latest(a.count, a.screenshots).joinToString(",") { storeImage(it) } + "]"
                "save" -> {
                    val bytes = services.images.bytes(requireNotNull(a.src)) ?: throw IllegalArgumentException("Image data is no longer available")
                    services.photos.save(bytes)
                    null
                }
                else -> throw IllegalArgumentException("Unknown Photos operation: ${args[0]}")
            }
        }
        asyncFunction("__photosPick") { args ->
            if (env.runsInWidget) throw IllegalStateException("Photos can't be picked during a widget refresh")
            val handle = activity.paused { services.ui.pickImage(args[0] as String) } ?: return@asyncFunction null
            val bytes = services.photos.read(handle) ?: throw IllegalStateException("The picked photo can't be read")
            storeImage(bytes)
        }
    }

    private fun QuickJs.defineSystemBindings(env: ScriptEnvironment, activity: ActivityClock) {
        fun requireApp(what: String) {
            if (env.runsInWidget) throw IllegalStateException("$what can't be used during a widget refresh")
        }
        function("__clipboard") { args ->
            when (args[0] as String) {
                "copy" -> services.clipboard.copy(args[1] as String).let { null }
                "paste" -> services.clipboard.paste()
                else -> throw IllegalArgumentException("Unknown Pasteboard operation: ${args[0]}")
            }
        }
        asyncFunction("__compose") { args ->
            val json = args[1] as String
            when (args[0] as String) {
                "share" -> { requireApp("ShareSheet"); services.composer.share(UiJson.decodeFromString(ShareSpec.serializer(), json)) }
                "mail" -> { requireApp("Mail"); services.composer.mail(UiJson.decodeFromString(MailSpec.serializer(), json)) }
                "message" -> { requireApp("Message"); services.composer.message(UiJson.decodeFromString(MessageSpec.serializer(), json)) }
                else -> throw IllegalArgumentException("Unknown composer: ${args[0]}")
            }
            null
        }
        asyncFunction("__callback") { args ->
            requireApp("CallbackURL")
            val result = activity.paused { services.callbacks.open(args[0] as String, args[1] as String) }
            UiJson.encodeToString(CallbackResult.serializer(), result)
        }
        asyncFunction("__speak") { args ->
            services.speaker.speak(args[0] as String)
            null
        }
    }

    private fun QuickJs.defineUtilityBindings(env: ScriptEnvironment) {
        val device by lazy { services.device() }
        // encodeDefaults: fields like systemName/isPad have defaults but scripts need them all.
        function("__device") { DeviceJson.encodeToString(DeviceInfo.serializer(), device) }
        function("__uuid") { java.util.UUID.randomUUID().toString().uppercase() }
        function("__openUrl") { args ->
            if (env.runsInWidget) throw IllegalStateException("Safari can't open URLs during a widget refresh; use widget.url instead")
            services.urls.open(args[0] as String)
            null
        }
        function("__xml") { args -> XmlEvents.parse(args[0] as String) }
    }

    @kotlinx.serialization.Serializable
    private data class SymbolImage(val src: String, val width: Int, val height: Int, val fallback: Boolean)

    @kotlinx.serialization.Serializable
    private data class ReverseGeocodeRequest(val lat: Double, val lon: Double, val locale: String? = null)

    private fun envScript(env: ScriptEnvironment, scriptName: String): String {
        val json = buildJsonObject {
            put("runsInWidget", env.runsInWidget)
            put("widgetFamily", env.widgetFamily.jsName)
            put("widgetParameter", env.widgetParameter)
            put("scriptName", scriptName)
            put("widgetAction", env.widgetAction)
            put("locale", services.locale.toLanguageTag())
            put("queryParameters", kotlinx.serialization.json.JsonObject(env.queryParameters.mapValues { kotlinx.serialization.json.JsonPrimitive(it.value) }))
            put("source", env.source.name)
            put("shortcutParameter", env.shortcutParameter)
            put("trigger", env.trigger)
            put("share", buildJsonObject {
                put("plainTexts", env.shareInput.plainTexts.toJsonArray())
                put("urls", env.shareInput.urls.toJsonArray())
                put("fileURLs", env.shareInput.fileURLs.toJsonArray())
                put("images", env.shareInput.images.toJsonArray())
            })
            env.notification?.let { n ->
                put("notification", buildJsonObject {
                    put("identifier", n.identifier)
                    put("title", n.title)
                    put("subtitle", n.subtitle)
                    put("body", n.body)
                    put("threadIdentifier", n.threadIdentifier)
                    put("userInfo", n.userInfo?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() } ?: kotlinx.serialization.json.JsonNull)
                    put("deliveredAt", n.deliveredAt)
                })
            }
        }
        return "globalThis.__env = $json;"
    }

    private fun List<String>.toJsonArray() = kotlinx.serialization.json.JsonArray(map { kotlinx.serialization.json.JsonPrimitive(it) })

    private val base64 = java.util.Base64.getEncoder()
    private val DeviceJson = Json { encodeDefaults = true }

    private fun decodeBase64(text: String): ByteArray = try {
        java.util.Base64.getDecoder().decode(text)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid base64 data")
    }

    private fun timeoutMessage() = "Script timed out after ${timeoutMillis}ms"

    private fun Long.toMillis() = this / 1_000_000

    /** Measures script activity; [paused] sections (waiting for the user) are excluded. */
    private class ActivityClock {
        private val start = System.nanoTime()
        private var pausedNanos = 0L
        private var openPauses = 0
        private var pauseStart = 0L

        /** Activity so far, excluding finished *and ongoing* pauses. */
        @Synchronized
        fun elapsed(): Long {
            val now = System.nanoTime()
            val ongoing = if (openPauses > 0) now - pauseStart else 0L
            return (now - start - pausedNanos - ongoing) / 1_000_000
        }

        suspend fun <T> paused(block: suspend () -> T): T {
            begin()
            try {
                return block()
            } finally {
                end()
            }
        }

        @Synchronized
        private fun begin() {
            if (openPauses++ == 0) pauseStart = System.nanoTime()
        }

        @Synchronized
        private fun end() {
            if (--openPauses == 0) pausedNanos += System.nanoTime() - pauseStart
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 30_000L
        const val DEFAULT_INTERACTIVE_TIMEOUT_MS = 30 * 60_000L
        private const val WATCHDOG_INTERVAL_MS = 250L
        const val DEFAULT_MEMORY_LIMIT_BYTES = 64L * 1024 * 1024
    }
}
