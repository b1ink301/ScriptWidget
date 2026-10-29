package kr.b1ink.scriptwidget.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kr.b1ink.scriptwidget.data.engine.AlertAction
import kr.b1ink.scriptwidget.data.engine.AlertResult
import kr.b1ink.scriptwidget.data.engine.AlertSpec
import kr.b1ink.scriptwidget.data.engine.AlertTextField
import kr.b1ink.scriptwidget.data.engine.QuickLookSpec
import kr.b1ink.scriptwidget.data.engine.TableCell
import kr.b1ink.scriptwidget.data.engine.TableEvent
import kr.b1ink.scriptwidget.data.engine.TableRow
import kr.b1ink.scriptwidget.data.engine.TableSpec
import kr.b1ink.scriptwidget.ui.scriptui.ScriptUiHost
import kr.b1ink.scriptwidget.ui.scriptui.ScriptUiOverlay
import kr.b1ink.scriptwidget.ui.scriptui.TABLE_IMAGE_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class ScriptUiOverlayTest {
    @get:Rule
    val compose = createComposeRule()

    private val host = ScriptUiHost().apply { attach() }

    private fun <T> request(block: suspend () -> T): CompletableDeferred<T> {
        val result = CompletableDeferred<T>()
        CoroutineScope(Dispatchers.Default).launch { result.complete(block()) }
        return result
    }

    private fun <T> CompletableDeferred<T>.get(): T = runBlocking { withTimeout(5_000) { await() } }

    @Test
    fun alertButtonsAndTextFieldsAnswerTheScript() {
        compose.setContent { ScriptUiOverlay(host) }
        val answer = request {
            host.alert(
                AlertSpec(
                    title = "City", message = "Where?",
                    actions = listOf(AlertAction("Save"), AlertAction("Delete", "destructive"), AlertAction("Cancel", "cancel")),
                    textFields = listOf(AlertTextField(placeholder = "name", text = "Busan")),
                )
            )
        }
        compose.waitUntil(5_000) { host.dialog.value != null }

        compose.onNodeWithText("Where?").assertExists()
        compose.onNodeWithText("Busan").performTextReplacement("Seoul")
        compose.onNodeWithText("Save").performClick()

        assertEquals(AlertResult(0, listOf("Seoul")), answer.get())
    }

    @Test
    fun cancelActionReturnsMinusOne() {
        compose.setContent { ScriptUiOverlay(host) }
        val answer = request { host.alert(AlertSpec(actions = listOf(AlertAction("OK"), AlertAction("Cancel", "cancel")))) }
        compose.waitUntil(5_000) { host.dialog.value != null }

        compose.onNodeWithText("Cancel").performClick()

        assertEquals(-1, answer.get().index)
    }

    @Test
    fun tableRowsButtonsAndCloseAreReported() {
        compose.setContent { ScriptUiOverlay(host) }
        val spec = TableSpec(
            rows = listOf(
                TableRow(cells = listOf(TableCell(type = "text", title = "Header")), isHeader = true),
                TableRow(cells = listOf(TableCell(type = "text", title = "Seoul", subtitle = "37.5"), TableCell(type = "button", title = "Go")), selectable = true),
            )
        )
        var answer = request { host.table(spec) }
        compose.waitUntil(5_000) { host.screen.value != null }
        compose.onNodeWithText("37.5").assertExists()
        compose.onNodeWithText("Seoul", useUnmergedTree = true).performClick()
        assertEquals(TableEvent.Selected(1), answer.get())

        answer = request { host.table(spec) }
        compose.waitForIdle()
        compose.onNodeWithText("Go").performClick()
        assertEquals(TableEvent.CellTapped(1, 1), answer.get())

        answer = request { host.table(spec) }
        compose.waitForIdle()
        compose.onNodeWithText("닫기").performClick()
        assertEquals(TableEvent.Dismissed, answer.get())
    }

    @Test
    fun closeStillWorksWhileTheScriptIsNotListening() {
        compose.setContent { ScriptUiOverlay(host) }
        val spec = TableSpec(rows = listOf(TableRow(cells = listOf(TableCell(type = "text", title = "Menu")), selectable = true)))
        val answer = request { host.table(spec) }
        compose.waitUntil(5_000) { host.screen.value != null }
        compose.onNodeWithText("Menu", useUnmergedTree = true).performClick()
        assertEquals(TableEvent.Selected(0), answer.get())

        // The script is busy (or has ended) and hasn't asked for the next event.
        compose.onNodeWithText("닫기").performClick()

        compose.waitUntil(5_000) { host.screen.value == null }
    }

    @Test
    fun imageCellsFillTallRows() {
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "table_cell.png")
        file.outputStream().use { Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.setContent { ScriptUiOverlay(host) }
        request { host.table(TableSpec(rows = listOf(TableRow(cells = listOf(TableCell(type = "image", src = file.absolutePath)), height = 200f)))) }
        compose.waitUntil(5_000) { host.screen.value != null }

        val height = compose.onNodeWithTag(TABLE_IMAGE_TAG, useUnmergedTree = true).fetchSemanticsNode().size.height
        val density = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
        assertTrue(height / density > 150f, "image is ${height / density}dp tall")
    }

    @Test
    fun quickLookShowsTextUntilClosed() {
        compose.setContent { ScriptUiOverlay(host) }
        val done = request { host.quickLook(QuickLookSpec(text = "preview text")) }
        compose.waitUntil(5_000) { host.screen.value != null }

        compose.onNodeWithText("preview text").assertExists()
        compose.onNodeWithText("닫기").performClick()

        done.get()
    }
}
