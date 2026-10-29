package kr.b1ink.scriptwidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
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
                var editing by rememberSaveable { mutableStateOf<String?>(null) }
                var showPermissions by rememberSaveable { mutableStateOf(false) }
                var showHistory by rememberSaveable { mutableStateOf(false) }
                var showApi by rememberSaveable { mutableStateOf(false) }
                var showBookmarks by rememberSaveable { mutableStateOf(false) }
                var showGallery by rememberSaveable { mutableStateOf(false) }
                val permissions = viewModel { PermissionsViewModel(AndroidPermissionStatus(applicationContext)) }
                val permissionState by permissions.uiState.collectAsStateWithLifecycle()
                var autoRun by remember { mutableStateOf<RunRequest?>(null) }

                LaunchedEffect(pendingRun) {
                    pendingRun?.let { (script, request) ->
                        showPermissions = false
                        editing = script
                        autoRun = request
                        pendingRun = null
                    }
                }

                LaunchedEffect(pendingImport) {
                    if (pendingImport.isNotEmpty()) {
                        editing = null
                        showPermissions = false
                    }
                }

                LaunchedEffect(pendingLink) {
                    when (val link = pendingLink) {
                        is DeepLink.Run -> {
                            editing = link.scriptName
                            autoRun = RunRequest(queryParameters = link.queryParameters)
                        }
                        is DeepLink.Open -> editing = link.scriptName
                        DeepLink.Home -> editing = null
                        // Replies to CallbackURL.open(): hand them to the waiting script, stay on screen.
                        is DeepLink.Callback -> container.callbacks.complete(link)
                        null -> Unit
                    }
                    pendingLink = null
                }

                val current = editing
                if (showApi) {
                    BackHandler { showApi = false }
                    kr.b1ink.scriptwidget.ui.api.ApiReferenceRoute(container = container, onBack = { showApi = false })
                } else if (showGallery && current == null) {
                    BackHandler { showGallery = false }
                    kr.b1ink.scriptwidget.ui.gallery.GalleryRoute(
                        container = container,
                        onBack = { showGallery = false },
                        // Opening an installed script keeps the gallery underneath: back returns to it.
                        onOpen = { editing = it },
                    )
                } else if (showBookmarks) {
                    BackHandler { showBookmarks = false }
                    kr.b1ink.scriptwidget.ui.bookmarks.FileBookmarksRoute(container = container, onBack = { showBookmarks = false })
                } else if (showHistory) {
                    BackHandler { showHistory = false }
                    kr.b1ink.scriptwidget.ui.history.RunHistoryRoute(container = container, onBack = { showHistory = false })
                } else if (showPermissions) {
                    BackHandler { showPermissions = false }
                    PermissionsRoute(vm = permissions, onBack = { showPermissions = false }, automation = container.automationSettings)
                } else if (current == null) {
                    PermissionActionHandler(permissions)
                    ScriptListRoute(
                        container = container,
                        onOpen = { editing = it },
                        widgetRecommendations = permissionState.widgetRecommendations,
                        onOpenPermissions = { showPermissions = true },
                        onOpenHistory = { showHistory = true },
                        onOpenApiReference = { showApi = true },
                        onOpenBookmarks = { showBookmarks = true },
                        onOpenGallery = { showGallery = true },
                        incomingFiles = pendingImport,
                        onIncomingFilesConsumed = { pendingImport = emptyList() },
                    )
                } else {
                    BackHandler { editing = null }
                    EditorRoute(
                        container = container,
                        scriptName = current,
                        onBack = { editing = null },
                        autoRun = autoRun,
                        onOpenApiReference = { showApi = true },
                        onAutoRunConsumed = { autoRun = null },
                    )
                }
            }
        }
    }

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
