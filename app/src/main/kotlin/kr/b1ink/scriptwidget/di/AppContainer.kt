package kr.b1ink.scriptwidget.di

import kotlinx.coroutines.sync.withLock

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.work.WorkManager
import kr.b1ink.scriptwidget.data.android.AndroidCalendarStore
import kr.b1ink.scriptwidget.data.android.AndroidCanvasRenderer
import kr.b1ink.scriptwidget.data.android.AndroidClipboard
import kr.b1ink.scriptwidget.data.android.AndroidComposer
import kr.b1ink.scriptwidget.data.android.AndroidContactStore
import kr.b1ink.scriptwidget.data.android.AndroidSpeaker
import kr.b1ink.scriptwidget.data.android.AndroidDeviceInfo
import kr.b1ink.scriptwidget.data.android.AndroidDocumentStore
import kr.b1ink.scriptwidget.data.android.AndroidLocationProvider
import kr.b1ink.scriptwidget.data.android.AndroidNotifier
import kr.b1ink.scriptwidget.data.android.AndroidPhotoLibrary
import kr.b1ink.scriptwidget.data.android.AndroidScriptDocuments
import kr.b1ink.scriptwidget.data.android.AndroidUrlOpener
import kr.b1ink.scriptwidget.data.android.AndroidWebPage
import kr.b1ink.scriptwidget.data.android.BitmapImageStore
import kr.b1ink.scriptwidget.data.android.KeystoreSecretStore
import kr.b1ink.scriptwidget.data.android.MaterialSymbolRenderer
import kr.b1ink.scriptwidget.data.engine.Prelude
import kr.b1ink.scriptwidget.data.engine.QuickJsScriptEngine
import kr.b1ink.scriptwidget.data.engine.ScriptServices
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.data.personal.FileReminderStore
import kr.b1ink.scriptwidget.data.location.CachingLocationProvider
import kr.b1ink.scriptwidget.data.module.FileModuleSource
import kr.b1ink.scriptwidget.data.http.OkHttpGateway
import kr.b1ink.scriptwidget.data.repository.DataStoreWidgetBindingRepository
import kr.b1ink.scriptwidget.data.repository.FileScriptRepository
import kr.b1ink.scriptwidget.deeplink.AppCallbackBroker
import kr.b1ink.scriptwidget.domain.scheduler.WidgetRefreshScheduler
import kr.b1ink.scriptwidget.ui.scriptui.ScriptUiHost
import kr.b1ink.scriptwidget.domain.time.Clock
import kr.b1ink.scriptwidget.domain.usecase.BindWidgetUseCase
import kr.b1ink.scriptwidget.domain.usecase.CreateScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.DeleteScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ExportScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ImportScriptsUseCase
import kr.b1ink.scriptwidget.domain.usecase.GetScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.GetWidgetBindingUseCase
import kr.b1ink.scriptwidget.domain.usecase.ObserveScriptNamesUseCase
import kr.b1ink.scriptwidget.domain.usecase.RefreshWidgetUseCase
import kr.b1ink.scriptwidget.domain.usecase.RenameScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.RunScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.SaveScriptUseCase
import kr.b1ink.scriptwidget.domain.usecase.ScheduleBoundWidgetsUseCase
import kr.b1ink.scriptwidget.domain.usecase.UnbindWidgetsUseCase
import kr.b1ink.scriptwidget.widget.ScriptWidget
import kr.b1ink.scriptwidget.widget.WidgetDependencies
import kr.b1ink.scriptwidget.widget.state.GlanceRenderStateRepository
import kr.b1ink.scriptwidget.widget.work.AppWidgetManagerFamilyResolver
import kr.b1ink.scriptwidget.widget.work.ExactAlarmRefreshScheduler
import kr.b1ink.scriptwidget.widget.work.ScriptWorkerFactory
import kr.b1ink.scriptwidget.widget.work.WorkManagerRefreshScheduler
import java.io.File

/**
 * Composition root: the only place that knows concrete data implementations.
 *
 * @param rootDir where scripts, images and bindings are stored (overridable for tests).
 * @param schedulerOverride replaces WorkManager scheduling (tests).
 */
class AppContainer(
    context: Context,
    rootDir: File = context.applicationContext.filesDir,
    schedulerOverride: WidgetRefreshScheduler? = null,
    keychainName: String = "keychain",
) : WidgetDependencies {
    private val appContext = context.applicationContext

    /** In-app UI for scripts run from the editor (Alert, UITable, …). */
    val scriptUi = ScriptUiHost()

    // --- data ---
    private val scriptRepository = FileScriptRepository(File(rootDir, "scripts"))
    private val bindingRepository = DataStoreWidgetBindingRepository(
        PreferenceDataStoreFactory.create { File(rootDir, "datastore/widget_bindings.preferences_pb") }
    )
    private val renderStates = GlanceRenderStateRepository(appContext, ScriptWidget())
    private val scriptFiles = SandboxedFileSystem(File(rootDir, "script_storage"))

    /** Where content shared to a script is copied (inside the script sandbox). */
    val scriptTemporaryDirectory: String get() = scriptFiles.temporaryDirectory
    private val urlOpener = AndroidUrlOpener(appContext)

    /** Receives x-callback-url replies from MainActivity. */
    val callbacks = AppCallbackBroker(urlOpener)

    private val notifier by lazy { AndroidNotifier(appContext, { WorkManager.getInstance(appContext) }) }

    private val imageStore by lazy { BitmapImageStore(File(rootDir, "script_images")) }

    private val http by lazy { OkHttpGateway.cached(File(appContext.cacheDir, "http")) }

    private val engine by lazy {
        // Once per process: shared files, camera photos and picked documents older than a day go.
        Thread { runCatching { scriptFiles.purgeTemporary() } }.start()
        QuickJsScriptEngine(
            prelude = Prelude.load(),
            services = ScriptServices(
                http = http,
                images = imageStore,
                files = scriptFiles,
                secrets = KeystoreSecretStore(appContext, prefsName = keychainName),
                location = CachingLocationProvider(
                    AndroidLocationProvider(appContext),
                    File(rootDir, "location/last.json"),
                    hasPermission = { AndroidLocationProvider.hasLocationPermission(appContext) },
                ),
                modules = FileModuleSource(File(rootDir, "scripts")),
                // Resolved on demand: WorkManager's init asks the Application for this container's worker factory.
                notifier = notifier,
                symbols = MaterialSymbolRenderer(appContext),
                canvas = AndroidCanvasRenderer(),
                device = AndroidDeviceInfo(appContext)::snapshot,
                urls = urlOpener,
                ui = scriptUi,
                web = { AndroidWebPage(appContext) },
                documents = AndroidDocumentStore(appContext, scriptFiles),
                clipboard = AndroidClipboard(appContext),
                composer = AndroidComposer(appContext),
                callbacks = callbacks,
                speaker = AndroidSpeaker(appContext),
                calendar = AndroidCalendarStore(appContext),
                reminders = FileReminderStore(File(rootDir, "reminders/reminders.json"), notifier),
                contacts = AndroidContactStore(appContext),
                photos = AndroidPhotoLibrary(appContext),
            ),
        )
    }

    override val scheduler: WidgetRefreshScheduler by lazy {
        schedulerOverride ?: ExactAlarmRefreshScheduler(
            context = appContext,
            delegate = WorkManagerRefreshScheduler(WorkManager.getInstance(appContext)),
            alarms = appContext.getSystemService(AlarmManager::class.java),
        )
    }

    // --- use cases ---
    val observeScriptNames = ObserveScriptNamesUseCase(scriptRepository)
    // --- gallery ---
    /** Gallery scripts that come in several languages (designs) install in the app's language. */
    private val galleryLanguage: () -> String = { kr.b1ink.scriptwidget.ui.text.AppLanguage.current(appContext) }
    private val bundledCatalog by lazy { kr.b1ink.scriptwidget.data.gallery.BuiltinCatalogSource(galleryLanguage) }

    /** The gallery from the GitHub repository (new and fixed samples), bundled copy when offline. */
    val builtinCatalog: kr.b1ink.scriptwidget.domain.repository.CatalogSource by lazy {
        kr.b1ink.scriptwidget.data.gallery.OfficialCatalogSource(
            online = kr.b1ink.scriptwidget.data.gallery.RemoteCatalogSource(
                kr.b1ink.scriptwidget.data.gallery.OfficialCatalogSource.URL, http, timeoutSeconds = 8.0, language = galleryLanguage,
            ),
            bundled = bundledCatalog,
        )
    }
    val installedScripts by lazy { kr.b1ink.scriptwidget.data.gallery.FileInstalledScriptRepository(File(rootDir, "gallery/installed.json")) }
    val catalogUrls by lazy { kr.b1ink.scriptwidget.data.gallery.FileCatalogUrlRepository(File(rootDir, "gallery/urls.json")) }
    val scriptRepositoryForGallery: kr.b1ink.scriptwidget.domain.repository.ScriptRepository get() = scriptRepository
    fun remoteCatalog(url: String): kr.b1ink.scriptwidget.domain.repository.CatalogSource =
        kr.b1ink.scriptwidget.data.gallery.RemoteCatalogSource(url, http, language = galleryLanguage)

    /** First launch: every gallery sample, remembered as installed so it can be updated later. */
    val seedSamples: suspend () -> Unit = {
        kr.b1ink.scriptwidget.domain.usecase.SeedFromCatalogUseCase(
            scriptRepository, bundledCatalog,
            kr.b1ink.scriptwidget.domain.usecase.InstallCatalogEntryUseCase(scriptRepository, installedScripts),
        )()
    }
    val createScript = CreateScriptUseCase(scriptRepository)
    val getScript = GetScriptUseCase(scriptRepository)
    val saveScript by lazy { SaveScriptUseCase(scriptRepository, bindingRepository, scheduler) }
    val renameScript by lazy { RenameScriptUseCase(scriptRepository, bindingRepository, scheduler) }
    val deleteScript by lazy { DeleteScriptUseCase(scriptRepository, bindingRepository, scheduler) }
    val scriptDocuments by lazy { AndroidScriptDocuments(appContext) }

    /** Files picked for `FileManager.bookmarkedPath()`, kept inside the script sandbox. */
    val fileBookmarks by lazy {
        kr.b1ink.scriptwidget.data.files.FileBookmarkStore(File(rootDir, "script_storage/${SandboxedFileSystem.BOOKMARKS}"))
    }
    /** Font files scripts and designs use by name: Documents/fonts. */
    val fonts by lazy { kr.b1ink.scriptwidget.data.files.FontStore(File(rootDir, "script_storage/Documents/fonts")) }
    val importScripts by lazy { ImportScriptsUseCase(scriptRepository, scriptDocuments) }
    val scriptSummaries by lazy { kr.b1ink.scriptwidget.domain.usecase.ObserveScriptSummariesUseCase(scriptRepository) }
    val setScriptAppearance by lazy { kr.b1ink.scriptwidget.domain.usecase.SetScriptAppearanceUseCase(scriptRepository) }
    private val archiveCodec by lazy { kr.b1ink.scriptwidget.data.backup.ZipScriptArchiveCodec() }
    val backupScripts by lazy { kr.b1ink.scriptwidget.domain.usecase.BackupScriptsUseCase(scriptRepository, archiveCodec, scriptDocuments) }
    val restoreScripts by lazy { kr.b1ink.scriptwidget.domain.usecase.RestoreScriptsUseCase(scriptRepository, archiveCodec, scriptDocuments) }
    /** A script and its catalog entry, zipped for publishing in a gallery. */
    val exportForGallery by lazy { kr.b1ink.scriptwidget.domain.usecase.ExportForGalleryUseCase(scriptRepository, archiveCodec, scriptDocuments) }
    val exportScript by lazy { ExportScriptUseCase(scriptRepository, scriptDocuments) }
    /** Every run (app, widget, automation, …) for the run history screen. */
    val runHistory by lazy {
        kr.b1ink.scriptwidget.data.history.FileRunHistoryRepository(
            File(rootDir, "history/runs.jsonl"),
            legacyFile = File(rootDir, "history/runs.json"),
        )
    }
    val runScript by lazy { RunScriptUseCase(engine, runHistory) }

    // --- widget designer ---
    /** Generated scripts use the words of the app's language at the time they are saved. */
    val designGenerator = kr.b1ink.scriptwidget.domain.repository.DesignScriptGenerator { design ->
        kr.b1ink.scriptwidget.data.design.JsDesignScriptGenerator(kr.b1ink.scriptwidget.ui.text.AppLanguage.generatorTexts(appContext)).generate(design)
    }
    val createDesign = kr.b1ink.scriptwidget.domain.usecase.CreateDesignedScriptUseCase(scriptRepository, designGenerator)
    val openDesign = kr.b1ink.scriptwidget.domain.usecase.OpenDesignUseCase(scriptRepository)
    val saveDesign by lazy { kr.b1ink.scriptwidget.domain.usecase.SaveDesignUseCase(saveScript, scriptRepository, designGenerator) }

    /** Designer previews run on every edit; they are not worth a run-history entry. */
    val runPreview by lazy { RunScriptUseCase(engine) }

    /** Widget count and last run per script, for the script list. */
    /** Compares [currentVersion] with the latest GitHub release, picking the APK for this device's ABI. */
    fun checkForUpdate(currentVersion: String) = kr.b1ink.scriptwidget.domain.usecase.CheckForUpdateUseCase(
        kr.b1ink.scriptwidget.data.update.GitHubReleaseSource(http, android.os.Build.SUPPORTED_ABIS.toList()),
        currentVersion,
    )

    fun scriptActivity() = kr.b1ink.scriptwidget.domain.usecase.ObserveScriptActivityUseCase(scriptRepository, bindingRepository, runHistory)()

    private val apiCatalogLock = kotlinx.coroutines.sync.Mutex()
    private var apiCatalogCache: List<kr.b1ink.scriptwidget.data.api.ApiEntry>? = null

    /** The script API read from the prelude (for completions and the API reference); built once. */
    suspend fun apiCatalog(): List<kr.b1ink.scriptwidget.data.api.ApiEntry> = apiCatalogLock.withLock {
        val entries = apiCatalogCache ?: runCatching { kr.b1ink.scriptwidget.data.api.ApiCatalog.load(engine) }.getOrDefault(emptyList())
            .also { if (it.isNotEmpty()) apiCatalogCache = it }
        // Descriptions follow the app's current language, which can change while the cache lives.
        kr.b1ink.scriptwidget.data.api.ApiCatalog.inLanguage(entries, kr.b1ink.scriptwidget.ui.text.AppLanguage.current(appContext))
    }
    val runScriptByName by lazy { kr.b1ink.scriptwidget.domain.usecase.RunScriptByNameUseCase(scriptRepository, runScript) }
    /** Whether widgets keep refreshing while the screen is off (settings tab). */
    val refreshSettings by lazy {
        kr.b1ink.scriptwidget.settings.BackgroundRefreshSettings(appContext.getSharedPreferences("refresh_settings", android.content.Context.MODE_PRIVATE))
    }

    val automationSettings by lazy {
        kr.b1ink.scriptwidget.automation.AutomationSettings(appContext.getSharedPreferences("automation", Context.MODE_PRIVATE))
    }
    val refreshWidget by lazy {
        RefreshWidgetUseCase(
            bindingRepository, scriptRepository, runScript, renderStates, scheduler, Clock.System,
            kr.b1ink.scriptwidget.widget.work.PowerManagerScreenState(appContext),
            kr.b1ink.scriptwidget.widget.work.ConnectivityNetworkState(appContext),
            refreshSettings,
        )
    }
    val getWidgetBinding = GetWidgetBindingUseCase(bindingRepository)
    val bindWidget by lazy { BindWidgetUseCase(bindingRepository, scheduler) }
    override val unbindWidgets by lazy {
        // A deleted widget's downloaded images (cached per widget) go with it.
        UnbindWidgetsUseCase(bindingRepository, scheduler) { id -> imageStore.clear("widget-$id") }
    }
    override val scheduleBoundWidgets by lazy { ScheduleBoundWidgetsUseCase(bindingRepository, scheduler) }
    override val resizeWidget by lazy { kr.b1ink.scriptwidget.domain.usecase.ResizeWidgetUseCase(renderStates, scheduler) }

    // --- event triggers ---
    val eventTriggers: kr.b1ink.scriptwidget.domain.repository.EventTriggerRepository by lazy {
        kr.b1ink.scriptwidget.data.trigger.FileEventTriggerRepository(File(rootDir, "triggers/triggers.json"))
    }
    val eventMonitor: kr.b1ink.scriptwidget.domain.scheduler.EventMonitor by lazy {
        kr.b1ink.scriptwidget.widget.work.AndroidEventMonitor(appContext)
    }
    override val handleEvent by lazy {
        kr.b1ink.scriptwidget.domain.usecase.HandleEventUseCase(eventTriggers, bindingRepository, scheduler, runScriptByName)
    }

    /** Re-hooks the watched events (after an update or reboot; cheap when nothing changed). */
    suspend fun syncEventMonitor() = kr.b1ink.scriptwidget.domain.usecase.SyncEventMonitorUseCase(eventTriggers, eventMonitor)()

    /** Built during WorkManager's initialization: must not resolve anything that needs WorkManager. */
    val workerFactory = ScriptWorkerFactory(
        refreshWidget = { refreshWidget },
        families = { AppWidgetManagerFamilyResolver(AppWidgetManager.getInstance(appContext)) },
        handleEvent = { handleEvent },
    )
}
