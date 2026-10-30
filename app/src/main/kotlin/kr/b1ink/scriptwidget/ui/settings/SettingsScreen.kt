package kr.b1ink.scriptwidget.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kr.b1ink.scriptwidget.R
import kr.b1ink.scriptwidget.domain.model.UpdateStatus
import kr.b1ink.scriptwidget.ui.main.LocalBottomBarSpace

class SettingsActions(
    val onPermissions: () -> Unit,
    val onBookmarks: () -> Unit,
    val onImport: () -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onCatalogs: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onRefreshWhileScreenOff: (Boolean) -> Unit = {},
    val onCheckUpdate: () -> Unit = {},
    val onTriggers: () -> Unit = {},
    /** "ko", "en", or null to follow the system. */
    val onLanguage: (String?) -> Unit = {},
)

const val REPOSITORY_URL = "https://github.com/b1ink301/ScriptWidget"

/** The README's license section (its Korean heading, percent-encoded). */
private const val LICENSE_URL = "$REPOSITORY_URL#%EB%9D%BC%EC%9D%B4%EC%84%A0%EC%8A%A4"

/** Everything used now and then: permissions, files, backups, catalogs and about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    versionName: String,
    actions: SettingsActions,
    refreshWhileScreenOff: Boolean = false,
    update: UpdateUiState = UpdateUiState.Idle,
    /** The chosen app language ("ko" / "en"), or null when it follows the system. */
    language: String? = null,
    canChooseLanguage: Boolean = true,
) {
    var choosingLanguage by remember { mutableStateOf(false) }
    if (choosingLanguage) {
        LanguageDialog(
            current = language,
            onChoose = {
                choosingLanguage = false
                actions.onLanguage(it)
            },
            onDismiss = { choosingLanguage = false },
        )
    }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = LocalBottomBarSpace.current),
        ) {
            group(R.string.settings_group_permissions_widgets) {
                Row("🔐", stringResource(R.string.settings_permissions_title), stringResource(R.string.settings_permissions_subtitle), actions.onPermissions)
                Row("⚡", stringResource(R.string.settings_triggers_title), stringResource(R.string.settings_triggers_subtitle), actions.onTriggers)
                ToggleRow(
                    "🌙", stringResource(R.string.settings_screen_off_title),
                    if (refreshWhileScreenOff) stringResource(R.string.settings_screen_off_on)
                    else stringResource(R.string.settings_screen_off_off),
                    checked = refreshWhileScreenOff,
                    onChange = actions.onRefreshWhileScreenOff,
                    tag = "screen-off-refresh",
                )
            }
            group(R.string.settings_group_files) {
                Row("🔖", stringResource(R.string.settings_bookmarks_title), stringResource(R.string.settings_bookmarks_subtitle), actions.onBookmarks)
                Row("📥", stringResource(R.string.settings_import_title), stringResource(R.string.settings_import_subtitle), actions.onImport)
                Row("🗂️", stringResource(R.string.settings_backup_title), stringResource(R.string.settings_backup_subtitle), actions.onBackup)
                Row("♻️", stringResource(R.string.settings_restore_title), stringResource(R.string.settings_restore_subtitle), actions.onRestore)
            }
            group(R.string.settings_group_gallery) {
                Row("🌐", stringResource(R.string.settings_catalogs_title), stringResource(R.string.settings_catalogs_subtitle), actions.onCatalogs)
            }
            group(R.string.settings_group_language) {
                Box(Modifier.testTag("language-row")) {
                    Row(
                        "🌐",
                        stringResource(R.string.settings_language_title),
                        if (!canChooseLanguage) stringResource(R.string.settings_language_follows_system) else languageLabel(language),
                    ) { if (canChooseLanguage) choosingLanguage = true }
                }
            }
            group(R.string.settings_group_about) {
                UpdateRow(update, actions)
                Row("ℹ️", stringResource(R.string.settings_app_version, versionName), stringResource(R.string.settings_view_source)) { actions.onOpenUrl(REPOSITORY_URL) }
                Row("📄", stringResource(R.string.settings_license_title), stringResource(R.string.settings_license_subtitle)) { actions.onOpenUrl(LICENSE_URL) }
            }
        }
    }
}

@Composable
private fun GroupScope.UpdateRow(update: UpdateUiState, actions: SettingsActions) {
    val status = (update as? UpdateUiState.Done)?.status
    val (title, subtitle) = when {
        update == UpdateUiState.Checking ->
            stringResource(R.string.settings_update_checking) to stringResource(R.string.settings_update_checking_subtitle)
        status is UpdateStatus.Available ->
            stringResource(R.string.settings_update_available, status.release.version) to stringResource(
                if (status.release.apkUrl != null) R.string.settings_update_available_apk else R.string.settings_update_available_page,
                status.current,
            )
        status is UpdateStatus.UpToDate ->
            stringResource(R.string.settings_update_up_to_date) to stringResource(R.string.settings_update_up_to_date_subtitle, status.current)
        status is UpdateStatus.Failed ->
            stringResource(R.string.settings_update_failed) to stringResource(R.string.settings_update_failed_subtitle, status.message)
        else -> stringResource(R.string.settings_update_check) to stringResource(R.string.settings_update_check_subtitle)
    }
    val emoji = if (status is UpdateStatus.Available) "⬆️" else "🔄"
    Box(Modifier.testTag("update-row")) {
        Row(emoji, title, subtitle) {
            if (status is UpdateStatus.Available) actions.onOpenUrl(status.release.apkUrl ?: status.release.pageUrl)
            else actions.onCheckUpdate()
        }
    }
}

@Composable
private fun languageLabel(language: String?) = when (language) {
    "ko" -> stringResource(R.string.settings_language_korean)
    "en" -> stringResource(R.string.settings_language_english)
    else -> stringResource(R.string.settings_language_system)
}

@Composable
private fun LanguageDialog(current: String?, onChoose: (String?) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_language_title)) },
        text = {
            Column {
                listOf(null to "system", "ko" to "ko", "en" to "en").forEach { (value, tag) ->
                    ListItem(
                        headlineContent = { Text(languageLabel(value)) },
                        leadingContent = { androidx.compose.material3.RadioButton(selected = value == current, onClick = { onChoose(value) }) },
                        modifier = Modifier.testTag("language-$tag").clickable { onChoose(value) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) } },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(@androidx.annotation.StringRes title: Int, rows: @Composable GroupScope.() -> Unit) {
    item {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
        )
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))) { GroupScope.rows() }
    }
}

object GroupScope {
    @Composable
    fun ToggleRow(emoji: String, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String) {
        ListItem(
            modifier = Modifier.padding(bottom = 2.dp).testTag(tag).clickable { onChange(!checked) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { Text(emoji, style = MaterialTheme.typography.titleMedium) },
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
            trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        )
    }

    @Composable
    fun Row(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
        ListItem(
            modifier = Modifier.padding(bottom = 2.dp).clickable(onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            leadingContent = { Text(emoji, style = MaterialTheme.typography.titleMedium) },
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
            trailingContent = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }
}
