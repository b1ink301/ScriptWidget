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
import androidx.compose.ui.unit.dp
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
    Scaffold(topBar = { TopAppBar(title = { Text("설정") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = LocalBottomBarSpace.current),
        ) {
            group("권한·위젯") {
                Row("🔐", "권한", "알림·위치·캘린더·배터리 최적화, 자동화 앱에서 실행", actions.onPermissions)
                Row("⚡", "자동 실행 트리거", "캘린더 변경·자정·충전·Wi-Fi 등에서 위젯 갱신 또는 스크립트 실행", actions.onTriggers)
                ToggleRow(
                    "🌙", "화면이 꺼져 있어도 갱신",
                    if (refreshWhileScreenOff) "15분마다 폰을 깨워 갱신합니다 (배터리 사용 증가)"
                    else "꺼짐: 화면이 켜질 때 바로 갱신합니다 (배터리 절약)",
                    checked = refreshWhileScreenOff,
                    onChange = actions.onRefreshWhileScreenOff,
                    tag = "screen-off-refresh",
                )
            }
            group("파일") {
                Row("🔖", "파일 북마크", "스크립트가 bookmarkedPath()로 읽는 파일", actions.onBookmarks)
                Row("📥", "스크립트 가져오기", ".js · .scriptable 파일", actions.onImport)
                Row("🗂️", "전체 백업", "모든 스크립트를 zip 하나로 저장", actions.onBackup)
                Row("♻️", "백업에서 복원", "덮어쓰지 않고 없는 스크립트만 추가", actions.onRestore)
            }
            group("갤러리") {
                Row("🌐", "카탈로그 관리", "기본 갤러리와 추가한 카탈로그(index.json)", actions.onCatalogs)
            }
            group("언어 · Language") {
                Box(Modifier.testTag("language-row")) {
                    Row(
                        "🌐",
                        "앱 언어 · App language",
                        if (!canChooseLanguage) "기기 언어를 따릅니다 (Android 13 이상에서 선택 가능)" else languageLabel(language),
                    ) { if (canChooseLanguage) choosingLanguage = true }
                }
            }
            group("정보") {
                UpdateRow(update, actions)
                Row("ℹ️", "ScriptWidget $versionName", "GitHub에서 소스 보기") { actions.onOpenUrl(REPOSITORY_URL) }
                Row("📄", "라이선스", "GPL-3.0 · 오픈소스 라이선스") { actions.onOpenUrl("$REPOSITORY_URL#라이선스") }
            }
        }
    }
}

@Composable
private fun GroupScope.UpdateRow(update: UpdateUiState, actions: SettingsActions) {
    val status = (update as? UpdateUiState.Done)?.status
    val (title, subtitle) = when {
        update == UpdateUiState.Checking -> "업데이트 확인 중…" to "GitHub 릴리스를 확인하고 있습니다"
        status is UpdateStatus.Available ->
            "새 버전 ${status.release.version}" to "현재 ${status.current} · 탭하면 ${if (status.release.apkUrl != null) "APK를 받습니다" else "릴리스 페이지를 엽니다"}"
        status is UpdateStatus.UpToDate -> "최신 버전입니다" to "현재 ${status.current} · 탭하면 다시 확인"
        status is UpdateStatus.Failed -> "업데이트 확인 실패" to "${status.message} · 탭하면 다시 시도"
        else -> "업데이트 확인" to "GitHub 릴리스에서 새 버전 확인"
    }
    val emoji = if (status is UpdateStatus.Available) "⬆️" else "🔄"
    Box(Modifier.testTag("update-row")) {
        Row(emoji, title, subtitle) {
            if (status is UpdateStatus.Available) actions.onOpenUrl(status.release.apkUrl ?: status.release.pageUrl)
            else actions.onCheckUpdate()
        }
    }
}

private fun languageLabel(language: String?) = when (language) {
    "ko" -> "한국어"
    "en" -> "English"
    else -> "기기 언어 · System"
}

@Composable
private fun LanguageDialog(current: String?, onChoose: (String?) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("앱 언어 · App language") },
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
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("취소 · Cancel") } },
    )
}

private fun androidx.compose.foundation.lazy.LazyListScope.group(title: String, rows: @Composable GroupScope.() -> Unit) {
    item {
        Text(
            title,
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
