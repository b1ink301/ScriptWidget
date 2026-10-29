package kr.b1ink.scriptwidget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.model.ScriptCommandResult
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetFamily
import kr.b1ink.scriptwidget.domain.usecase.RefreshWidgetUseCase
import kr.b1ink.scriptwidget.di.AppContainer
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wires the real data layer (QuickJS for Android, files, DataStore, bitmap store) through the
 * container and exercises it via use cases, the way the UI and widget use it.
 */
@RunWith(AndroidJUnit4::class)
class AppContainerIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "container-it-${System.nanoTime()}")
    private val scheduler = FakeRefreshScheduler()
    private val keychain = "it-keychain-${System.nanoTime()}"
    private val container = AppContainer(context, rootDir = root, schedulerOverride = scheduler, keychainName = keychain)

    @After
    fun tearDown() {
        root.deleteRecursively()
        context.deleteSharedPreferences(keychain)
    }

    private val preview = ScriptEnvironment(runsInWidget = false, widgetFamily = WidgetFamily.Medium)

    @Test
    fun seedsTheBundledSamplesOnce() = runTest {
        container.seedSamples()
        container.seedSamples()

        val names = container.observeScriptNames().value
        assertEquals(23, names.size, names.toString())
        assertTrue(names.containsAll(listOf("01_hello", "09_counter", "오늘 일정", "할 일", "세계 시계")), names.toString())
        // Seeded samples are known to the gallery, so they can be updated later.
        assertEquals(23, container.installedScripts.all().size)
    }

    @Test
    fun runsTheHelloSampleOnAndroidQuickJs() = runTest {
        container.seedSamples()
        val script = assertNotNull(container.getScript("01_hello"))

        val result = container.runScript(script, preview.copy(widgetParameter = "p"), "01_hello")

        assertNull(result.error)
        val texts = result.widget!!.children.filterIsInstance<TextNode>().map { it.text }
        assertEquals("Hello, Widget 👋", texts.first())
        assertContains(texts[1], "family: medium · param: p")
    }

    @Test
    fun fileBookmarksSavedInTheAppAreReadableByScripts() = runTest {
        val png = java.io.ByteArrayOutputStream().also {
            android.graphics.Bitmap.createBitmap(4, 4, android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        container.fileBookmarks.save("still", png)

        val result = container.runScript(
            """
            const fm = FileManager.iCloud();
            const img = fm.readImage(fm.bookmarkedPath("still"));
            console.log(fm.bookmarkExists("still"), img.size.width);
            """,
            preview,
            "bookmarks",
        )

        assertNull(result.error, result.error)
        assertEquals("true 4", result.logs.single().message)
    }

    @Test
    fun asyncTimersWorkOnDevice() = runTest {
        container.seedSamples()

        val result = container.runScript(container.getScript("05_async_timers")!!, preview, "05_async_timers")

        assertNull(result.error)
        assertContains(result.logs.single().message, """results ["A","B","C"]""")
    }

    @Test
    fun scriptErrorsCarryLineNumbers() = runTest {
        val result = container.runScript("const a = 1;\nnope();", preview, "broken")

        assertContains(assertNotNull(result.error), "broken.js:2")
    }

    @Test
    fun renameMovesTheFileAndTheWidgetBinding() = runTest {
        container.saveScript("weather", "Script.setWidget(new ListWidget());")
        container.bindWidget(appWidgetId = 41, scriptName = "weather", parameter = "Seoul")

        val result = container.renameScript("weather", "forecast")

        assertEquals(ScriptCommandResult.Success("forecast"), result)
        assertEquals("Script.setWidget(new ListWidget());", container.getScript("forecast"))
        assertEquals("forecast", container.getWidgetBinding(41)!!.scriptName)
        assertEquals("Seoul", container.getWidgetBinding(41)!!.parameter)
        assertTrue(41 in scheduler.refreshed)
    }

    @Test
    fun refreshingAnUnknownWidgetReportsItIsNotBound() = runTest {
        val state = container.refreshWidget(appWidgetId = 987_654, family = WidgetFamily.Small)

        assertEquals(RefreshWidgetUseCase.NOT_BOUND_ERROR, state.error)
    }

    @Test
    fun fileManagerAndKeychainPersistAcrossRuns() = runTest {
        val write = container.runScript(
            """
            const fm = FileManager.local();
            fm.writeString(fm.joinPath(fm.documentsDirectory(), "state.json"), JSON.stringify({n: 1}));
            Keychain.set("token", "t-123");
            """,
            preview,
            "writer",
        )
        assertNull(write.error)

        val read = container.runScript(
            """
            const fm = FileManager.local();
            console.log(JSON.parse(fm.readString(fm.joinPath(fm.documentsDirectory(), "state.json"))).n, Keychain.get("token"));
            """,
            preview,
            "reader",
        )

        assertNull(read.error)
        assertEquals("1 t-123", read.logs.single().message)
        assertTrue(File(root, "script_storage/Documents/state.json").isFile)
    }

    @Test
    fun scriptsCanImportOtherScriptsAsModules() = runTest {
        container.saveScript("format", "module.exports.won = (n) => n.toFixed(0) + '원';")

        val result = container.runScript("""console.log(importModule("format").won(1234.5));""", preview, "main")

        assertNull(result.error)
        assertEquals("1235원", result.logs.single().message)
    }
}
