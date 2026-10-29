package kr.b1ink.scriptwidget.ui.editor.code

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalTextSyncTest {
    @Test
    fun `the first load is adopted`() {
        val sync = ExternalTextSync()
        assertTrue(sync.shouldAdopt(external = "loaded", current = ""))
    }

    @Test
    fun `echoes of our own edits arriving late are ignored`() {
        val sync = ExternalTextSync()
        sync.shouldAdopt(external = "a", current = "")
        sync.edited("ab")
        sync.edited("abc")

        // The view model reports "ab" after the editor already shows "abc": not an outside change.
        assertFalse(sync.shouldAdopt(external = "ab", current = "abc"))
        assertFalse(sync.shouldAdopt(external = "abc", current = "abc"))
    }

    @Test
    fun `a real outside change after edits is adopted`() {
        val sync = ExternalTextSync()
        sync.edited("abc")
        sync.shouldAdopt(external = "abc", current = "abc")

        assertTrue(sync.shouldAdopt(external = "replaced by import", current = "abc"))
    }
}
