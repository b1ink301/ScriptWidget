package kr.b1ink.scriptwidget.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kr.b1ink.scriptwidget.data.engine.HttpGateway
import kr.b1ink.scriptwidget.data.engine.HttpRequestSpec
import kr.b1ink.scriptwidget.data.engine.HttpResponseSpec
import kr.b1ink.scriptwidget.domain.ai.LlmClient
import kr.b1ink.scriptwidget.domain.ai.LlmMessage
import java.io.IOException

/** The model services a user can bring an API key for. */
enum class AiProvider { Gemini, Claude, OpenAI }

/** [LlmClient]s over the providers' REST APIs (no SDKs), and their model lists. */
object LlmClients {
    /** Models write a whole script before answering, which can take a while. */
    private const val TIMEOUT_SECONDS = 120.0
    private const val MAX_TOKENS = 8_192

    fun create(provider: AiProvider, http: HttpGateway, apiKey: String, model: String): LlmClient = when (provider) {
        AiProvider.Gemini -> Gemini(http, apiKey, model)
        AiProvider.Claude -> Claude(http, apiKey, model)
        AiProvider.OpenAI -> OpenAi(http, apiKey, model)
    }

    /** The models this key can use for chat, as the provider names them. */
    suspend fun models(provider: AiProvider, http: HttpGateway, apiKey: String): List<String> = when (provider) {
        AiProvider.Gemini -> get(http, "$GEMINI/models?pageSize=1000", mapOf("x-goog-api-key" to apiKey))["models"]!!.jsonArray
            .map { it.jsonObject }
            .filter { model -> model["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.content == "generateContent" } == true }
            .map { it["name"]!!.jsonPrimitive.content.removePrefix("models/") }
        AiProvider.Claude -> get(http, "https://api.anthropic.com/v1/models?limit=1000", claudeHeaders(apiKey))["data"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }
        AiProvider.OpenAI -> get(http, "https://api.openai.com/v1/models", mapOf("Authorization" to "Bearer $apiKey"))["data"]!!.jsonArray
            .map { it.jsonObject["id"]!!.jsonPrimitive.content }
            // The list also has embedding, speech, image and moderation models.
            .filter { id -> (id.startsWith("gpt-") || Regex("""^o\d""").containsMatchIn(id)) && NOT_CHAT.none { it in id } }
    }

    private val NOT_CHAT = listOf("embedding", "audio", "tts", "whisper", "image", "realtime", "transcribe", "search", "moderation", "dall-e")
    private const val GEMINI = "https://generativelanguage.googleapis.com/v1beta"

    private class Gemini(private val http: HttpGateway, private val key: String, private val model: String) : LlmClient {
        override suspend fun complete(system: String, messages: List<LlmMessage>): String {
            val body = buildJsonObject {
                putJsonObject("systemInstruction") { putJsonArray("parts") { addJsonObject { put("text", system) } } }
                putJsonArray("contents") {
                    messages.forEach { message ->
                        addJsonObject {
                            put("role", if (message.role == LlmMessage.Role.User) "user" else "model")
                            putJsonArray("parts") { addJsonObject { put("text", message.text) } }
                        }
                    }
                }
            }
            val json = post(http, "$GEMINI/models/$model:generateContent", mapOf("x-goog-api-key" to key), body)
            // Thinking models also return their thoughts as parts; only the answer counts.
            return json["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
                ?.map { it.jsonObject }
                ?.filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true }
                ?.mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                .nonEmpty(json)
        }
    }

    private fun claudeHeaders(key: String) = mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01")

    private class Claude(private val http: HttpGateway, private val key: String, private val model: String) : LlmClient {
        override suspend fun complete(system: String, messages: List<LlmMessage>): String {
            val body = buildJsonObject {
                put("model", model)
                put("max_tokens", MAX_TOKENS)
                put("system", system)
                putJsonArray("messages") { messages.forEach { addJsonObject { put("role", it.role.name.lowercase()); put("content", it.text) } } }
            }
            val json = post(http, "https://api.anthropic.com/v1/messages", claudeHeaders(key), body)
            return json["content"]?.jsonArray
                ?.map { it.jsonObject }
                ?.filter { it["type"]?.jsonPrimitive?.content == "text" }
                ?.mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }
                ?.joinToString("")
                .nonEmpty(json)
        }
    }

    private class OpenAi(private val http: HttpGateway, private val key: String, private val model: String) : LlmClient {
        override suspend fun complete(system: String, messages: List<LlmMessage>): String {
            val body = buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    addJsonObject { put("role", "system"); put("content", system) }
                    messages.forEach { addJsonObject { put("role", it.role.name.lowercase()); put("content", it.text) } }
                }
            }
            val json = post(http, "https://api.openai.com/v1/chat/completions", mapOf("Authorization" to "Bearer $key"), body)
            return json["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                .nonEmpty(json)
        }
    }

    private fun String?.nonEmpty(response: JsonObject): String =
        this?.takeIf { it.isNotBlank() } ?: throw IOException("The model gave no answer: ${response.toString().take(300)}")

    private suspend fun post(http: HttpGateway, url: String, headers: Map<String, String>, body: JsonObject): JsonObject =
        parse(http.loadString(HttpRequestSpec(url, "POST", headers + ("Content-Type" to "application/json"), body.toString(), TIMEOUT_SECONDS)))

    private suspend fun get(http: HttpGateway, url: String, headers: Map<String, String>): JsonObject =
        parse(http.loadString(HttpRequestSpec(url, "GET", headers, timeout = 30.0)))

    private fun parse(response: HttpResponseSpec): JsonObject {
        val json = runCatching { Json.parseToJsonElement(response.body).jsonObject }.getOrNull()
        if (response.statusCode !in 200..299 || json == null) {
            // All three put a readable explanation in {"error": {"message": ...}}.
            val message = json?.get("error")?.let(::errorMessage) ?: response.body.take(300)
            throw IOException("HTTP ${response.statusCode}: $message")
        }
        return json
    }

    private fun errorMessage(error: JsonElement): String? =
        (error as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull ?: runCatching { error.jsonPrimitive.content }.getOrNull()
}
