package kr.b1ink.scriptwidget.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.data.ai.AiProvider
import kr.b1ink.scriptwidget.domain.ai.AiWidgetResult
import kr.b1ink.scriptwidget.domain.model.ScriptRunResult
import kr.b1ink.scriptwidget.domain.model.TextNode
import kr.b1ink.scriptwidget.domain.model.WidgetRoot
import kr.b1ink.scriptwidget.ui.list.AiWidgetBackend
import kr.b1ink.scriptwidget.ui.list.AiWidgetDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AiWidgetDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private class FakeBackend : AiWidgetBackend {
        override var provider = AiProvider.Gemini
        val keys = mutableMapOf<AiProvider, String>()
        val modelsSet = mutableMapOf<AiProvider, String>()
        val prompts = mutableListOf<String>()
        val generated = mutableListOf<String>()
        override fun model(provider: AiProvider) = modelsSet[provider].orEmpty()
        override fun setModel(provider: AiProvider, model: String) { modelsSet[provider] = model }
        override fun keyHint(provider: AiProvider) = keys[provider]?.takeLast(4)
        override fun setKey(provider: AiProvider, key: String) { keys[provider] = key }
        override suspend fun models(provider: AiProvider) = listOf("model-a", "model-b")
        override suspend fun prompt(request: String) = "PROMPT for $request".also { prompts += request }
        override suspend fun run(code: String) = if ("broken" in code) {
            ScriptRunResult(error = "ReferenceError: 'broken' is not defined")
        } else {
            ScriptRunResult(widget = WidgetRoot(children = listOf(TextNode("Hello from AI"))))
        }
        override suspend fun generate(request: String, onAttempt: (Int) -> Unit): AiWidgetResult {
            generated += request
            onAttempt(1)
            val code = "const w = new ListWidget(); w.addText('Hello from AI'); Script.setWidget(w);"
            return AiWidgetResult(code, attempts = 1, error = null, run = run(code))
        }
    }

    private val backend = FakeBackend()
    private val saved = mutableListOf<Pair<String, String>>()

    private fun open() = compose.setContent { AiWidgetDialog(backend, onSave = { name, code -> saved += name to code }, onDismiss = {}) }

    @Test
    fun copyPromptThenPasteTheAnswerRunsAndSavesIt() {
        open()
        compose.onNodeWithTag("ai-request").performTextInput("인사 위젯")
        compose.onNodeWithTag("ai-mode-copy").performClick()
        compose.onNodeWithTag("ai-copy-prompt").performClick()
        compose.waitUntil(5_000) { backend.prompts.isNotEmpty() }

        compose.onNodeWithTag("ai-answer").performTextInput("Sure!\n```javascript\nconst w = new ListWidget(); w.addText('Hello from AI'); Script.setWidget(w);\n```")
        compose.onNodeWithTag("ai-try").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("ai-works").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("ai-save").performScrollTo().performClick()

        assertEquals(listOf("인사 위젯"), backend.prompts)
        val (name, code) = saved.single()
        assertEquals("인사 위젯", name)
        assertTrue(code.startsWith("const w = new ListWidget();"), code)
    }

    @Test
    fun aFailingPastedScriptOffersAFixRequest() {
        open()
        compose.onNodeWithTag("ai-request").performTextInput("x")
        compose.onNodeWithTag("ai-mode-copy").performClick()
        compose.onNodeWithTag("ai-answer").performTextInput("```js\nbroken(); const w = new ListWidget(); Script.setWidget(w);\n```")
        compose.onNodeWithTag("ai-try").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("ai-error").fetchSemanticsNodes().isNotEmpty() }

        compose.onNodeWithTag("ai-copy-fix").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun withAKeyAndModelTheAppGeneratesItself() {
        open()
        compose.onNodeWithTag("ai-request").performTextInput("인사 위젯")
        compose.onNodeWithTag("ai-generate").assertIsNotEnabled()

        compose.onNodeWithTag("ai-provider-Claude").performClick()
        compose.onNodeWithTag("ai-key").performTextInput("sk-test-1234")
        compose.onNodeWithTag("ai-model").performTextReplacement("model-x")
        // The key is saved with its OK button.
        compose.onNodeWithTag("ai-key-ok", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("ai-generate").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("ai-works").fetchSemanticsNodes().isNotEmpty() }

        assertEquals(AiProvider.Claude, backend.provider)
        assertEquals("sk-test-1234", backend.keys[AiProvider.Claude])
        assertEquals("model-x", backend.modelsSet[AiProvider.Claude])
        assertEquals(listOf("인사 위젯"), backend.generated)
    }
}
