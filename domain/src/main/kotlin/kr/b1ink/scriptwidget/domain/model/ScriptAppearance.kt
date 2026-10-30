package kr.b1ink.scriptwidget.domain.model

/**
 * A script's icon color and glyph, stored like Scriptable does: in a comment header at the top of
 * the file (`// icon-color: deep-blue; icon-glyph: magic;`), so it travels with exports and imports.
 */
data class ScriptAppearance(val color: String, val glyph: String? = null) {

    fun applyTo(code: String): String {
        val lines = code.split('\n')
        val header = lines.takeWhile { it.trimStart().startsWith("//") }
        val index = header.indexOfFirst { ICON_LINE.containsMatchIn(it) }
        return if (index >= 0) {
            (lines.take(index) + line() + lines.drop(index + 1)).joinToString("\n")
        } else {
            "$FIRST_LINE\n$SECOND_LINE\n${line()}\n$code"
        }
    }

    private fun line() = "// icon-color: $color;" + (glyph?.let { " icon-glyph: $it;" } ?: "")

    companion object {
        private const val FIRST_LINE = "// Variables used by Scriptable."
        private const val SECOND_LINE = "// These must be at the very top of the file. Do not edit."
        private val ICON_LINE = Regex("""icon-color:\s*([\w-]+)""")
        private val GLYPH = Regex("""icon-glyph:\s*([\w-]+)""")

        /** The header in the leading comment lines, if any. */
        fun parse(code: String): ScriptAppearance? {
            val header = code.lineSequence().takeWhile { it.trimStart().startsWith("//") }
            val line = header.firstOrNull { ICON_LINE.containsMatchIn(it) } ?: return null
            return ScriptAppearance(ICON_LINE.find(line)!!.groupValues[1], GLYPH.find(line)?.groupValues?.get(1))
        }
    }
}

/** A script in the list. */
data class ScriptSummary(val name: String, val appearance: ScriptAppearance?)

/** Shown under each script in the list. */
data class ScriptActivity(val widgets: Int = 0, val lastRunAt: Long? = null)
