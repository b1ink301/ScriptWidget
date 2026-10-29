package kr.b1ink.scriptwidget.ui.editor.code

/**
 * Tells outside changes to the code (first load, import) from late echoes of the editor's own edits:
 * the view model's copy can lag a keystroke behind, and adopting it would undo typing and move the cursor.
 */
class ExternalTextSync {
    private val pending = ArrayDeque<String>()

    fun edited(text: String) {
        pending.addLast(text)
        if (pending.size > MAX_PENDING) pending.removeFirst()
    }

    fun shouldAdopt(external: String, current: String): Boolean {
        val echo = pending.indexOf(external)
        if (echo >= 0) {
            repeat(echo + 1) { pending.removeFirst() }
            return false
        }
        if (external == current) return false
        pending.clear()
        return true
    }

    private companion object {
        const val MAX_PENDING = 64
    }
}
