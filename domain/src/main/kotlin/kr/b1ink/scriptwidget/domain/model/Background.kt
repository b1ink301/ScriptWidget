package kr.b1ink.scriptwidget.domain.model

import kotlinx.serialization.Serializable

/**
 * Scriptable's `LinearGradient`. Points are unit coordinates of the element (0,0 = top-left).
 */
@Serializable
data class Gradient(
    val colors: List<String>,
    val locations: List<Float>? = null,
    val start: List<Float> = listOf(0f, 0f),
    val end: List<Float> = listOf(0f, 1f),
) {
    /** Color stop positions; spread evenly when [locations] is missing or doesn't match [colors]. */
    fun stops(): List<Float> =
        locations?.takeIf { it.size == colors.size }
            ?: colors.indices.map { if (colors.size == 1) 0f else it.toFloat() / (colors.size - 1) }
}

/** The effective background of a widget or stack: image > gradient > color. */
sealed interface Background {
    data object None : Background
    data class Solid(val color: String) : Background
    data class Linear(val gradient: Gradient) : Background
    data class Image(val src: String) : Background

    companion object {
        fun of(color: String?, gradient: Gradient?, image: String?): Background = when {
            image != null -> Image(image)
            gradient != null && gradient.colors.size >= 2 -> Linear(gradient)
            color != null -> Solid(color)
            else -> None
        }
    }
}

val WidgetRoot.background: Background get() = Background.of(bg, gradient, backgroundImage)
val StackNode.background: Background get() = Background.of(bg, gradient, backgroundImage)
