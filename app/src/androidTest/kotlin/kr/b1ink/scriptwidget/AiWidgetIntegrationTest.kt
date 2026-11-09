package kr.b1ink.scriptwidget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.ai.AiProvider
import kr.b1ink.scriptwidget.di.AppContainer
import kr.b1ink.scriptwidget.domain.fake.FakeRefreshScheduler
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import kr.b1ink.scriptwidget.domain.model.TextNode
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** "With AI" through the real container: the prompt, the preview run, saving, and where the key lives. */
@RunWith(AndroidJUnit4::class)
class AiWidgetIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "ai-it-${System.nanoTime()}")
    private val keychain = "ai-it-keychain-${System.nanoTime()}"
    private val container = AppContainer(context, rootDir = root, schedulerOverride = FakeRefreshScheduler(), keychainName = keychain)

    @After
    fun tearDown() {
        container.aiSettings.setKey(AiProvider.Gemini, null)
        root.deleteRecursively()
        context.deleteSharedPreferences(keychain)
    }

    @Test
    fun thePromptListsTheRealApi() = runTest {
        val prompt = container.aiPrompt("배터리 위젯")

        assertContains(prompt, "ListWidget(")
        assertContains(prompt, "Request(")
        assertContains(prompt, "배터리 위젯")
    }

    @Test
    fun aPastedScriptRunsAsAWidgetAndIsSavedUnderAFreeName() = runTest {
        val code = "const w = new ListWidget(); w.addText(config.widgetFamily); Script.setWidget(w);"

        val run = container.aiPreview(code)
        val first = container.saveNewScript("AI 위젯", code)
        val second = container.saveNewScript("AI 위젯", code)

        assertNull(run.error)
        assertEquals("medium", (run.widget!!.children.single() as TextNode).text)
        assertEquals("AI 위젯", first)
        assertEquals("AI 위젯 2", second)
        assertEquals(code, container.getScript("AI 위젯 2"))
    }

    @Test
    fun scriptsCantReadTheApiKey() = runTest {
        container.aiSettings.setKey(AiProvider.Gemini, "secret-key-123")

        val result = container.runScript(
            "const w = new ListWidget(); w.addText(String(Keychain.contains('Gemini'))); Script.setWidget(w);",
            ScriptEnvironment(runsInWidget = true),
            "probe",
        )

        assertEquals("secret-key-123", container.aiSettings.key(AiProvider.Gemini))
        assertEquals("false", (result.widget!!.children.single() as TextNode).text)
    }
}
