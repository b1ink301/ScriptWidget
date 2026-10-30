package kr.b1ink.scriptwidget.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.domain.engine.ScriptEngine
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment

@Serializable
data class ApiMember(
    val name: String,
    val kind: Kind,
    val params: List<String> = emptyList(),
    val isStatic: Boolean = false,
    val isAsync: Boolean = false,
) {
    enum class Kind { Method, Property }

    /** e.g. `addText(text)`, `await loadJSON()`, `textColor`. */
    val signature: String
        get() = if (kind == Kind.Property) name else "${if (isAsync) "await " else ""}$name(${params.joinToString(", ")})"
}

@Serializable
data class ApiEntry(
    val name: String,
    val kind: Kind,
    /** Null for objects and functions. */
    val constructorParams: List<String>? = null,
    val members: List<ApiMember> = emptyList(),
    val summary: String = "",
) {
    enum class Kind { Class, Object, Function }

    val docsUrl: String get() = "https://docs.scriptable.app/${name.lowercase()}/"

    fun member(name: String): ApiMember? = members.firstOrNull { it.name == name }
}

/**
 * The script API as the prelude actually defines it: every global below is inspected on QuickJS
 * (constructor and method parameters, properties, statics), then given a short description.
 */
object ApiCatalog {
    val NAMES = listOf(
        "ListWidget", "WidgetStack", "WidgetText", "WidgetDate", "WidgetImage", "WidgetSpacer",
        "Color", "Font", "LinearGradient", "Size", "Point", "Rect", "Image", "Data", "SFSymbol", "DrawContext", "Path",
        "Request", "FileManager", "Keychain", "Location", "Notification", "Device", "UUID", "Timer",
        "DateFormatter", "RelativeDateTimeFormatter", "XMLParser", "Safari", "URLScheme",
        "Alert", "UITable", "UITableRow", "UITableCell", "DatePicker", "QuickLook", "WebView", "DocumentPicker",
        "Pasteboard", "ShareSheet", "Mail", "Message", "CallbackURL", "Speech", "Dictation",
        "Calendar", "CalendarEvent", "RecurrenceRule", "Reminder", "Contact", "ContactsContainer", "ContactsGroup", "Photos",
        "Script", "config", "args", "console", "importModule",
    )

    private val json = Json { ignoreUnknownKeys = true }

    /** [language] "ko" (default) or "en" picks the descriptions. */
    suspend fun load(engine: ScriptEngine, language: String = "ko"): List<ApiEntry> {
        val script = "const __names = ${json.encodeToString(NAMES)};\n" + resource("introspect.js")
        val result = engine.execute(script, ScriptEnvironment(runsInWidget = false), "api-introspection")
        val output = result.logs.lastOrNull()?.message
            ?: error("API introspection failed: ${result.error}")
        return json.decodeFromString<List<ApiEntry>>(output)
            .map { it.copy(summary = ApiDocs.summary(it.name, language), members = it.members.sortedWith(compareBy({ !it.isStatic }, { it.name }))) }
    }

    /** The same entries with their descriptions in [language], e.g. after the app's language changed. */
    fun inLanguage(entries: List<ApiEntry>, language: String): List<ApiEntry> =
        entries.map { it.copy(summary = ApiDocs.summary(it.name, language)) }

    private fun resource(name: String): String =
        requireNotNull(ApiCatalog::class.java.getResourceAsStream("/api/$name")) { "api/$name not found" }
            .bufferedReader().use { it.readText() }
}
