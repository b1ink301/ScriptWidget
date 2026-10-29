package kr.b1ink.scriptwidget.data.symbols

/** Codepoints of the bundled Material Icons font (Apache-2.0), loaded from `/symbols`. */
object MaterialIcons {
    private val codepoints: Map<String, Int> by lazy {
        requireNotNull(javaClass.getResourceAsStream("/symbols/MaterialIcons-Regular.codepoints")) { "codepoints missing" }
            .bufferedReader().useLines { lines ->
                lines.mapNotNull { line ->
                    val (name, hex) = line.trim().split(' ').takeIf { it.size == 2 } ?: return@mapNotNull null
                    hex.toIntOrNull(16)?.let { name to it }
                }.toMap()
            }
    }

    fun codepointOf(name: String): Int? = codepoints[name]
}
