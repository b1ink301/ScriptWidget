package kr.b1ink.scriptwidget.data.engine

/** The Scriptable-compatible JS API, bundled as classpath resources under `/prelude`. */
object Prelude {
    fun load(): String =
        resource("index.txt").lines().map { it.trim() }.filter { it.isNotEmpty() }
            .joinToString(separator = "\n;\n") { resource(it) }

    private fun resource(name: String): String =
        requireNotNull(Prelude::class.java.getResourceAsStream("/prelude/$name")) { "prelude/$name not found" }
            .bufferedReader().use { it.readText() }
}
