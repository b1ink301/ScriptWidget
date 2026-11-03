package kr.b1ink.scriptwidget.data.android

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kr.b1ink.scriptwidget.data.engine.PhotoLibrary
import java.io.File

/** [PhotoLibrary] over MediaStore. Saved images are named `scriptwidget_*`. */
class AndroidPhotoLibrary(context: Context) : PhotoLibrary {
    private val resolver = context.applicationContext.contentResolver
    private val images: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    override fun latest(count: Int, screenshots: Boolean): List<ByteArray> = guarded {
        val (selection, args) = if (screenshots) {
            val column = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.RELATIVE_PATH else MediaStore.MediaColumns.DATA
            "$column LIKE ?" to arrayOf("%Screenshots%")
        } else {
            null to null
        }
        resolver.query(
            images, arrayOf(MediaStore.MediaColumns._ID), selection, args,
            "${MediaStore.MediaColumns.DATE_ADDED} DESC, ${MediaStore.MediaColumns._ID} DESC",
        )?.use { c ->
            buildList {
                while (size < count && c.moveToNext()) {
                    read(ContentUris.withAppendedId(images, c.getLong(0)).toString())?.let(::add)
                }
            }
        }.orEmpty()
    }

    override fun read(handle: String): ByteArray? = guarded {
        resolver.openInputStream(Uri.parse(handle))?.use { it.readBytes() }
    }

    override fun save(bytes: ByteArray) = save(bytes, DEFAULT_PATH)

    fun save(bytes: ByteArray, relativePath: String) {
        guarded {
            val name = "$PREFIX${System.currentTimeMillis()}.png"
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                saveLegacy(bytes, relativePath, name)
                return@guarded
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = requireNotNull(resolver.insert(images, values)) { "Could not save the image" }
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    }

    /**
     * Before Android 10 there is no RELATIVE_PATH: the file is written into the folder itself
     * (WRITE_EXTERNAL_STORAGE) and registered by path. insertImage() would re-encode it as a JPEG
     * and ignore the folder.
     */
    @Suppress("DEPRECATION")
    private fun saveLegacy(bytes: ByteArray, relativePath: String, name: String) {
        val file = File(File(Environment.getExternalStorageDirectory(), relativePath).apply { mkdirs() }, name)
        file.writeBytes(bytes)
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DATA, file.absolutePath)
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
        }
        requireNotNull(resolver.insert(images, values)) { "Could not save the image" }
    }

    /** Removes images saved by this app (used by tests). */
    fun deleteSavedByThisApp() {
        runCatching { resolver.delete(images, "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?", arrayOf("$PREFIX%")) }
    }

    private inline fun <T> guarded(block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        throw IllegalStateException("Photos permission not granted. Allow it in ScriptWidget → 권한.")
    }

    private companion object {
        const val PREFIX = "scriptwidget_"
        const val DEFAULT_PATH = "Pictures/ScriptWidget/"
    }
}
