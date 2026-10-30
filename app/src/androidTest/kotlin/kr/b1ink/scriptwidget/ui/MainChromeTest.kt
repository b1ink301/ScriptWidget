package kr.b1ink.scriptwidget.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.AppRelease
import kr.b1ink.scriptwidget.domain.model.ScriptSummary
import kr.b1ink.scriptwidget.domain.model.UpdateStatus
import kr.b1ink.scriptwidget.ui.list.ScriptListScreen
import kr.b1ink.scriptwidget.ui.list.ScriptListUiState
import kr.b1ink.scriptwidget.ui.main.FloatingTabBar
import kr.b1ink.scriptwidget.ui.main.MainTab
import kr.b1ink.scriptwidget.ui.settings.SettingsActions
import kr.b1ink.scriptwidget.ui.settings.SettingsScreen
import kr.b1ink.scriptwidget.ui.settings.UpdateUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** The floating tab bar, the settings tab and the "my scripts" top bar. */
@RunWith(AndroidJUnit4::class)
class MainChromeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tabBarSwitchesTabsAndMarksTheSelectedOne() {
        var tab by mutableStateOf(MainTab.Scripts)
        compose.setContent { FloatingTabBar(selected = tab, onSelect = { tab = it }) }

        compose.onNodeWithTag("tab-Scripts").assertIsSelected()
        compose.onNodeWithTag("tab-Gallery").performClick()

        assertEquals(MainTab.Gallery, tab)
        compose.onNodeWithTag("tab-Gallery").assertIsSelected()
        compose.onNodeWithTag("tab-Scripts").assertIsNotSelected()
        listOf("내 스크립트", "갤러리", "기록", "도움말", "설정").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun oneIndicatorMovesToTheSelectedTab() {
        var tab by mutableStateOf(MainTab.History)
        compose.setContent { FloatingTabBar(selected = tab, onSelect = { tab = it }) }

        compose.onNodeWithTag("tab-Help").performClick()
        compose.waitForIdle()

        // A single indicator (no second capsule fading out on the previous tab), centered on the new tab.
        val indicators = compose.onAllNodesWithTag("tab-indicator", useUnmergedTree = true).fetchSemanticsNodes()
        assertEquals(1, indicators.size)
        val indicator = indicators.single().boundsInRoot
        val help = compose.onNodeWithTag("tab-Help").fetchSemanticsNode().boundsInRoot
        kotlin.test.assertTrue(
            kotlin.math.abs(indicator.center.x - help.center.x) < 2f,
            "indicator at ${indicator.center.x}, Help tab at ${help.center.x}",
        )
    }

    @Test
    fun settingsItemsLeadToTheirFeatures() {
        val calls = mutableListOf<String>()
        compose.setContent {
            SettingsScreen(
                versionName = "1.2.3",
                refreshWhileScreenOff = false,
                actions = SettingsActions(
                    onPermissions = { calls += "permissions" },
                    onBookmarks = { calls += "bookmarks" },
                    onImport = { calls += "import" },
                    onBackup = { calls += "backup" },
                    onRestore = { calls += "restore" },
                    onCatalogs = { calls += "catalogs" },
                    onOpenUrl = { calls += "url:$it" },
                    onRefreshWhileScreenOff = { calls += "screenOff:$it" },
                ),
            )
        }

        compose.onNodeWithTag("screen-off-refresh").performClick()
        listOf("권한", "파일 북마크", "스크립트 가져오기", "전체 백업", "백업에서 복원", "카탈로그 관리").forEach {
            scrollSettingsTo(hasText(it))
            compose.onNodeWithText(it).performClick()
        }
        scrollSettingsTo(hasText("ScriptWidget 1.2.3"))
        compose.onNodeWithText("ScriptWidget 1.2.3").assertIsDisplayed()
        scrollSettingsTo(hasText("GitHub에서 소스 보기"))
        compose.onNodeWithText("GitHub에서 소스 보기").performClick()

        assertEquals(
            listOf("screenOff:true", "permissions", "bookmarks", "import", "backup", "restore", "catalogs", "url:https://github.com/b1ink301/ScriptWidget"),
            calls,
        )
    }

    @Test
    fun settingsOffersAnAvailableUpdateAndChecksOnTap() {
        val calls = mutableListOf<String>()
        var update by mutableStateOf<UpdateUiState>(UpdateUiState.Idle)
        compose.setContent {
            SettingsScreen(
                versionName = "0.1.0",
                update = update,
                actions = SettingsActions(
                    onPermissions = {}, onBookmarks = {}, onImport = {}, onBackup = {}, onRestore = {}, onCatalogs = {},
                    onOpenUrl = { calls += "url:$it" },
                    onCheckUpdate = { calls += "check" },
                ),
            )
        }

        scrollSettingsTo(hasTestTag("update-row"))
        compose.onNodeWithTag("update-row").performClick()
        update = UpdateUiState.Done(UpdateStatus.Available("0.1.0", AppRelease("v0.2.0", "https://page", "https://apk", "")))
        scrollSettingsTo(hasText("새 버전 v0.2.0", substring = true))
        compose.onNodeWithText("새 버전 v0.2.0", substring = true).performClick()
        update = UpdateUiState.Done(UpdateStatus.UpToDate("0.1.0"))
        compose.onNodeWithText("최신 버전", substring = true).assertIsDisplayed()

        assertEquals(listOf("check", "url:https://apk"), calls)
    }

    /** The settings list is lazy: on short screens (CI emulators) the lower rows are not composed until scrolled to. */
    private fun scrollSettingsTo(matcher: SemanticsMatcher) {
        compose.onNode(hasScrollAction()).performScrollToNode(matcher)
    }

    @Test
    fun myScriptsHasNewScriptInTheTopBarSearchAndRowDetails() {
        var created = 0
        var query = ""
        compose.setContent {
            ScriptListScreen(
                state = ScriptListUiState(
                    scripts = listOf(ScriptSummary("오늘 일정", null)),
                    activity = mapOf("오늘 일정" to kr.b1ink.scriptwidget.domain.model.ScriptActivity(widgets = 2)),
                ),
                onOpen = {}, onCreate = { created++ }, onRequestDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
                onSearch = { query = it },
            )
        }

        compose.onNodeWithTag("new-script").performClick()
        compose.onNodeWithText("코드로 작성", substring = true).performClick()
        compose.onNodeWithTag("script-search").performTextInput("일정")

        assertEquals(1, created)
        assertEquals("일정", query)
        compose.onNodeWithText("위젯 2개").assertIsDisplayed()
    }

    @Test
    fun newScriptOffersDesignerTemplates() {
        val designs = mutableListOf<kr.b1ink.scriptwidget.domain.model.DesignTemplates>()
        compose.setContent {
            ScriptListScreen(
                state = ScriptListUiState(),
                onOpen = {}, onCreate = {}, onRequestDelete = {}, onConfirmDelete = {}, onCancelDelete = {},
                onNewDesign = { designs += it },
            )
        }

        compose.onNodeWithTag("new-script").performClick()
        compose.onNodeWithTag("template-Battery").performClick()

        assertEquals(listOf(kr.b1ink.scriptwidget.domain.model.DesignTemplates.Battery), designs)
    }
}
