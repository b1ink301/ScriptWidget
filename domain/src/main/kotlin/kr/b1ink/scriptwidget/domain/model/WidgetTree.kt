package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Widget tree produced by `Script.setWidget()` in JavaScript (see prelude.js `toJSON()`).
 *
 * Colors are `#AARRGGBB` strings, sizes are dp, paddings are `[top, leading, bottom, trailing]`.
 */
@Serializable
sealed interface WidgetNode

@Serializable
@SerialName("widget")
data class WidgetRoot(
    val bg: String? = null,
    val gradient: Gradient? = null,
    /** Absolute path of a cached image file. */
    val backgroundImage: String? = null,
    val padding: List<Float>? = null,
    val spacing: Float = 0f,
    val url: String? = null,
    /** Android extension: tapping re-runs the script with `args.widgetAction` set to this. */
    val action: String? = null,
    /** Epoch millis of `refreshAfterDate`: the widget should not refresh periodically before it. */
    val refreshAfter: Long? = null,
    val children: List<WidgetNode> = emptyList(),
) : WidgetNode

@Serializable
@SerialName("stack")
data class StackNode(
    /** "h" (default, as in Scriptable) or "v" */
    val dir: String = "h",
    /** Cross-axis alignment: "top" | "center" | "bottom" */
    val align: String = "center",
    val bg: String? = null,
    val gradient: Gradient? = null,
    val backgroundImage: String? = null,
    val padding: List<Float>? = null,
    val spacing: Float = 0f,
    val cornerRadius: Float = 0f,
    val borderColor: String? = null,
    val borderWidth: Float = 0f,
    /** Fixed size `[width, height]`; 0 means "fit". */
    val size: List<Float>? = null,
    val url: String? = null,
    /** Android extension: tapping re-runs the script with `args.widgetAction` set to this. */
    val action: String? = null,
    val children: List<WidgetNode> = emptyList(),
) : WidgetNode

@Serializable
@SerialName("text")
data class TextNode(
    val text: String,
    val color: String? = null,
    val size: Float = 17f,
    /** "regular" | "medium" | "semibold" | "bold" ... */
    val weight: String = "regular",
    val italic: Boolean = false,
    val lineLimit: Int = 0,
    /** "left" | "center" | "right" */
    val align: String = "left",
    val opacity: Float = 1f,
    val url: String? = null,
    /** Android extension: tapping re-runs the script with `args.widgetAction` set to this. */
    val action: String? = null,
    /** Font name: "system", "monospace", "rounded" or a font name such as "Menlo". */
    val font: String = "system",
    /** A font file the script installed in Documents/fonts (`new Font(name)` picks it up). */
    val fontFile: String? = null,
    val shadowColor: String? = null,
    val shadowRadius: Float = 0f,
    /** [x, y] in points. */
    val shadowOffset: List<Float> = emptyList(),
    /** Shrink (down to this factor) instead of truncating when the text doesn't fit. */
    val minimumScaleFactor: Float = 1f,
) : WidgetNode

@Serializable
@SerialName("image")
data class ImageNode(
    /** Absolute path of the image file cached by the runtime. */
    val src: String,
    val width: Int = 0,
    val height: Int = 0,
    /** Display size `[width, height]` in dp; null means natural/fit. */
    val size: List<Float>? = null,
    val cornerRadius: Float = 0f,
    val opacity: Float = 1f,
    val url: String? = null,
    /** Android extension: tapping re-runs the script with `args.widgetAction` set to this. */
    val action: String? = null,
    /** Horizontal alignment inside a vertical container: "left" | "center" | "right". */
    val align: String = "left",
    /** Template tint color (`tintColor`), `#AARRGGBB`. */
    val tint: String? = null,
    /** "fit" (aspect fit, Scriptable's default) | "fill" (aspect fill). */
    val contentMode: String = "fit",
) : WidgetNode

@Serializable
@SerialName("spacer")
data class SpacerNode(
    /** null = flexible spacer */
    val length: Float? = null,
) : WidgetNode

@Serializable
@SerialName("date")
data class DateNode(
    /** Epoch millis */
    val time: Long,
    /** "time" | "date" | "relative" | "timer" */
    val style: String = "time",
    val color: String? = null,
    val size: Float = 17f,
    val weight: String = "regular",
    val align: String = "left",
    /** Android extension: tapping re-runs the script with `args.widgetAction` set to this. */
    val action: String? = null,
) : WidgetNode

/** JSON codec shared by every layer that stores or exchanges widget trees. */
val WidgetJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}

object WidgetTree {
    /** @throws IllegalArgumentException if [json] is malformed or its root is not a `widget`. */
    fun decode(json: String): WidgetRoot =
        WidgetJson.decodeFromString(WidgetNode.serializer(), json) as? WidgetRoot
            ?: throw IllegalArgumentException("Root element must be of type \"widget\"")

    fun encode(root: WidgetRoot): String = WidgetJson.encodeToString(WidgetNode.serializer(), root)
}
