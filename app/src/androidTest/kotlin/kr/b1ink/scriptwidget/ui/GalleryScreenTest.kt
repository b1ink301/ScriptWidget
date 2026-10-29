package kr.b1ink.scriptwidget.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kr.b1ink.scriptwidget.domain.model.EntryStatus
import kr.b1ink.scriptwidget.domain.model.GalleryItem
import kr.b1ink.scriptwidget.domain.model.GallerySection
import kr.b1ink.scriptwidget.ui.gallery.GalleryActions
import kr.b1ink.scriptwidget.ui.gallery.GalleryScreen
import kr.b1ink.scriptwidget.ui.gallery.GalleryUiState
import kr.b1ink.scriptwidget.ui.gallery.InstallPreview
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class GalleryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = CatalogEntry(id = "events", name = "오늘 일정", category = "일정·할 일", description = "오늘 남은 일정", script = "e.js", sizes = listOf("small", "medium"), permissions = listOf("calendar"))
    private val quotes = CatalogEntry(id = "quotes", name = "명언", category = "정보", description = "날마다 다른 명언", script = "q.js")
    private val state = GalleryUiState(
        loading = false,
        sections = listOf(
            GallerySection("builtin", "기본 갤러리", listOf(GalleryItem(events, EntryStatus.NotInstalled), GalleryItem(quotes, EntryStatus.Installed("명언")))),
            GallerySection("https://x.example/index.json", "https://x.example/index.json", emptyList(), error = "HTTP 404", removable = true),
        ),
    )

    private val calls = mutableListOf<String>()
    private val actions = GalleryActions(
        onInstall = { s, e -> calls += "install:$s:$e" },
        onUpdate = { s, e -> calls += "update:$s:$e" },
        onOpen = { calls += "open:$it" },
        onConfirm = { calls += "confirm" },
        onCancel = { calls += "cancel" },
        onCategory = { calls += "category:$it" },
        onAddUrl = { calls += "add:$it" },
        onRemoveUrl = { calls += "remove:$it" },
    )

    @Test
    fun entriesShowDescriptionPermissionsAndTheRightButton() {
        compose.setContent { GalleryScreen(state, actions, onBack = {}) }

        compose.onNodeWithText("오늘 남은 일정").assertIsDisplayed()
        compose.onNodeWithText("캘린더", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("gallery-action-events").performClick()
        compose.onNodeWithTag("gallery-action-quotes").performClick()
        compose.onNodeWithText("HTTP 404", substring = true).assertIsDisplayed()

        assertEquals(listOf("install:builtin:events", "open:명언"), calls)
    }

    @Test
    fun downloadedScriptsAreShownBeforeInstalling() {
        val preview = state.copy(preview = InstallPreview("https://x.example/index.json", events, "const w = new ListWidget()"))
        compose.setContent { GalleryScreen(preview, actions, onBack = {}) }

        compose.onNodeWithText("const w = new ListWidget()").assertIsDisplayed()
        compose.onNodeWithTag("install-confirm").performClick()

        assertEquals(listOf("confirm"), calls)
    }
}
