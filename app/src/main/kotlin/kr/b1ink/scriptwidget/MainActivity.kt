package kr.b1ink.scriptwidget

import android.content.Intent
import android.os.Bundle
import kotlinx.coroutines.launch
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kr.b1ink.scriptwidget.ui.main.FloatingTabBar
import kr.b1ink.scriptwidget.ui.main.FloatingTabBarSpace
import kr.b1ink.scriptwidget.ui.main.ListAction
import kr.b1ink.scriptwidget.ui.main.LocalBottomBarSpace
import kr.b1ink.scriptwidget.ui.main.MainNavState
import kr.b1ink.scriptwidget.ui.main.MainTab
import kr.b1ink.scriptwidget.ui.main.Overlay
import kr.b1ink.scriptwidget.ui.settings.SettingsActions
import kr.b1ink.scriptwidget.ui.settings.SettingsScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.b1ink.scriptwidget.automation.ScriptShortcuts
import kr.b1ink.scriptwidget.data.android.NotificationTap
import kr.b1ink.scriptwidget.deeplink.DeepLink
import kr.b1ink.scriptwidget.domain.model.RunSource
import kr.b1ink.scriptwidget.share.SharedRun
import kr.b1ink.scriptwidget.ui.editor.RunRequest
import kr.b1ink.scriptwidget.permissions.AndroidPermissionStatus
import kr.b1ink.scriptwidget.ui.permissions.PermissionActionHandler
import kr.b1ink.scriptwidget.ui.permissions.PermissionsRoute
import kr.b1ink.scriptwidget.ui.permissions.PermissionsViewModel
import kr.b1ink.scriptwidget.ui.editor.EditorRoute
import kr.b1ink.scriptwidget.ui.list.ScriptListRoute
import kr.b1ink.scriptwidget.ui.theme.ScriptWidgetTheme

class MainActivity : ComponentActivity() {

    /** The latest `scriptwidget://` link, consumed by the UI. */
    private var pendingLink by mutableStateOf<DeepLink?>(null)

    /** Script files shared with / opened in the app, imported by the script list. */
    private var pendingImport by mutableStateOf<List<String>>(emptyList())

    /** A script to run because a notification was tapped or content was shared to it. */
    private var pendingRun by mutableStateOf<Pair<String, RunRequest>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            pendingLink = intent.deepLink()
            pendingImport = incomingFiles(intent)
            pendingRun = intent.runRequest()
        }
        val container = container
        setContent {
            ScriptWidgetTheme {
                var nav by rememberSaveable(stateSaver = NavSaver) { mutableStateOf(MainNavState()) }
                val permissions = viewModel { PermissionsViewModel(AndroidPermissionStatus(applicationContext)) }
                val permissionState by permissions.uiState.collectAsStateWithLifecycle()
                var autoRun by remember { mutableStateOf<RunRequest?>(null) }

                LaunchedEffect(pendingRun) {
                    pendingRun?.let { (script, request) ->
                        nav = nav.open(script)
                        autoRun = request
                        pendingRun = null
                    }
                }

                LaunchedEffect(pendingImport) {
                    if (pendingImport.isNotEmpty()) nav = nav.showScripts()
                }

                LaunchedEffect(pendingLink) {
                    when (val link = pendingLink) {
                        is DeepLink.Run -> {
                            nav = nav.open(link.scriptName)
                            autoRun = RunRequest(queryParameters = link.queryParameters)
                        }
                        is DeepLink.Open -> nav = nav.open(link.scriptName)
                        DeepLink.Home -> nav = nav.goHome()
                        // Replies to CallbackURL.open(): hand them to the waiting script, stay on screen.
                        is DeepLink.Callback -> container.callbacks.complete(link)
                        null -> Unit
                    }
                    pendingLink = null
                }

                // Back closes what is on top, then returns to "my scripts", then leaves the app.
                val back = nav.back()
                BackHandler(enabled = back != null) { back?.let { nav = it } }

                val editing = nav.editing
                when {
                    nav.overlay == Overlay.Permissions ->
                        PermissionsRoute(vm = permissions, onBack = { nav = nav.back() ?: nav }, automation = container.automationSettings)
                    nav.overlay == Overlay.Bookmarks ->
                        kr.b1ink.scriptwidget.ui.bookmarks.FileBookmarksRoute(container = container, onBack = { nav = nav.back() ?: nav })
                    nav.overlay == Overlay.Triggers ->
                        kr.b1ink.scriptwidget.ui.triggers.TriggersRoute(container = container, onBack = { nav = nav.back() ?: nav })
                    nav.overlay == Overlay.ApiReference ->
                        kr.b1ink.scriptwidget.ui.api.ApiReferenceRoute(container = container, onBack = { nav = nav.back() ?: nav })
                    editing != null && nav.designing -> kr.b1ink.scriptwidget.ui.designer.DesignerRoute(
                        container = container,
                        scriptName = editing,
                        onBack = { nav = nav.closeEditor() },
                        onShowCode = { nav = nav.showCode() },
                    )
                    editing != null -> EditorRoute(
                        container = container,
                        scriptName = editing,
                        onBack = { nav = nav.closeEditor() },
                        onOpenDesigner = { nav = nav.showDesigner() },
                        autoRun = autoRun,
                        onOpenApiReference = { nav = nav.show(Overlay.ApiReference) },
                        onAutoRunConsumed = { autoRun = null },
                    )
                    else -> Box(Modifier.fillMaxSize()) {
                        CompositionLocalProvider(LocalBottomBarSpace provides FloatingTabBarSpace) {
                            TabContent(nav, permissions, permissionState.widgetRecommendations, onNav = { nav = it })
                        }
                        FloatingTabBar(
                            selected = nav.tab,
                            onSelect = { nav = nav.select(it) },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun TabContent(
        nav: MainNavState,
        permissions: PermissionsViewModel,
        widgetRecommendations: List<kr.b1ink.scriptwidget.permissions.AppPermission>,
        onNav: (MainNavState) -> Unit,
    ) {
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        // Scripts made in the designer (and not edited by hand since) open in the designer.
        val open: (String) -> Unit = { name ->
            scope.launch {
                val designed = container.openDesign(name) is kr.b1ink.scriptwidget.domain.model.DesignRead.Designed
                onNav(nav.open(name, designer = designed))
            }
        }
        val newDesign: (kr.b1ink.scriptwidget.domain.model.DesignTemplates) -> Unit = { template ->
            val texts = kr.b1ink.scriptwidget.ui.text.AppLanguage.templateTexts(this@MainActivity)
            scope.launch { onNav(nav.open(container.createDesign(template.design(texts)), designer = true)) }
        }
        when (nav.tab) {
            MainTab.Scripts -> {
                PermissionActionHandler(permissions)
                ScriptListRoute(
                    container = container,
                    onOpen = open,
                    onNewDesign = newDesign,
                    widgetRecommendations = widgetRecommendations,
                    onOpenPermissions = { onNav(nav.show(Overlay.Permissions)) },
                    incomingFiles = pendingImport,
                    onIncomingFilesConsumed = { pendingImport = emptyList() },
                    listAction = nav.listAction,
                    onListActionDone = { onNav(nav.actionDone()) },
                )
            }
            MainTab.Gallery -> kr.b1ink.scriptwidget.ui.gallery.GalleryRoute(container = container, onBack = null, onOpen = open)
            MainTab.History -> kr.b1ink.scriptwidget.ui.history.RunHistoryRoute(container = container)
            MainTab.Help -> kr.b1ink.scriptwidget.ui.api.ApiReferenceRoute(container = container)
            MainTab.Settings -> {
              var screenOff by remember { mutableStateOf(container.refreshSettings.refreshWhileScreenOff()) }
              val updates = androidx.lifecycle.viewmodel.compose.viewModel {
                  kr.b1ink.scriptwidget.ui.settings.UpdateViewModel(container.checkForUpdate(versionName())::invoke)
              }
              LaunchedEffect(Unit) { updates.checkOnce() }
              SettingsScreen(
                versionName = versionName(),
                refreshWhileScreenOff = screenOff,
                update = updates.state.collectAsState().value,
                language = kr.b1ink.scriptwidget.ui.text.AppLanguage.chosen(this@MainActivity),
                canChooseLanguage = kr.b1ink.scriptwidget.ui.text.AppLanguage.canChoose,
                actions = SettingsActions(
                    onPermissions = { onNav(nav.show(Overlay.Permissions)) },
                    onTriggers = { onNav(nav.show(Overlay.Triggers)) },
                    onLanguage = { kr.b1ink.scriptwidget.ui.text.AppLanguage.choose(this@MainActivity, it) },
                    onBookmarks = { onNav(nav.show(Overlay.Bookmarks)) },
                    onImport = { onNav(nav.request(ListAction.Import)) },
                    onBackup = { onNav(nav.request(ListAction.Backup)) },
                    onRestore = { onNav(nav.request(ListAction.Restore)) },
                    onCatalogs = { onNav(nav.select(MainTab.Gallery)) },
                    onOpenUrl = { url -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } },
                    onCheckUpdate = updates::check,
                    onRefreshWhileScreenOff = { enabled ->
                        container.refreshSettings.setRefreshWhileScreenOff(enabled)
                        screenOff = enabled
                    },
                ),
              )
            }
        }
    }

    private fun versionName(): String =
        runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingLink = intent.deepLink()
        pendingImport = incomingFiles(intent)
        pendingRun = intent.runRequest()
    }

    private fun Intent?.runRequest(): Pair<String, RunRequest>? {
        NotificationTap.parse(this)?.let { (script, notification) ->
            return script to RunRequest(source = RunSource.Notification, notification = notification)
        }
        SharedRun.parse(this)?.let { (script, input) ->
            return script to RunRequest(source = RunSource.ShareSheet, shareInput = input)
        }
        ScriptShortcuts.parse(this)?.let { return it }
        return null
    }

    private fun Intent?.deepLink(): DeepLink? = this?.dataString?.let(DeepLink::parse)
}

private val NavSaver = listSaver<MainNavState, String?>(save = { it.save() }, restore = { MainNavState.restore(it) })
