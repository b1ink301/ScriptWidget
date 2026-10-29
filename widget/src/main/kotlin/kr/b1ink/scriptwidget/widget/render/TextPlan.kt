package kr.b1ink.scriptwidget.widget.render

import kr.b1ink.scriptwidget.domain.model.TextNode

/** How a [TextNode] is drawn: as a Glance Text, or as a bitmap when Glance can't express its style. */
sealed interface TextPlan {
    enum class Family { SansSerif, Serif, Monospace }
    enum class Weight { Normal, Medium, Bold }

    data class Native(val family: Family, val weight: Weight) : TextPlan
    data object Bitmap : TextPlan

    companion object {
        private val NATIVE_WEIGHTS = mapOf("regular" to Weight.Normal, "medium" to Weight.Medium, "bold" to Weight.Bold)
        private val MONOSPACE = listOf("monospace", "menlo", "courier", "monaco", "sfmono", "sf mono", "andalemono")
        private val SERIF = listOf("serif", "georgia", "times", "newyork", "new york", "palatino", "baskerville", "didot", "charter")

        fun of(node: TextNode): TextPlan {
            val weight = NATIVE_WEIGHTS[node.weight]
            val shadow = node.shadowColor != null && (node.shadowRadius > 0f || node.shadowOffset.any { it != 0f })
            if (weight == null || shadow || node.minimumScaleFactor < 1f || node.fontFile != null) return Bitmap
            return Native(familyOf(node.font), weight)
        }

        fun familyOf(font: String): Family {
            val name = font.lowercase()
            return when {
                MONOSPACE.any(name::startsWith) -> Family.Monospace
                SERIF.any(name::startsWith) -> Family.Serif
                else -> Family.SansSerif
            }
        }
    }
}
