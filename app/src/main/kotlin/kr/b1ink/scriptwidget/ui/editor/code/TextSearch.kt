package kr.b1ink.scriptwidget.ui.editor.code

/** Find / replace in the editor. Ranges are inclusive character indices. */
object TextSearch {
    fun matches(text: String, query: String, ignoreCase: Boolean = true): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val out = mutableListOf<IntRange>()
        var at = text.indexOf(query, 0, ignoreCase)
        while (at >= 0) {
            out += at until at + query.length
            at = text.indexOf(query, at + query.length, ignoreCase)
        }
        return out
    }

    /** First match starting at or after [from], wrapping to the top. */
    fun next(text: String, query: String, from: Int, ignoreCase: Boolean = true): IntRange? {
        val all = matches(text, query, ignoreCase)
        return all.firstOrNull { it.first >= from } ?: all.firstOrNull()
    }

    /** Last match starting before [before], wrapping to the bottom. */
    fun previous(text: String, query: String, before: Int, ignoreCase: Boolean = true): IntRange? {
        val all = matches(text, query, ignoreCase)
        return all.lastOrNull { it.first < before } ?: all.lastOrNull()
    }

    /** @return the new text and how many matches were replaced. */
    fun replaceAll(text: String, query: String, replacement: String, ignoreCase: Boolean = true): Pair<String, Int> {
        val all = matches(text, query, ignoreCase)
        if (all.isEmpty()) return text to 0
        val out = StringBuilder()
        var last = 0
        for (range in all) {
            out.append(text, last, range.first).append(replacement)
            last = range.last + 1
        }
        out.append(text, last, text.length)
        return out.toString() to all.size
    }
}
