package kr.b1ink.scriptwidget.domain.ai

import kr.b1ink.scriptwidget.domain.model.ScriptRunResult

/** One turn of a conversation with a language model. */
data class LlmMessage(val role: Role, val text: String) {
    enum class Role { User, Assistant }
}

/** A chat model behind an API key (Gemini, Claude, OpenAI, ...). */
interface LlmClient {
    /** The model's answer; throws on network, authentication or quota errors. */
    suspend fun complete(system: String, messages: List<LlmMessage>): String
}

/**
 * "Make a widget with AI": the instructions a model needs to write a ScriptWidget script, and
 * reading the script back out of its answer. Used both for the copy-the-prompt flow (any chat app)
 * and for generating in the app with an API key.
 */
object AiWidget {
    private const val MAX_NAME = 40

    /** The rules and the API list; the system prompt when the app talks to the model itself. */
    fun instructions(apiReference: String): String = """
        |You write widget scripts for ScriptWidget, an Android app that runs Scriptable (iOS) JavaScript.
        |
        |Rules:
        |- Plain JavaScript run by QuickJS; top-level await works. No import/require, npm, DOM or fetch: use Request.
        |- Build a ListWidget, then call Script.setWidget(widget) and Script.complete().
        |- The widget runs in the background (config.runsInWidget is true): never show Alert, UITable, WebView or other UI then.
        |- Use only the APIs listed below. They follow Scriptable's names and behavior.
        |- Network: new Request(url) with await loadJSON(), loadString() or loadImage(). Use public APIs that need no key unless the request gives one.
        |- Catch failures (try/catch) and show a short message in the widget instead of throwing.
        |- config.widgetFamily is "small", "medium" or "large"; make the content fit it.
        |- Set widget.refreshAfterDate when the data changes on a schedule.
        |- Fonts: Font.systemFont(size), Font.boldSystemFont(size). Colors: new Color("#RRGGBB"), Color.dynamic(light, dark).
        |- Texts shown in the widget are in the language of the request.
        |
        |Reply with exactly one ```javascript code block holding the whole script, and nothing after it.
        |
        |Available APIs:
        |$apiReference
    """.trimMargin()

    /** Everything in one message, to paste into any AI chat app. */
    fun prompt(request: String, apiReference: String): String =
        instructions(apiReference) + "\n\nRequest:\n" + request.trim()

    /** Sent back when the script failed, with the error from the real engine. */
    fun fixPrompt(code: String, error: String): String = """
        |The script failed when ScriptWidget ran it as a medium widget:
        |$error
        |
        |Fix it and reply with the whole corrected script in one ```javascript block.
        |
        |```javascript
        |$code
        |```
    """.trimMargin()

    const val ASK_FOR_CODE = "Reply with the whole script in one ```javascript code block."

    private val FENCE = Regex("""```[ \t]*([A-Za-z]*)[ \t]*\r?\n(.*?)```""", RegexOption.DOT_MATCHES_ALL)

    /** The script in a model's answer: the longest fenced block (preferring JavaScript), or the answer itself if it is code. */
    fun code(answer: String): String? {
        val blocks = FENCE.findAll(answer).map { it.groupValues[1].lowercase() to it.groupValues[2].trim() }.filter { it.second.isNotEmpty() }.toList()
        if (blocks.isNotEmpty()) {
            val scripts = blocks.filter { it.first in setOf("javascript", "js") }.ifEmpty { blocks }
            return scripts.maxBy { it.second.length }.second
        }
        val plain = answer.trim()
        return plain.takeIf { looksLikeScript(it) }
    }

    private fun looksLikeScript(text: String) = "ListWidget" in text || "Script.setWidget" in text

    /** [base], or "[base] 2", "[base] 3", ... — the first that isn't [taken]. */
    fun uniqueName(base: String, taken: Set<String>): String =
        generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base $it" }.first { it !in taken }

    /** A script name from the request: its first words, without characters names can't have. */
    fun name(request: String): String {
        val cleaned = request.trim().replace(Regex("""[/\\\r\n]+"""), "-").trimStart('.', '-', ' ')
        return cleaned.take(MAX_NAME).trim().ifEmpty { "AI widget" }
    }
}

/** What generating came to: the last script, whether it ran, and how many tries it took. */
data class AiWidgetResult(
    val code: String?,
    val attempts: Int,
    /** Null when the script drew a widget. */
    val error: String?,
    val run: ScriptRunResult? = null,
)

/**
 * Asks the model for a script, runs it on the real engine, and sends errors back for a fix until it
 * draws a widget or [MAX_ATTEMPTS] is reached.
 */
class GenerateWidgetUseCase(
    private val llm: LlmClient,
    private val apiReference: String,
    private val run: suspend (code: String) -> ScriptRunResult,
) {
    suspend operator fun invoke(request: String, onAttempt: (Int) -> Unit = {}): AiWidgetResult {
        val system = AiWidget.instructions(apiReference)
        val messages = mutableListOf(LlmMessage(LlmMessage.Role.User, request.trim()))
        var code: String? = null
        var error: String? = null
        var last: ScriptRunResult? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            onAttempt(attempt)
            val answer = llm.complete(system, messages)
            messages += LlmMessage(LlmMessage.Role.Assistant, answer)
            val script = AiWidget.code(answer)
            if (script == null) {
                error = NO_CODE
                messages += LlmMessage(LlmMessage.Role.User, AiWidget.ASK_FOR_CODE)
                continue
            }
            code = script
            val result = run(script)
            last = result
            if (result.error == null && result.widget != null) return AiWidgetResult(script, attempt, null, result)
            error = result.error ?: NO_WIDGET
            messages += LlmMessage(LlmMessage.Role.User, AiWidget.fixPrompt(script, error))
        }
        return AiWidgetResult(code, MAX_ATTEMPTS, error, last)
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val NO_CODE = "The answer had no script in it."
        const val NO_WIDGET = "The script finished without calling Script.setWidget()."
    }
}
