package kr.b1ink.scriptwidget.ui.scriptui

import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.AlertAction
import kr.b1ink.scriptwidget.data.engine.AlertResult
import kr.b1ink.scriptwidget.data.engine.AlertSpec
import kr.b1ink.scriptwidget.data.engine.DatePickerSpec
import kr.b1ink.scriptwidget.data.engine.QuickLookSpec
import kr.b1ink.scriptwidget.data.engine.TableEvent
import kr.b1ink.scriptwidget.data.engine.TableSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScriptUiHostTest {
    private val host = ScriptUiHost()
    private val alert = AlertSpec(title = "t", actions = listOf(AlertAction("OK")))

    @Test
    fun `without a visible screen UI requests fail`() = runTest {
        assertFailsWith<IllegalStateException> { host.alert(alert) }
    }

    @Test
    fun `an alert is shown until the user answers`() = runTest {
        host.attach()
        val answer = async { host.alert(alert) }
        testScheduler.runCurrent()

        val dialog = assertIs<UiDialog.Alert>(host.dialog.value)
        assertEquals(alert, dialog.spec)
        dialog.respond(AlertResult(0, listOf("x")))

        assertEquals(AlertResult(0, listOf("x")), answer.await())
        assertNull(host.dialog.value)
    }

    @Test
    fun `cancelling the script removes its dialog`() = runTest {
        host.attach()
        val answer = async { host.alert(alert) }
        testScheduler.runCurrent()

        answer.cancel()
        testScheduler.runCurrent()

        assertNull(host.dialog.value)
    }

    @Test
    fun `the table stays visible between events and closes on dismissal`() = runTest {
        host.attach()
        val first = async { host.table(TableSpec()) }
        testScheduler.runCurrent()
        assertIs<UiScreen.Table>(host.screen.value).respond(TableEvent.Selected(2))
        assertEquals(TableEvent.Selected(2), first.await())
        assertIs<UiScreen.Table>(host.screen.value)

        val second = async { host.table(TableSpec(showSeparators = true)) }
        testScheduler.runCurrent()
        val shown = assertIs<UiScreen.Table>(host.screen.value)
        assertTrue(shown.spec.showSeparators)
        shown.respond(TableEvent.Dismissed)

        assertEquals(TableEvent.Dismissed, second.await())
        assertNull(host.screen.value)
    }

    @Test
    fun `scripts can close the table themselves`() = runTest {
        host.attach()
        val event = async { host.table(TableSpec()) }
        testScheduler.runCurrent()
        assertIs<UiScreen.Table>(host.screen.value).respond(TableEvent.Selected(0))
        event.await()

        host.dismissTable()

        assertNull(host.screen.value)
    }

    @Test
    fun `date pickers and quick look complete when answered or closed`() = runTest {
        host.attach()
        val date = async { host.pickDate(DatePickerSpec(mode = "date")) }
        testScheduler.runCurrent()
        assertIs<UiDialog.DatePicker>(host.dialog.value).respond(123L)
        assertEquals(123L, date.await())

        val look = async { host.quickLook(QuickLookSpec(text = "hi")) }
        testScheduler.runCurrent()
        assertIs<UiScreen.QuickLook>(host.screen.value).close()
        look.await()
        assertNull(host.screen.value)
    }

    @Test
    fun `leaving the screen answers pending requests as cancelled`() = runTest {
        host.attach()
        val answer = async { host.alert(alert) }
        val table = async { host.table(TableSpec()) }
        testScheduler.runCurrent()

        host.detach()

        assertEquals(-1, answer.await().index)
        assertEquals(TableEvent.Dismissed, table.await())
        assertNull(host.dialog.value)
        assertNull(host.screen.value)
    }

    @Test
    fun `web pages are presented until closed`() = runTest {
        host.attach()
        val page = object : kr.b1ink.scriptwidget.data.engine.WebPage {
            override suspend fun loadUrl(url: String) = Unit
            override suspend fun loadHtml(html: String, baseUrl: String?) = Unit
            override suspend fun evaluate(script: String, useCallback: Boolean) = "null"
            override suspend fun html() = ""
            override fun close() = Unit
        }
        val shown = async { host.presentWebPage(page, fullscreen = true) }
        testScheduler.runCurrent()

        val screen = assertIs<UiScreen.Web>(host.screen.value)
        assertTrue(screen.fullscreen)
        screen.close()
        shown.await()
        assertNull(host.screen.value)
    }

    @Test
    fun `document requests are answered with handles`() = runTest {
        host.attach()
        val request = kr.b1ink.scriptwidget.data.engine.DocumentRequest(kind = "open", mimeTypes = listOf("*/*"), multiple = true)
        val picked = async { host.pickDocuments(request) }
        testScheduler.runCurrent()

        val dialog = assertIs<UiDialog.Documents>(host.dialog.value)
        assertEquals(request, dialog.request)
        dialog.respond(listOf("content://a"))

        assertEquals(listOf("content://a"), picked.await())
    }

    @Test
    fun `dictation is answered with the recognized text`() = runTest {
        host.attach()
        val text = async { host.dictate("ko-KR") }
        testScheduler.runCurrent()

        val dialog = assertIs<UiDialog.Dictation>(host.dialog.value)
        assertEquals("ko-KR", dialog.locale)
        dialog.respond("안녕")

        assertEquals("안녕", text.await())
    }

    @Test
    fun `image pickers answer with a handle`() = runTest {
        host.attach()
        val picked = async { host.pickImage("camera") }
        testScheduler.runCurrent()

        val dialog = assertIs<UiDialog.ImagePicker>(host.dialog.value)
        assertEquals("camera", dialog.source)
        dialog.respond("content://photo")

        assertEquals("content://photo", picked.await())
    }
}
