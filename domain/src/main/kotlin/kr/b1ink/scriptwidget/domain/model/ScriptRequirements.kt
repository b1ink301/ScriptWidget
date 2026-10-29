package kr.b1ink.scriptwidget.domain.model

/** What a script needs, guessed from its source (cheap, no parsing). */
object ScriptRequirements {
    private val NETWORK = Regex("""\bRequest\s*\(|\bWebView\b|\bimportModule\s*\(""")
    private val COMMENTS = Regex("""//[^\n]*|/\*[\s\S]*?\*/""")

    /** Whether the script loads anything from the web (modules may, too). */
    fun usesNetwork(source: String): Boolean = NETWORK.containsMatchIn(source.replace(COMMENTS, ""))
}
