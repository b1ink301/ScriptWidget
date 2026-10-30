package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.b1ink.scriptwidget.domain.model.ScriptFile
import kr.b1ink.scriptwidget.domain.repository.ScriptDocuments
import java.io.FileNotFoundException
import java.io.IOException

/** Reads and writes documents picked with the system file picker (SAF) or shared by other apps. */
class AndroidScriptDocuments(context: Context) : ScriptDocuments {
    private val resolver = context.applicationContext.contentResolver

    override suspend fun read(uri: String): ScriptFile {
        val bytes = readLimited(uri, MAX_BYTES)
        return ScriptFile(displayName(Uri.parse(uri)), bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF"))
    }

    override suspend fun write(uri: String, content: String) = writeBytes(uri, content.toByteArray(Charsets.UTF_8))

    override suspend fun readBytes(uri: String): ByteArray = readLimited(uri, MAX_BACKUP_BYTES)

    override suspend fun writeBytes(uri: String, content: ByteArray) = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        // "wt" truncates; a few providers only accept "w".
        val stream = runCatching { resolver.openOutputStream(parsed, "wt") }.getOrNull()
            ?: resolver.openOutputStream(parsed, "w")
            ?: throw FileNotFoundException(uri)
        stream.use { it.write(content) }
    }

    private suspend fun readLimited(uri: String, limit: Int): ByteArray = withContext(Dispatchers.IO) {
        resolver.openInputStream(Uri.parse(uri))?.use { input ->
            val buffer = input.readUpTo(limit + 1)
            if (buffer.size > limit) throw IOException("File is larger than ${limit / 1024} KB")
            buffer
        } ?: throw FileNotFoundException(uri)
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/') ?: "imported.js"

    private fun java.io.InputStream.readUpTo(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (out.size() < limit) {
            val n = read(chunk, 0, minOf(chunk.size, limit - out.size()))
            if (n < 0) break
            out.write(chunk, 0, n)
        }
        return out.toByteArray()
    }

    companion object {
        /** Scripts are text; anything bigger is almost certainly not one. */
        const val MAX_BYTES = 1024 * 1024

        /** Zip backups of every script. */
        const val MAX_BACKUP_BYTES = 64 * 1024 * 1024
    }
}
