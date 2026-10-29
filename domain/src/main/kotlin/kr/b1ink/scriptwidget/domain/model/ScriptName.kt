package kr.b1ink.scriptwidget.domain.model

/** Script names double as file names, so they must be safe path segments. */
object ScriptName {
    const val MAX_LENGTH = 64

    fun isValid(name: String): Boolean =
        name.isNotBlank() &&
            name == name.trim() &&
            name.length <= MAX_LENGTH &&
            !name.startsWith('.') &&
            '/' !in name &&
            '\\' !in name
}
