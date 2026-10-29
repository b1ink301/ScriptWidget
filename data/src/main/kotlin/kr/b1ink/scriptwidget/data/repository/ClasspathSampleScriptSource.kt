package kr.b1ink.scriptwidget.data.repository

import kr.b1ink.scriptwidget.domain.repository.SampleScriptSource

/** Samples bundled as classpath resources under `/samples`, listed in `/samples/index.txt`. */
class ClasspathSampleScriptSource : SampleScriptSource {

    override fun load(): Map<String, String> =
        resource("index.txt").lines().map { it.trim() }.filter { it.isNotEmpty() }
            .associateWith { resource("$it.js") }

    private fun resource(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/samples/$name")) { "Missing sample resource: $name" }
            .bufferedReader().use { it.readText() }
}
