package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kr.b1ink.scriptwidget.domain.model.CatalogValidator
import kr.b1ink.scriptwidget.domain.repository.ScriptArchiveCodec
import kr.b1ink.scriptwidget.domain.repository.ScriptDocuments
import kr.b1ink.scriptwidget.domain.repository.ScriptRepository

/**
 * Packs a script for a gallery catalog: a zip with `<id>.js` and `entry.json`, an `index.json`
 * entry whose permissions are read from the code, so it passes the catalog checks as it is.
 */
class ExportForGalleryUseCase(
    private val scripts: ScriptRepository,
    private val codec: ScriptArchiveCodec,
    private val documents: ScriptDocuments,
) {
    /** @return false when there is no script [name]. */
    suspend operator fun invoke(name: String, uri: String): Boolean {
        val code = scripts.read(name) ?: return false
        val id = idFor(name)
        val entry = JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "name" to JsonPrimitive(name),
                "title" to JsonPrimitive(name),
                "category" to JsonPrimitive(CATEGORY),
                "description" to JsonPrimitive(DESCRIPTION),
                "script" to JsonPrimitive("$id.js"),
                "version" to JsonPrimitive(1),
                "sizes" to JsonArray(listOf("small", "medium", "large").map(::JsonPrimitive)),
                "permissions" to JsonArray(CatalogValidator.usedPermissions(code).sorted().map(::JsonPrimitive)),
            ),
        )
        documents.writeBytes(uri, codec.encode(mapOf("$id.js" to code, "entry.json" to PrettyJson.encodeToString(JsonObject.serializer(), entry))))
        return true
    }

    companion object {
        private const val CATEGORY = "Designs"
        private const val DESCRIPTION = "Made with the ScriptWidget designer."
        private val PrettyJson = Json { prettyPrint = true }

        fun fileName(name: String) = "${idFor(name)}-gallery.zip"

        /** A file-safe catalog id: the name's latin letters and digits, else a short hash of it. */
        fun idFor(name: String): String {
            val slug = name.lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
            return slug.ifEmpty { "design_%06x".format(name.hashCode() and 0xFFFFFF) }
        }
    }
}
