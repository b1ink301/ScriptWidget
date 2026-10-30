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
        val to = path.last() + by
        if (to < 0) return this to path
        return moveTo(path, path.dropLast(1), if (by > 0) to + 1 else to)
    }

    /**
     * Moves the element at [from] into the stack at [toParent] (empty = the widget), before the child
     * now at [index] — counted before the element is taken out. Returns the design and the new path;
     * moves that make no sense (into itself, into a non-stack, out of range) change nothing.
     */
    fun moveTo(from: List<Int>, toParent: List<Int>, index: Int): Pair<WidgetDesign, List<Int>> {
        val element = elementAt(from) ?: return this to from
        if (toParent.size >= from.size && toParent.take(from.size) == from) return this to from
        val targets = if (toParent.isEmpty()) children else (elementAt(toParent) as? DesignStack)?.children ?: return this to from
        if (index !in 0..targets.size) return this to from
        val sameParent = from.dropLast(1) == toParent
        val at = if (sameParent && from.last() < index) index - 1 else index
        if (sameParent && at == from.last()) return this to from
        // Taking the element out shifts a later sibling on the way to the target.
        val parent = toParent.toMutableList()
        val depth = from.size - 1
        if (parent.size > depth && parent.take(depth) == from.dropLast(1) && parent[depth] > from.last()) parent[depth]--
        val moved = remove(from).editChildren(parent) { it.toMutableList().apply { add(at, element) } }
        return moved to parent + at
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

    /** Every file bookmark the design shows, once each: the background first. */
    fun bookmarks(): List<String> {
        fun walk(list: List<DesignElement>): List<String> = list.flatMap {
            when (it) {
                is DesignImage -> listOfNotNull(it.bookmark)
                is DesignStack -> walk(it.children)
                else -> emptyList()
            }
        }
        return (listOfNotNull(background.image) + walk(children)).distinct()
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
    /** A file bookmark name: that picture fills the widget, over the colors. */
    val image: String? = null,
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
    /** null = the system font; "serif", "monospace", or the name of a font file in Documents/fonts. */
    val font: String? = null,
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
    val url: String = "",
    /** A file bookmark name (Settings → File bookmarks); wins over [url]. */
    val bookmark: String? = null,
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

    /**
     * Days to [date] (`yyyy-MM-dd`): "D-12", "D-DAY", "D+3". [yearly] moves a past date to next year
     * (birthdays). A blank [date] reads the widget parameter, "2026-12-25 Christmas", so one design
     * serves many widgets; [Field.Name] is then the text after the date.
     */
    @Serializable
    @SerialName("countdown")
    data class Countdown(val date: String = "", val yearly: Boolean = false, val field: Field = Field.Days) : DataBinding {
        @Serializable
        enum class Field { Days, Name }
    }

    /** How much of the current day, week (from Monday), month or year has passed, 0–100. */
    @Serializable
    @SerialName("progress")
    data class Progress(val period: Period = Period.Year) : DataBinding {
        @Serializable
        enum class Period { Day, Week, Month, Year }
    }

    /** The time ("HH:mm") in another time zone, e.g. "America/New_York". */
    @Serializable
    @SerialName("worldTime")
    data class WorldTime(val zone: String = "America/New_York") : DataBinding
}

private val DesignJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
}
