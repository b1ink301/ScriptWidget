package kr.b1ink.scriptwidget.domain.model

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.ai.AiWidget
import kr.b1ink.scriptwidget.domain.ai.GenerateWidgetUseCase
import kr.b1ink.scriptwidget.domain.ai.LlmClient
import kr.b1ink.scriptwidget.domain.ai.LlmMessage
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiWidgetTest {
    private val reference = "ListWidget: addText(text), addStack(), backgroundColor\nRequest: await loadJSON()"

    @Test
    fun `the prompt has the rules, the real API list and the request`() {
        val prompt = AiWidget.prompt("서울 날씨를 크게 보여 줘", reference)

        assertContains(prompt, "Scriptable")
        assertContains(prompt, "Script.setWidget")
        assertContains(prompt, "config.runsInWidget")
        assertContains(prompt, reference)
        assertContains(prompt, "서울 날씨를 크게 보여 줘")
        // One code block back, so the answer can be pasted or read as is.
        assertContains(prompt, "```javascript")
    }

    @Test
    fun `the fix prompt carries the code and what went wrong`() {
        val prompt = AiWidget.fixPrompt("const w = new ListWidget();", "TypeError: x is undefined\n    at test.js:3")

        assertContains(prompt, "const w = new ListWidget();")
        assertContains(prompt, "TypeError: x is undefined")
    }

    @Test
    fun `code is taken from a fenced block`() {
        val answer = "Here you go:\n```javascript\nconst w = new ListWidget();\nScript.setWidget(w);\n```\nEnjoy!"

        assertEquals("const w = new ListWidget();\nScript.setWidget(w);", AiWidget.code(answer))
    }

    @Test
    fun `js and unlabeled fences work, and the longest block wins`() {
        assertEquals("let a = 1;", AiWidget.code("```js\nlet a = 1;\n```"))
        assertEquals("let b = 2;", AiWidget.code("```\nlet b = 2;\n```"))
        assertEquals("const w = new ListWidget();\nScript.setWidget(w);", AiWidget.code("```\nnpm i\n```\n```javascript\nconst w = new ListWidget();\nScript.setWidget(w);\n```"))
    }

    @Test
    fun `plain code without fences is used as it is`() {
        assertEquals("const w = new ListWidget();\nScript.setWidget(w);", AiWidget.code("  const w = new ListWidget();\nScript.setWidget(w);\n"))
    }

    @Test
    fun `an answer without code gives nothing`() {
        assertNull(AiWidget.code("Sorry, I can't help with that."))
        assertNull(AiWidget.code(""))
    }

    @Test
    fun `a name comes from the request`() {
        assertEquals("서울 날씨를 크게 보여 줘", AiWidget.name("서울 날씨를 크게 보여 줘"))
        assertEquals("a/b c", AiWidget.name("a/b c").replace('-', '/'))
        assertTrue(AiWidget.name("x".repeat(200)).length <= 40)
        assertEquals("AI widget", AiWidget.name("   "))
        assertTrue(ScriptName.isValid(AiWidget.name("../secret\\path")))
    }

    @Test
    fun `a taken name gets a number`() {
        assertEquals("날씨", AiWidget.uniqueName("날씨", setOf("시계")))
        assertEquals("날씨 3", AiWidget.uniqueName("날씨", setOf("날씨", "날씨 2")))
    }

    private class ScriptedLlm(private val answers: List<String>) : LlmClient {
        val calls = mutableListOf<List<LlmMessage>>()
        override suspend fun complete(system: String, messages: List<LlmMessage>): String {
            calls += messages
            return answers[calls.size - 1]
        }
    }

    private val good = "```javascript\nconst w = new ListWidget(); w.addText('ok'); Script.setWidget(w);\n```"
    private val bad = "```javascript\nundefinedFn();\n```"

    private fun run(code: String) = if ("undefinedFn" in code) {
        ScriptRunResult(error = "ReferenceError: 'undefinedFn' is not defined")
    } else {
        ScriptRunResult(widget = WidgetRoot(children = listOf(TextNode("ok"))))
    }

    @Test
    fun `a widget that works the first time is done in one call`() = runTest {
        val llm = ScriptedLlm(listOf(good))

        val result = GenerateWidgetUseCase(llm, reference) { run(it) }("ok 위젯")

        assertEquals(1, llm.calls.size)
        assertNull(result.error)
        assertEquals(1, result.attempts)
        assertContains(result.code!!, "Script.setWidget")
    }

    @Test
    fun `errors go back to the model until the widget runs`() = runTest {
        val llm = ScriptedLlm(listOf(bad, good))
        val progress = mutableListOf<Int>()

        val result = GenerateWidgetUseCase(llm, reference) { run(it) }("ok 위젯") { progress += it }

        assertNull(result.error)
        assertEquals(2, result.attempts)
        assertEquals(listOf(1, 2), progress)
        // The second call continues the conversation with the error.
        val second = llm.calls[1]
        assertEquals(LlmMessage.Role.Assistant, second[1].role)
        assertContains(second[2].text, "undefinedFn")
    }

    @Test
    fun `it stops after the last attempt and keeps the code and error`() = runTest {
        val llm = ScriptedLlm(List(GenerateWidgetUseCase.MAX_ATTEMPTS) { bad })

        val result = GenerateWidgetUseCase(llm, reference) { run(it) }("ok 위젯")

        assertEquals(GenerateWidgetUseCase.MAX_ATTEMPTS, llm.calls.size)
        assertContains(result.error!!, "undefinedFn")
        assertContains(result.code!!, "undefinedFn")
    }

    @Test
    fun `an answer without code is asked again`() = runTest {
        val llm = ScriptedLlm(listOf("I think you want a weather widget.", good))

        val result = GenerateWidgetUseCase(llm, reference) { run(it) }("ok 위젯")

        assertNull(result.error)
        assertFalse(llm.calls[1].last().text.isBlank())
    }
}
