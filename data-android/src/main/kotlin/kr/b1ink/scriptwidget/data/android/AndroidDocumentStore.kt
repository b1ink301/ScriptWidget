package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kr.b1ink.scriptwidget.data.engine.DocumentStore
import kr.b1ink.scriptwidget.data.engine.ScriptFileSystem
import java.io.IOException

/** Reads/writes documents chosen with the system picker (content URIs) and copies imports into the sandbox. */
class AndroidDocumentStore(
    context: Context,
    private val files: ScriptFileSystem,
) : DocumentStore {
    private val resolver = context.applicationContext.contentResolver

    override fun importToSandbox(handle: String): String {
        val uri = Uri.parse(handle)
        val name = displayName(uri)?.replace('/', '_')?.takeIf { it.isNotBlank() && !it.startsWith('.') } ?: "document"
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IOException("Cannot read $handle")
        val path = "${files.temporaryDirectory}/picked/$name"
        files.writeBytes(path, bytes)
        return path
    }

    override fun write(handle: String, bytes: ByteArray) {
        resolver.openOutputStream(Uri.parse(handle), "wt")?.use { it.write(bytes) } ?: throw IOException("Cannot write $handle")
    }

    private fun displayName(uri: Uri): String? =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}
