package kr.b1ink.scriptwidget.domain.usecase

import kr.b1ink.scriptwidget.domain.model.FontResult
import kr.b1ink.scriptwidget.domain.repository.FontRepository
import kr.b1ink.scriptwidget.domain.repository.ScriptDocuments
import kotlin.coroutines.cancellation.CancellationException

/** Copies a font file the user picked into the fonts folder, named after the picked file. */
class AddFontUseCase(private val fonts: FontRepository, private val documents: ScriptDocuments) {
    /** @param fileName the picked file's display name, e.g. "Pixel Sans.ttf". */
    suspend operator fun invoke(uri: String, fileName: String): FontResult {
        val bytes = try {
            documents.readBytes(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return FontResult.Failed(e.message ?: e.toString())
        }
        val extension = extensionOf(bytes) ?: return FontResult.NotAFont
        val base = fileName.substringAfterLast('/').substringAfterLast('\\')
        val name = base.substringBeforeLast('.').takeIf { base.lowercase().endsWith(".ttf") || base.lowercase().endsWith(".otf") } ?: base
        if (name.isBlank() || name.startsWith(".")) return FontResult.NotAFont
        fonts.save("$name$extension", bytes)
        return FontResult.Saved(name)
    }

    /** ".ttf" or ".otf" judged from the file's first bytes (sfnt version), or null. */
    private fun extensionOf(bytes: ByteArray): String? {
        if (bytes.size < 4) return null
        val head = bytes.copyOfRange(0, 4)
        return when {
            head.contentEquals(byteArrayOf(0, 1, 0, 0)) || head.decodeToString() == "true" -> ".ttf"
            head.decodeToString() == "OTTO" -> ".otf"
            else -> null
        }
    }
}
