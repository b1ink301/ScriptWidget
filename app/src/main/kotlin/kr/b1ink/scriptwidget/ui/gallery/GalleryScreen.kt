package kr.b1ink.scriptwidget.ui.gallery

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.model.EntryStatus
import kr.b1ink.scriptwidget.domain.model.GalleryItem
import kr.b1ink.scriptwidget.domain.model.GallerySection
import kr.b1ink.scriptwidget.ui.text.AppLanguage
import kr.b1ink.scriptwidget.ui.text.asString

class GalleryActions(
    val onInstall: (sourceId: String, entryId: String) -> Unit,
    val onUpdate: (sourceId: String, entryId: String) -> Unit,
    val onOpen: (scriptName: String) -> Unit,
    val onConfirm: () -> Unit,
    val onCancel: () -> Unit,
    val onCategory: (String?) -> Unit,
    val onAddUrl: (String) -> Unit,
    val onRemoveUrl: (String) -> Unit,
    val onMessageShown: () -> Unit = {},
)

@Composable
fun GalleryRoute(container: AppContainer, onBack: (() -> Unit)?, onOpen: (String) -> Unit) {
    val language = AppLanguage.current(LocalContext.current)
    val vm = viewModel {
        GalleryViewModel(
            container.builtinCatalog, container::remoteCatalog, container.catalogUrls, container.installedScripts,
            container.scriptRepositoryForGallery, language,
        )
    }
    val state by vm.uiState.collectAsStateWithLifecycle()
    GalleryScreen(
        state,
        GalleryActions(
            onInstall = vm::install, onUpdate = vm::update, onOpen = onOpen, onConfirm = vm::confirmInstall,
            onCancel = vm::cancelPreview, onCategory = vm::selectCategory, onAddUrl = vm::addUrl, onRemoveUrl = vm::removeUrl,
            onMessageShown = vm::messageShown,
        ),
        onBack = onBack,
    )
}

private val PERMISSION_LABELS = mapOf(
    "calendar" to R.string.gallery_permission_calendar, "contacts" to R.string.gallery_permission_contacts,
    "location" to R.string.gallery_permission_location, "network" to R.string.gallery_permission_network,
    "notification" to R.string.gallery_permission_notification, "photos" to R.string.gallery_permission_photos,
)

@Composable
private fun permissionLabels(permissions: Collection<String>, separator: String): String {
    val labels = permissions.map { p -> PERMISSION_LABELS[p]?.let { stringResource(it) } ?: p }
    return labels.joinToString(separator)
}

/** A category as shown: a blank one (or the catalog default) is "Other" in the app language. */
@Composable
private fun categoryLabel(category: String): String =
    if (category.isBlank() || category == DEFAULT_CATEGORY) stringResource(R.string.gallery_category_other) else category

/** The category catalogs give entries that don't name one. */
private val DEFAULT_CATEGORY = kr.b1ink.scriptwidget.domain.model.CatalogEntry(id = "", name = "", script = "").category

private val SIZE_LABELS = mapOf("small" to "S", "medium" to "M", "large" to "L")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(state: GalleryUiState, actions: GalleryActions, onBack: (() -> Unit)? = null) {
    var addingUrl by rememberSaveable { mutableStateOf(false) }
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.asString()
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); actions.onMessageShown() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { onBack?.let { TextButton(onClick = it) { Text("←") } } },
                title = { Text(stringResource(R.string.gallery_title)) },
                actions = { TextButton(onClick = { addingUrl = true }) { Text(stringResource(R.string.gallery_add_catalog_button)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = kr.b1ink.scriptwidget.ui.main.LocalBottomBarSpace.current)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = state.category == null, onClick = { actions.onCategory(null) }, label = { Text(stringResource(R.string.gallery_category_all)) })
                    state.categories.forEach { c ->
                        FilterChip(selected = state.category == c, onClick = { actions.onCategory(c) }, label = { Text(categoryLabel(c)) })
                    }
                }
            }
            state.visibleSections.forEach { section ->
                item(key = "header-${section.sourceId}") { SectionHeader(section, state.language, onRemove = { removing = section.sourceId }) }
                items(section.items, key = { "${section.sourceId}/${it.entry.id}" }) { item ->
                    EntryCard(item, state.language, busy = state.busy, actions = actions, sourceId = section.sourceId)
                }
            }
        }
    }

    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = actions.onCancel,
            title = { Text(stringResource(R.string.gallery_install_title, preview.entry.textFor(state.language).title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.gallery_install_warning), style = MaterialTheme.typography.bodySmall)
                    if (preview.undeclaredPermissions.isNotEmpty()) {
                        Text(
                            stringResource(R.string.gallery_undeclared_permissions, permissionLabels(preview.undeclaredPermissions, ", ")),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (preview.entry.permissions.isNotEmpty()) {
                        Text(stringResource(R.string.gallery_required_permissions, permissionLabels(preview.entry.permissions, ", ")), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        preview.code,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    )
                }
            },
            confirmButton = { TextButton(onClick = actions.onConfirm, modifier = Modifier.testTag("install-confirm")) { Text(stringResource(R.string.gallery_install)) } },
            dismissButton = { TextButton(onClick = actions.onCancel) { Text(stringResource(R.string.gallery_cancel)) } },
        )
    }

    if (addingUrl) {
        // Cursor after the prefilled scheme, so typing continues the address.
        var url by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("https://", androidx.compose.ui.text.TextRange(8))) }
        AlertDialog(
            onDismissRequest = { addingUrl = false },
            title = { Text(stringResource(R.string.gallery_add_catalog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.gallery_add_catalog_hint), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = url, onValueChange = { url = it }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.testTag("catalog-url"),
                    )
                }
            },
            confirmButton = { TextButton(onClick = { addingUrl = false; actions.onAddUrl(url.text) }) { Text(stringResource(R.string.gallery_add)) } },
            dismissButton = { TextButton(onClick = { addingUrl = false }) { Text(stringResource(R.string.gallery_cancel)) } },
        )
    }

    removing?.let { url ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.gallery_remove_catalog_title)) },
            text = { Text(stringResource(R.string.gallery_remove_catalog_message, url)) },
            confirmButton = { TextButton(onClick = { removing = null; actions.onRemoveUrl(url) }) { Text(stringResource(R.string.gallery_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.gallery_cancel)) } },
        )
    }
}

@Composable
private fun SectionHeader(section: GallerySection, language: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(section.titles[language] ?: section.title, style = MaterialTheme.typography.titleMedium)
            section.error?.let { Text(stringResource(R.string.gallery_load_failed, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
        if (section.removable) TextButton(onClick = onRemove) { Text(stringResource(R.string.gallery_remove)) }
    }
}

@Composable
private fun EntryCard(item: GalleryItem, language: String, busy: Boolean, actions: GalleryActions, sourceId: String) {
    val entry = item.entry
    val text = entry.textFor(language)
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text.title, style = MaterialTheme.typography.titleSmall)
                Text(text.description, style = MaterialTheme.typography.bodySmall)
                val sizes = if (entry.sizes.isNotEmpty()) stringResource(R.string.gallery_sizes, entry.sizes.joinToString("·") { SIZE_LABELS[it] ?: it }) else null
                val permissions = if (entry.permissions.isNotEmpty()) permissionLabels(entry.permissions, " · ") else null
                val meta = listOfNotNull(sizes, permissions, entry.author)
                if (meta.isNotEmpty()) {
                    Text(meta.joinToString("  ·  "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val tag = Modifier.testTag("gallery-action-${entry.id}")
            when (val status = item.status) {
                EntryStatus.NotInstalled -> Button(enabled = !busy, onClick = { actions.onInstall(sourceId, entry.id) }, modifier = tag) { Text(stringResource(R.string.gallery_install)) }
                is EntryStatus.Installed -> OutlinedButton(onClick = { actions.onOpen(status.scriptName) }, modifier = tag) { Text(stringResource(R.string.gallery_open)) }
                is EntryStatus.UpdateAvailable -> FilledTonalButton(enabled = !busy, onClick = { actions.onUpdate(sourceId, entry.id) }, modifier = tag) { Text(stringResource(R.string.gallery_update)) }
            }
        }
    }
}
