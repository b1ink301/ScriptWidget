package kr.b1ink.scriptwidget.domain.repository

/** Bundled sample scripts: name → content. */
fun interface SampleScriptSource {
    fun load(): Map<String, String>
}
