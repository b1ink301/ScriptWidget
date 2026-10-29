package kr.b1ink.scriptwidget.data.backup

import kr.b1ink.scriptwidget.domain.repository.ScriptArchiveCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Backups are plain zip files of `.js` scripts, readable with any zip tool. */
class ZipScriptArchiveCodec : ScriptArchiveCodec {

    override fun encode(files: Map<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            files.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }.toByteArray()

    override fun decode(bytes: ByteArray): Map<String, String> {
        if (!isZip(bytes)) throw IOException("Not a zip backup")
        val files = linkedMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                // Only the file name counts: folders are flattened, "../" can't escape.
                val name = entry.name.replace('\\', '/').substringAfterLast('/')
                if (entry.isDirectory || name.isBlank()) continue
                if (files.size >= MAX_ENTRIES) throw IOException("Too many files in the backup")
                files[name] = readLimited(zip).toString(Charsets.UTF_8)
            }
        }
        return files
    }

    private fun readLimited(zip: ZipInputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = zip.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_ENTRY_BYTES) throw IOException("A file in the backup is larger than ${MAX_ENTRY_BYTES / 1024} KB")
        }
        return out.toByteArray()
    }

    private fun isZip(bytes: ByteArray) =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() &&
            (bytes[2].toInt() == 3 && bytes[3].toInt() == 4 || bytes[2].toInt() == 5 && bytes[3].toInt() == 6)

    companion object {
        const val MAX_ENTRY_BYTES = 1024 * 1024
        const val MAX_ENTRIES = 2000
    }
}
