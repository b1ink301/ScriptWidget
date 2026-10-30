package kr.b1ink.scriptwidget.domain.model

sealed interface FontResult {
    /** Saved; scripts and designs use it by [name]. */
    data class Saved(val name: String) : FontResult

    /** Not a TrueType / OpenType font file. */
    data object NotAFont : FontResult

    data class Failed(val message: String) : FontResult
}
