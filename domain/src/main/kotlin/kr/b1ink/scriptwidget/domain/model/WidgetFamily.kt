package kr.b1ink.scriptwidget.domain.model

/** Scriptable's widget families, exposed to scripts as `config.widgetFamily`. */
enum class WidgetFamily(val jsName: String) {
    Small("small"),
    Medium("medium"),
    Large("large");

    companion object {
        private const val WIDE_DP = 250
        private const val TALL_DP = 300

        /** Maps a widget's portrait size in dp to the closest family. */
        fun fromSize(widthDp: Int, heightDp: Int): WidgetFamily = when {
            widthDp < WIDE_DP -> Small
            heightDp >= TALL_DP -> Large
            else -> Medium
        }
    }
}
