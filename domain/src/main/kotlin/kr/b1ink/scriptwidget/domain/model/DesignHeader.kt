package kr.b1ink.scriptwidget.domain.model

import java.security.MessageDigest

sealed interface DesignRead {
    /** Regular code, not made in the designer. */
    data object Plain : DesignRead

    data class Designed(val design: WidgetDesign) : DesignRead

    /** Made in the designer, but the code was changed by hand since. */
    data class EditedByHand(val design: WidgetDesign) : DesignRead
}

/**
 * Stores a design in the leading comments of its generated script:
 *
 * ```
 * // scriptwidget-design: {"children":[...]}
 * // scriptwidget-design-hash: 3f2a…
 * const w = new ListWidget(); ...
 * ```
 *
 * The hash of the code below tells whether it was edited by hand after it was generated.
 */
object DesignHeader {
    private const val DESIGN = "// scriptwidget-design: "
    private const val HASH = "// scriptwidget-design-hash: "

    /** [body] with the design header; other leading comments of [previous] (the icon header) are kept. */
    fun attach(design: WidgetDesign, body: String, previous: String? = null): String {
        val kept = previous.orEmpty().lineSequence()
            .takeWhile { it.trimStart().startsWith("//") }
            .filterNot { it.startsWith(DESIGN) || it.startsWith(HASH) }
            .toList()
        return (kept + "$DESIGN${design.encode()}" + "$HASH${hash(body)}").joinToString("\n", postfix = "\n") + body
    }

    fun read(code: String): DesignRead {
        val lines = code.split('\n')
        val designLine = lines.indexOfFirst { it.startsWith(DESIGN) }
        val hashLine = designLine + 1
        if (designLine < 0 || lines.getOrNull(hashLine)?.startsWith(HASH) != true) return DesignRead.Plain
        if (lines.take(designLine).any { !it.trimStart().startsWith("//") }) return DesignRead.Plain
        val design = runCatching { WidgetDesign.decode(lines[designLine].removePrefix(DESIGN)) }.getOrNull()
            ?: return DesignRead.Plain
        val body = lines.drop(hashLine + 1).joinToString("\n")
        return if (lines[hashLine].removePrefix(HASH).trim() == hash(body)) DesignRead.Designed(design)
        else DesignRead.EditedByHand(design)
    }

    private fun hash(body: String): String =
        MessageDigest.getInstance("SHA-256").digest(body.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}
