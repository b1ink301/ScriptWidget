package kr.b1ink.scriptwidget.ui.api

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kr.b1ink.scriptwidget.data.api.ApiEntry
import kr.b1ink.scriptwidget.data.api.ApiMember
import kr.b1ink.scriptwidget.di.AppContainer

@Composable
fun ApiReferenceRoute(container: AppContainer, onBack: (() -> Unit)? = null) {
    val vm = viewModel { ApiReferenceViewModel { container.apiCatalog() } }
    val state by vm.uiState.collectAsStateWithLifecycle()
    val selected = state.selected
    if (selected != null) {
        BackHandler { vm.select(null) }
        ApiEntryScreen(selected, onBack = { vm.select(null) })
    } else {
        ApiReferenceScreen(state, onBack, vm::search, vm::select)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiReferenceScreen(state: ApiReferenceUiState, onBack: (() -> Unit)?, onSearch: (String) -> Unit, onSelect: (String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(navigationIcon = { onBack?.let { TextButton(onClick = it) { Text("←") } } }, title = { Text("API 도움말") })
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = kr.b1ink.scriptwidget.ui.main.LocalBottomBarSpace.current)) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onSearch,
                    singleLine = true,
                    label = { Text("클래스·메서드 검색") },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
            if (state.loading) item { Text("불러오는 중…", Modifier.padding(16.dp)) }
            items(state.entries, key = { it.name }) { entry ->
                ListItem(
                    modifier = Modifier.clickable { onSelect(entry.name) },
                    headlineContent = { Text(entry.name, fontFamily = FontFamily.Monospace) },
                    supportingContent = { if (entry.summary.isNotEmpty()) Text(entry.summary, maxLines = 2) },
                    trailingContent = { Text(kindLabel(entry), style = MaterialTheme.typography.labelSmall) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApiEntryScreen(entry: ApiEntry, onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } },
                title = { Text(entry.name, fontFamily = FontFamily.Monospace) },
                actions = {
                    TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.docsUrl))) } }) {
                        Text("공식 문서")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (entry.summary.isNotEmpty()) item { Text(entry.summary, style = MaterialTheme.typography.bodyMedium) }
            entry.constructorParams?.takeIf { entry.kind != ApiEntry.Kind.Object }?.let { params ->
                item {
                    val code = if (entry.kind == ApiEntry.Kind.Function) "${entry.name}(${params.joinToString()})" else "new ${entry.name}(${params.joinToString()})"
                    Text(code, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 8.dp))
                }
            }
            section("정적 멤버", entry.members.filter { it.isStatic && entry.kind == ApiEntry.Kind.Class }, entry.name)
            section("메서드", entry.members.filter { it.kind == ApiMember.Kind.Method && !(it.isStatic && entry.kind == ApiEntry.Kind.Class) }, null)
            section("속성", entry.members.filter { it.kind == ApiMember.Kind.Property && !(it.isStatic && entry.kind == ApiEntry.Kind.Class) }, null)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, members: List<ApiMember>, owner: String?) {
    if (members.isEmpty()) return
    item { Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp)) }
    items(members, key = { "$title-${it.name}" }) { member ->
        Column {
            val target = (owner?.let { "$it." } ?: ".") + member.name
            val call = if (member.kind == ApiMember.Kind.Method) "$target(${member.params.joinToString(", ")})" else target
            Text(if (member.isAsync) "await $call" else call, fontFamily = FontFamily.Monospace)
        }
    }
}

private fun kindLabel(entry: ApiEntry) = when (entry.kind) {
    ApiEntry.Kind.Class -> "클래스"
    ApiEntry.Kind.Object -> "객체"
    ApiEntry.Kind.Function -> "함수"
}
