package kr.b1ink.scriptwidget.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kr.b1ink.scriptwidget.ui.main.LocalBottomBarSpace

class SettingsActions(
    val onPermissions: () -> Unit,
    val onBookmarks: () -> Unit,
    val onImport: () -> Unit,
    val onBackup: () -> Unit,
    val onRestore: () -> Unit,
    val onCatalogs: () -> Unit,
    val onOpenUrl: (String) -> Unit,
)

const val REPOSITORY_URL = "https://github.com/b1ink301/ScriptWidget"

/** Everything used now and then: permissions, files, backups, catalogs and about. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(versionName: String, actions: SettingsActions) {
    Scaffold(topBar = { TopAppBar(title = { Text("설정") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = LocalBottomBarSpace.current),
        ) {
            group("권한·위젯") {
                Row("🔐", "권한", "알림·위치·캘린더·배터리 최적화, 자동화 앱에서 실행", actions.onPermissions)
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
            group("정보") {
                Row("ℹ️", "ScriptWidget $versionName", "GitHub에서 소스 보기 · 업데이트 받기") { actions.onOpenUrl(REPOSITORY_URL) }
                Row("📄", "라이선스", "GPL-3.0 · 오픈소스 라이선스") { actions.onOpenUrl("$REPOSITORY_URL#라이선스") }
            }
        }
    }
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
