package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** A value in a JSON response: its dotted [path] (as [DataBinding.Json] reads it) and what it is now. */
data class JsonField(val path: String, val preview: String)

/**
 * "Make a widget from a URL": lists the values in a JSON response so the user can pick some, then
 * lays them out as a designer design, each one a live [DataBinding.Json] value.
 */
object JsonWidget {
    const val MAX_FIELDS = 200
    const val MAX_PREVIEW = 60
    const val REFRESH_MINUTES = 30

    /** Arrays show this many items; the rest usually look the same. */
    private const val ARRAY_ITEMS = 3
    private const val MAX_DEPTH = 8
    private const val GRAY = "#FF8E8E93"

    fun fields(json: JsonElement): List<JsonField> = buildList { collect(json, "", 0) }.take(MAX_FIELDS)

    private fun MutableList<JsonField>.collect(element: JsonElement, path: String, depth: Int) {
        if (size >= MAX_FIELDS || depth > MAX_DEPTH) return
        when (element) {
            is JsonObject -> element.forEach { (key, value) -> collect(value, join(path, key), depth + 1) }
            is JsonArray -> element.take(ARRAY_ITEMS).forEachIndexed { index, value -> collect(value, join(path, index.toString()), depth + 1) }
            JsonNull -> add(JsonField(path, "null"))
            is JsonPrimitive -> add(JsonField(path, shorten(element.content)))
        }
    }

    private fun join(path: String, key: String) = if (path.isEmpty()) key else "$path.$key"

    private fun shorten(text: String) = if (text.length > MAX_PREVIEW) text.take(MAX_PREVIEW) + "…" else text

    /** "main.windSpeed" → "Wind speed": the last name in the path, split into words. */
    fun label(path: String): String {
        val name = path.split('.').lastOrNull { it.isNotEmpty() && it.toIntOrNull() == null } ?: return "Value"
        val words = name.replace(Regex("([a-z0-9])([A-Z])"), "$1 $2").replace('_', ' ').replace('-', ' ').trim().lowercase()
        return words.replaceFirstChar { it.uppercase() }
    }

    fun defaultTitle(url: String): String = Regex("""^https?://([^/?#:]+)""").find(url.trim())?.groupValues?.get(1).orEmpty()

    /** One field is shown big under the title; several become "label … value" rows. */
    fun design(title: String, url: String, fields: List<JsonField>): WidgetDesign {
        val header = DesignText(title, color = GRAY, size = 13f, lineLimit = 1)
        val values = if (fields.size == 1) {
            listOf(
                DesignText("{value}", data = DataBinding.Json(url, fields.single().path), size = 30f, weight = "bold", lineLimit = 1, minimumScaleFactor = 0.5f),
            )
        } else {
            fields.map { field ->
                DesignStack(
                    children = listOf(
                        DesignText(label(field.path), color = GRAY, size = 13f, lineLimit = 1),
                        DesignSpacer(null),
                        DesignText("{value}", data = DataBinding.Json(url, field.path), size = 15f, weight = "semibold", lineLimit = 1),
                    ),
                )
            }
        }
        return WidgetDesign(refreshMinutes = REFRESH_MINUTES, spacing = 4f, children = listOf(header) + values)
    }
}
