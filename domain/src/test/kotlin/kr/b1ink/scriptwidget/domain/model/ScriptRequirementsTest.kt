package kr.b1ink.scriptwidget.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptRequirementsTest {
    @Test
    fun `scripts that load from the web use the network`() {
        listOf(
            "await new Request(url).loadJSON()",
            "const img = await Request ('x').loadImage()",
            "const wv = new WebView(); await wv.loadURL(u)",
            "const m = importModule('lib')", // a module may fetch
        ).forEach { assertTrue(ScriptRequirements.usesNetwork(it), it) }
    }

    @Test
    fun `local-only scripts don't`() {
        listOf(
            "const w = new ListWidget(); w.addText(new Date().toString())",
            "// new Request(url) is mentioned only in a comment",
            "const fm = FileManager.local(); fm.readString(p)",
        ).forEach { assertFalse(ScriptRequirements.usesNetwork(it), it) }
    }
}
