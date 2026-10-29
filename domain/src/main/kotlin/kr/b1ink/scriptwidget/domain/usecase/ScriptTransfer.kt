package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kr.b1ink.scriptwidget.domain.model.ImportOutcome
import kr.b1ink.scriptwidget.domain.model.ScriptAppearance
import kr.b1ink.scriptwidget.domain.model.ScriptFile
import kr.b1ink.scriptwidget.domain.model.ScriptName
import kr.b1ink.scriptwidget.domain.repository.ScriptArchiveCodec
import kr.b1ink.scriptwidget.domain.repository.ScriptDocuments
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository
import kotlin.coroutines.cancellation.CancellationException

/**
 * Imports `.js` files and Scriptable `.scriptable` exports. Each file becomes a new script;
 * existing scripts are never overwritten ("name 2", "name 3", …).
 */
class ImportScriptsUseCase(
    private val scripts: ScriptRepository,
    private val documents: ScriptDocuments,
) {
    suspend operator fun invoke(uris: List<String>): List<ImportOutcome> = uris.map { uri ->
        val file = try {
            documents.read(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@map ImportOutcome.Rejected(uri, ImportOutcome.Reason.Unreadable)
        }
        val (title, raw, icon) = unpack(file)
        // Scriptable keeps the icon next to the code; store it in the header like Scriptable's own files.
        val code = if (icon != null && ScriptAppearance.parse(raw) == null) icon.applyTo(raw) else raw
        if (code.isBlank() || '\u0000' in code) {
            return@map ImportOutcome.Rejected(file.fileName, ImportOutcome.Reason.NotAScript)
        }
        val name = scripts.freeName(scriptNameFor(title))
        scripts.write(name, code)
        ImportOutcome.Imported(name)
    }

    /** `.scriptable` files are JSON with `name`, `script` and `icon`; anything else is the script itself. */
    private fun unpack(file: ScriptFile): Triple<String, String, ScriptAppearance?> {
        val base = file.fileName.substringAfterLast('/').substringAfterLast('\\')
        if (base.endsWith(".scriptable", ignoreCase = true)) {
            val json = runCatching { Json.parseToJsonElement(file.content) as? JsonObject }.getOrNull()
            val script = json?.get("script")?.jsonPrimitive?.contentOrNull
            if (script != null) {
                val name = json["name"]?.jsonPrimitive?.contentOrNull ?: base.substringBeforeLast('.')
                val icon = (json["icon"] as? JsonObject)?.let { icon ->
                    icon["color"]?.jsonPrimitive?.contentOrNull?.let { ScriptAppearance(it, icon["glyph"]?.jsonPrimitive?.contentOrNull) }
                }
                return Triple(name, script, icon)
            }
        }
        val title = EXTENSIONS.firstOrNull { base.endsWith(it, ignoreCase = true) }
            ?.let { base.dropLast(it.length) } ?: base
        return Triple(title, file.content, null)
    }

    companion object {
        private val EXTENSIONS = listOf(".js", ".scriptable", ".txt")

        /** Makes a file title a valid [ScriptName]: no separators, no leading dots, length-limited. */
        fun scriptNameFor(title: String): String {
            val cleaned = title
                .replace('/', '_').replace('\\', '_')
                .filter { !it.isISOControl() }
                .trim()
                .trimStart('.')
                .take(ScriptName.MAX_LENGTH)
                .trim()
            return cleaned.ifEmpty { "imported" }
        }
    }
}

/** Writes a script as a plain `.js` file to a document the user chose. */
class ExportScriptUseCase(
    private val scripts: ScriptRepository,
    private val documents: ScriptDocuments,
) {
    /** @return false when the script doesn't exist. */
    suspend operator fun invoke(name: String, uri: String): Boolean {
        val content = scripts.read(name) ?: return false
        documents.write(uri, content)
        return true
    }

    companion object {
        fun fileNameFor(name: String): String = "$name.js"
    }
}

/** [base] if it's free, otherwise "base 2", "base 3", … (existing scripts are never overwritten). */
internal suspend fun ScriptRepository.freeName(base: String): String {
    if (!exists(base)) return base
    return generateSequence(2) { it + 1 }
        .map { n -> " $n".let { suffix -> base.take(ScriptName.MAX_LENGTH - suffix.length).trimEnd() + suffix } }
        .first { !exists(it) }
}

/** Saves every script into one archive document. @return how many scripts were saved. */
class BackupScriptsUseCase(
    private val scripts: ScriptRepository,
    private val codec: ScriptArchiveCodec,
    private val documents: ScriptDocuments,
) {
    suspend operator fun invoke(uri: String): Int {
        val files = scripts.list().associate { name -> "$name.js" to scripts.read(name).orEmpty() }
        documents.writeBytes(uri, codec.encode(files))
        return files.size
    }

    companion object {
        fun fileName(date: String) = "ScriptWidget-backup-$date.zip"
    }
}

data class RestoreResult(val added: List<String> = emptyList(), val unchanged: List<String> = emptyList())

/** Restores a backup: identical scripts are skipped, conflicting ones are added as "name 2". */
class RestoreScriptsUseCase(
    private val scripts: ScriptRepository,
    private val codec: ScriptArchiveCodec,
    private val documents: ScriptDocuments,
) {
    suspend operator fun invoke(uri: String): RestoreResult {
        val files = codec.decode(documents.readBytes(uri))
        val added = mutableListOf<String>()
        val unchanged = mutableListOf<String>()
        for ((file, content) in files.toSortedMap()) {
            if (!file.endsWith(".js", ignoreCase = true)) continue
            val base = ImportScriptsUseCase.scriptNameFor(file.dropLast(3))
            if (scripts.read(base) == content) {
                unchanged += base
                continue
            }
            val name = scripts.freeName(base)
            scripts.write(name, content)
            added += name
        }
        return RestoreResult(added, unchanged)
    }
}
