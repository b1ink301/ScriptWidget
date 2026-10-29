package kr.b1ink.scriptwidget.data.module

import kr.b1ink.scriptwidget.data.engine.ModuleSource
import kr.b1ink.scriptwidget.domain.model.ScriptName
import java.io.File

/** Loads `<name>.js` from the scripts directory; names follow the script name rules. */
class FileModuleSource(private val scriptsDir: File) : ModuleSource {
    override fun load(name: String): String? {
        if (!ScriptName.isValid(name)) return null
        return File(scriptsDir, "$name.js").takeIf { it.isFile }?.readText()
    }
}
