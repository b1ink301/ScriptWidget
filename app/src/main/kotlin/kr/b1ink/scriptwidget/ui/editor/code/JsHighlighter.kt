package kr.b1ink.scriptwidget.ui.editor.code

/** Splits JavaScript into colored spans: comments, strings, numbers, keywords and Scriptable API names. */
class JsHighlighter(private val apiNames: Set<String>) {
    enum class Kind { Comment, String, Number, Keyword, Api }

    data class Span(val start: Int, val end: Int, val kind: Kind)

    fun spans(code: String): List<Span> {
        val out = mutableListOf<Span>()
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            when {
                c == '/' && i + 1 < n && code[i + 1] == '/' -> {
                    val end = code.indexOf('\n', i).let { if (it < 0) n else it }
                    out += Span(i, end, Kind.Comment); i = end
                }
                c == '/' && i + 1 < n && code[i + 1] == '*' -> {
                    val close = code.indexOf("*/", i + 2)
                    val end = if (close < 0) n else close + 2
                    out += Span(i, end, Kind.Comment); i = end
                }
                c == '"' || c == '\'' || c == '`' -> {
                    val end = stringEnd(code, i, c)
                    out += Span(i, end, Kind.String); i = end
                }
                c.isDigit() && (i == 0 || !isIdentifierPart(code[i - 1])) -> {
                    var end = i + 1
                    while (end < n && (code[end].isLetterOrDigit() || code[end] == '.' || code[end] == '_')) end++
                    out += Span(i, end, Kind.Number); i = end
                }
                isIdentifierStart(c) -> {
                    var end = i + 1
                    while (end < n && isIdentifierPart(code[end])) end++
                    val word = code.substring(i, end)
                    val afterDot = i > 0 && code[i - 1] == '.'
                    when {
                        !afterDot && word in KEYWORDS -> out += Span(i, end, Kind.Keyword)
                        !afterDot && word in apiNames -> out += Span(i, end, Kind.Api)
                    }
                    i = end
                }
                else -> i++
            }
        }
        return out
    }

    private fun stringEnd(code: String, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < code.length) {
            when (code[i]) {
                '\\' -> i += 2
                quote -> return i + 1
                '\n' -> if (quote != '`') return i else i++
                else -> i++
            }
        }
        return code.length
    }

    companion object {
        val KEYWORDS = setOf(
            "const", "let", "var", "function", "return", "if", "else", "for", "while", "do", "break", "continue",
            "new", "class", "extends", "await", "async", "try", "catch", "finally", "throw", "switch", "case",
            "default", "typeof", "instanceof", "in", "of", "true", "false", "null", "undefined", "this", "delete",
            "void", "yield", "static", "get", "set", "super", "import", "export",
        )

        fun isIdentifierStart(c: Char) = c.isLetter() || c == '_' || c == '$'
        fun isIdentifierPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'
    }
}
