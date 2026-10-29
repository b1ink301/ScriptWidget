package kr.b1ink.scriptwidget.ui.editor.code

import kr.b1ink.scriptwidget.data.api.ApiEntry
import kr.b1ink.scriptwidget.data.api.ApiMember
import kr.b1ink.scriptwidget.ui.editor.code.JsHighlighter.Companion.isIdentifierPart

/** Suggestions for the word being typed: API members after `Class.` / `variable.`, otherwise globals and names. */
class CodeCompletion(private val catalog: List<ApiEntry>) {
    data class Suggestion(
        val label: String,
        /** Text that replaces the typed prefix. */
        val insert: String,
        /** Cursor position inside [insert] after inserting (e.g. between the parentheses). */
        val cursorOffset: Int = insert.length,
        val detail: String? = null,
    )

    data class Result(val replaceFrom: Int, val items: List<Suggestion>)

    private val byName = catalog.associateBy { it.name }

    fun suggest(code: String, cursor: Int, limit: Int = 12): Result {
        val end = cursor.coerceIn(0, code.length)
        var start = end
        while (start > 0 && isIdentifierPart(code[start - 1])) start--
        val prefix = code.substring(start, end)
        if (prefix.isEmpty() || insideStringOrComment(code, start)) return Result(start, emptyList())

        val candidates: List<Suggestion> = if (start > 0 && code[start - 1] == '.') {
            var r = start - 1
            while (r > 0 && isIdentifierPart(code[r - 1])) r--
            membersOf(code, code.substring(r, start - 1))
        } else {
            globals(code)
        }
        val items = candidates
            // JavaScript names are case-sensitive, and so is matching.
            .filter { it.label.startsWith(prefix) && it.label != prefix }
            .distinctBy { it.label }
            // Shorter names first: boldSystemFont before boldMonospacedSystemFont.
            .sortedWith(compareBy({ it.label.length }, { it.label }))
            .take(limit)
        return Result(start, items)
    }

    private fun membersOf(code: String, receiver: String): List<Suggestion> {
        byName[receiver]?.let { entry ->
            val statics = if (entry.kind == ApiEntry.Kind.Class) entry.members.filter { it.isStatic } else entry.members
            return statics.map(::suggestion)
        }
        val type = Regex("""\b${Regex.escape(receiver)}\s*=\s*(?:await\s+)?new\s+([A-Za-z_$][\w$]*)""").find(code)?.groupValues?.get(1)
        byName[type]?.let { entry -> return entry.members.filter { !it.isStatic }.map(::suggestion) }
        return catalog.flatMap { e -> e.members.filter { !it.isStatic || e.kind != ApiEntry.Kind.Class } }.map(::suggestion)
    }

    private fun globals(code: String): List<Suggestion> {
        val declared = DECLARATION.findAll(code).map { it.groupValues[1] }.toSet()
        return catalog.map { Suggestion(it.name, it.name, detail = it.summary.takeIf(String::isNotEmpty)) } +
            JsHighlighter.KEYWORDS.map { Suggestion(it, it) } +
            declared.map { Suggestion(it, it) }
    }

    private fun suggestion(member: ApiMember): Suggestion =
        if (member.kind == ApiMember.Kind.Property) {
            Suggestion(member.name, member.name, detail = member.signature)
        } else {
            val insert = "${member.name}()"
            Suggestion(member.name, insert, cursorOffset = if (member.params.isEmpty()) insert.length else insert.length - 1, detail = member.signature)
        }

    /** Rough scan: is [index] inside a string literal or a comment on the way from the start of the code? */
    private fun insideStringOrComment(code: String, index: Int): Boolean =
        JsHighlighter(emptySet()).spans(code.substring(0, index))
            .any { (it.kind == JsHighlighter.Kind.String || it.kind == JsHighlighter.Kind.Comment) && it.end >= index && unterminated(code, it) }

    private fun unterminated(code: String, span: JsHighlighter.Span): Boolean {
        val text = code.substring(span.start, span.end)
        return when (span.kind) {
            JsHighlighter.Kind.Comment -> text.startsWith("//") || !text.endsWith("*/")
            else -> text.length < 2 || text.last() != text.first() || text.endsWith("\\" + text.first())
        }
    }

    private companion object {
        val DECLARATION = Regex("""\b(?:const|let|var|function|class)\s+([A-Za-z_$][\w$]*)""")
    }
}
