package kr.b1ink.scriptwidget.data.ai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kr.b1ink.scriptwidget.data.engine.FakeHttpGateway
import kr.b1ink.scriptwidget.data.engine.HttpResponseSpec
import kr.b1ink.scriptwidget.domain.ai.LlmMessage
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LlmClientsTest {
    private val http = FakeHttpGateway()
    private fun reply(status: Int = 200, body: String) {
        http.responses = { HttpResponseSpec(it.url, status, emptyMap(), "application/json", body) }
    }

    private val conversation = listOf(
        LlmMessage(LlmMessage.Role.User, "make a widget"),
        LlmMessage(LlmMessage.Role.Assistant, "```javascript\nx\n```"),
        LlmMessage(LlmMessage.Role.User, "fix it"),
    )

    private fun body() = Json.parseToJsonElement(http.requests.single().body!!).jsonObject

    @Test
    fun `gemini sends the key in a header and reads the text parts`() = runTest {
        reply(body = """{"candidates":[{"content":{"parts":[{"text":"thinking…","thought":true},{"text":"```js\nok\n```"}]}}]}""")

        val answer = LlmClients.create(AiProvider.Gemini, http, apiKey = "g-key", model = "gemini-x").complete("rules", conversation)

        assertEquals("```js\nok\n```", answer)
        val request = http.requests.single()
        assertEquals("POST", request.method)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-x:generateContent", request.url)
        assertEquals("g-key", request.headers["x-goog-api-key"])
        assertContains(request.url, "gemini-x")
        val body = body()
        assertEquals("rules", body["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(listOf("user", "model", "user"), body["contents"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content })
    }

    @Test
    fun `claude uses the messages API with a system prompt`() = runTest {
        reply(body = """{"content":[{"type":"text","text":"answer"}]}""")

        val answer = LlmClients.create(AiProvider.Claude, http, apiKey = "c-key", model = "claude-x").complete("rules", conversation)

        assertEquals("answer", answer)
        val request = http.requests.single()
        assertEquals("https://api.anthropic.com/v1/messages", request.url)
        assertEquals("c-key", request.headers["x-api-key"])
        assertEquals("2023-06-01", request.headers["anthropic-version"])
        val body = body()
        assertEquals("claude-x", body["model"]!!.jsonPrimitive.content)
        assertEquals("rules", body["system"]!!.jsonPrimitive.content)
        assertEquals(listOf("user", "assistant", "user"), body["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content })
    }

    @Test
    fun `openai uses chat completions with the system prompt first`() = runTest {
        reply(body = """{"choices":[{"message":{"role":"assistant","content":"answer"}}]}""")

        val answer = LlmClients.create(AiProvider.OpenAI, http, apiKey = "o-key", model = "gpt-x").complete("rules", conversation)

        assertEquals("answer", answer)
        val request = http.requests.single()
        assertEquals("https://api.openai.com/v1/chat/completions", request.url)
        assertEquals("Bearer o-key", request.headers["Authorization"])
        val roles = body()["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("system", "user", "assistant", "user"), roles)
    }

    @Test
    fun `api errors carry the provider's message`() = runTest {
        reply(status = 401, body = """{"error":{"message":"API key not valid. Please pass a valid API key."}}""")

        val error = assertFailsWith<IOException> {
            LlmClients.create(AiProvider.Gemini, http, apiKey = "bad", model = "m").complete("s", conversation)
        }

        assertContains(error.message!!, "401")
        assertContains(error.message!!, "API key not valid")
    }

    @Test
    fun `models are listed for each provider`() = runTest {
        http.responses = { spec ->
            val body = when {
                "generativelanguage" in spec.url -> """{"models":[{"name":"models/gemini-a","supportedGenerationMethods":["generateContent"]},{"name":"models/embedding-1","supportedGenerationMethods":["embedContent"]}]}"""
                "anthropic" in spec.url -> """{"data":[{"id":"claude-a"},{"id":"claude-b"}]}"""
                else -> """{"data":[{"id":"gpt-a"},{"id":"text-embedding-3"},{"id":"whisper-1"},{"id":"o9"}]}"""
            }
            HttpResponseSpec(spec.url, 200, emptyMap(), "application/json", body)
        }

        assertEquals(listOf("gemini-a"), LlmClients.models(AiProvider.Gemini, http, "k"))
        assertEquals(listOf("claude-a", "claude-b"), LlmClients.models(AiProvider.Claude, http, "k"))
        assertEquals(listOf("gpt-a", "o9"), LlmClients.models(AiProvider.OpenAI, http, "k"))
        assertEquals("k", http.requests.first { "anthropic" in it.url }.headers["x-api-key"])
    }

    @Test
    fun `an empty answer is an error, not an empty script`() = runTest {
        reply(body = """{"candidates":[{"finishReason":"SAFETY"}]}""")

        assertFailsWith<IOException> { LlmClients.create(AiProvider.Gemini, http, "k", "m").complete("s", conversation) }
    }
}
