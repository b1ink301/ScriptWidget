package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A widget built in the designer. It is turned into a Scriptable script (see `DesignScriptGenerator`)
 * and stored with it in a header comment, so the script can be reopened in the designer.
 *
 * Elements are addressed by their path: child indexes from the root, e.g. `[1, 0]`.
 */
@Serializable
data class WidgetDesign(
    val version: Int = 1,
    val background: DesignBackground = DesignBackground(),
    /** Padding on every side, dp; null keeps the widget's default. */
    val padding: Float? = null,
    val spacing: Float = 0f,
    /** Opened when the widget is tapped. */
    val url: String? = null,
    /** Asks for the next refresh this many minutes later (`refreshAfterDate`). */
    val refreshMinutes: Int? = null,
    val children: List<DesignElement> = emptyList(),
) {
    fun elementAt(path: List<Int>): DesignElement? {
        var list = children
        var element: DesignElement? = null
        for (index in path) {
            element = list.getOrNull(index) ?: return null
            list = (element as? DesignStack)?.children ?: emptyList()
        }
        return element
    }

    /**
     * Adds [element] inside the selected stack, after the selected element, or at the end.
     * Returns the new design and the path of the added element.
     */
    fun add(element: DesignElement, selected: List<Int>?): Pair<WidgetDesign, List<Int>> {
        val target = selected?.takeIf { elementAt(it) != null }
        val (parent, index) = when {
            target == null -> emptyList<Int>() to children.size
            elementAt(target) is DesignStack -> target to (elementAt(target) as DesignStack).children.size
            else -> target.dropLast(1) to target.last() + 1
        }
        return editChildren(parent) { it.toMutableList().apply { add(index, element) } } to parent + index
    }

    fun update(path: List<Int>, change: (DesignElement) -> DesignElement): WidgetDesign =
        editChildren(path.dropLast(1)) { list -> list.mapIndexed { i, e -> if (i == path.last()) change(e) else e } }

    fun remove(path: List<Int>): WidgetDesign =
        editChildren(path.dropLast(1)) { list -> list.filterIndexed { i, _ -> i != path.last() } }

    /** Moves an element [by] places among its siblings; returns the design and its new path. */
    fun move(path: List<Int>, by: Int): Pair<WidgetDesign, List<Int>> {
        val parent = path.dropLast(1)
        val siblings = if (parent.isEmpty()) children else (elementAt(parent) as? DesignStack)?.children ?: return this to path
        val from = path.last()
        val to = from + by
        if (from !in siblings.indices || to !in siblings.indices) return this to path
        val moved = editChildren(parent) { it.toMutableList().apply { add(to, removeAt(from)) } }
        return moved to parent + to
    }

    fun wrapInStack(path: List<Int>, dir: String): Pair<WidgetDesign, List<Int>> {
        val element = elementAt(path) ?: return this to path
        return update(path) { DesignStack(dir = dir, children = listOf(element)) } to path
    }

    /** Every data source the design reads, once each, in order. */
    fun bindings(): List<DataBinding> {
        fun walk(list: List<DesignElement>): List<DataBinding> = list.flatMap {
            when (it) {
                is DesignText -> listOfNotNull(it.data)
                is DesignStack -> walk(it.children)
                else -> emptyList()
            }
        }
        return walk(children).distinct()
    }

    fun encode(): String = DesignJson.encodeToString(serializer(), this)

    private fun editChildren(parent: List<Int>, change: (List<DesignElement>) -> List<DesignElement>): WidgetDesign {
        fun edit(list: List<DesignElement>, rest: List<Int>): List<DesignElement> =
            if (rest.isEmpty()) {
                change(list)
            } else {
                list.mapIndexed { i, e ->
                    if (i == rest.first() && e is DesignStack) e.copy(children = edit(e.children, rest.drop(1))) else e
                }
            }
        return copy(children = edit(children, parent))
    }

    companion object {
        /** @throws IllegalArgumentException when [json] is not a design. */
        fun decode(json: String): WidgetDesign = DesignJson.decodeFromString(serializer(), json)
    }
}

@Serializable
data class DesignBackground(
    val color: String? = null,
    /** Top-to-bottom colors; wins over [color]. */
    val gradient: List<String>? = null,
)

@Serializable
sealed interface DesignElement

@Serializable
@SerialName("text")
data class DesignText(
    /** `{value}` is replaced by [data]'s value. */
    val text: String,
    val data: DataBinding? = null,
    val color: String? = null,
    val size: Float = 17f,
    val weight: String = "regular",
    val align: String = "left",
    val lineLimit: Int = 0,
    val minimumScaleFactor: Float = 1f,
    val url: String? = null,
) : DesignElement

/** The current time or date, kept up to date by the launcher. */
@Serializable
@SerialName("date")
data class DesignDate(
    /** "time" | "date" | "relative" | "timer" */
    val style: String = "time",
    val color: String? = null,
    val size: Float = 17f,
    val weight: String = "regular",
    val align: String = "left",
) : DesignElement

@Serializable
@SerialName("symbol")
data class DesignSymbol(
    val name: String,
    val color: String? = null,
    val size: Float = 24f,
) : DesignElement

@Serializable
@SerialName("image")
data class DesignImage(
    val url: String,
    val size: Float? = null,
    val cornerRadius: Float = 0f,
) : DesignElement

@Serializable
@SerialName("spacer")
data class DesignSpacer(
    /** null = flexible */
    val length: Float? = null,
) : DesignElement

@Serializable
@SerialName("stack")
data class DesignStack(
    /** "h" | "v" */
    val dir: String = "h",
    /** Cross-axis alignment of a horizontal stack: "top" | "center" | "bottom". */
    val align: String = "center",
    val spacing: Float = 0f,
    val padding: Float? = null,
    val bg: String? = null,
    val cornerRadius: Float = 0f,
    val children: List<DesignElement> = emptyList(),
) : DesignElement

/** Live data shown by a text element. */
@Serializable
sealed interface DataBinding {
    /** Today's date or time in a custom pattern, e.g. "M월 d일 EEEE". */
    @Serializable
    @SerialName("dateText")
    data class DateText(val format: String = "M월 d일 EEEE") : DataBinding

    /** Battery level in percent. */
    @Serializable
    @SerialName("battery")
    data object Battery : DataBinding

    @Serializable
    @SerialName("nextEvent")
    data class NextEvent(val field: Field = Field.Title) : DataBinding {
        @Serializable
        enum class Field { Title, Time }
    }

    @Serializable
    @SerialName("reminders")
    data class Reminders(val field: Field = Field.Count) : DataBinding {
        @Serializable
        enum class Field { Count, Next }
    }

    /** Current weather at the device location (Open-Meteo, no API key). */
    @Serializable
    @SerialName("weather")
    data class Weather(val field: Field = Field.Temperature) : DataBinding {
        @Serializable
        enum class Field { Temperature, Condition }
    }

    /** A value from JSON at [url], at a dotted [path] such as `data.0.name`. */
    @Serializable
    @SerialName("json")
    data class Json(val url: String, val path: String) : DataBinding

    /** The widget parameter set on the home screen. */
    @Serializable
    @SerialName("parameter")
    data class Parameter(val fallback: String = "") : DataBinding
}

private val DesignJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}
