package kr.b1ink.scriptwidget.data.files

import kr.b1ink.scriptwidget.data.engine.ScriptFileSystem
import java.io.File
import java.io.IOException

/** [ScriptFileSystem] confined to [root]: `Documents`, `Caches` and `tmp` live inside it. */
class SandboxedFileSystem(root: File) : ScriptFileSystem {
    private val root: File = root.apply { mkdirs() }.canonicalFile
    private val documents = dir("Documents")
    private val caches = dir("Caches")
    private val tmp = dir("tmp")
    private val bookmarks = File(root, BOOKMARKS)

    override val documentsDirectory: String = documents.path
    override val cacheDirectory: String = caches.path
    override val temporaryDirectory: String = tmp.path
    override val bookmarksDirectory: String = bookmarks.path

    private fun dir(name: String) = File(root, name).apply { mkdirs() }

    /** Resolves [path] and makes sure it stays inside the sandbox. */
    private fun resolve(path: String): File {
        val raw = File(fromIos(path))
        val file = (if (raw.isAbsolute) raw else File(documents, path)).canonicalFile
        if (file != root && !file.path.startsWith(root.path + File.separator)) {
            throw SecurityException("Path is outside the script sandbox: $path")
        }
        return file
    }

    /**
     * Scripts written for iOS Scriptable sometimes hard-code its folders (iCloud Documents, the app's
     * Documents and Library/Caches); they map to the same folders here. The result is still checked
     * against the sandbox, so "../" can't escape.
     */
    private fun fromIos(path: String): String {
        IOS_FOLDERS.forEach { (pattern, folder) ->
            pattern.find(path)?.let { match ->
                val target = when (folder) {
                    Folder.Documents -> documents
                    Folder.Caches -> caches
                }
                return target.path + path.substring(match.range.last + 1)
            }
        }
        return path
    }

    private enum class Folder { Documents, Caches }

    companion object {
        /** Folder (inside the sandbox root) holding the files of [FileBookmarkStore]. */
        const val BOOKMARKS = "Bookmarks"
        const val TEMP_MAX_AGE_MS = 24 * 60 * 60_000L

        private val IOS_FOLDERS = listOf(
            Regex("""^(/private)?/var/mobile/Library/Mobile Documents/iCloud~dk~simonbs~Scriptable/Documents(?=/|$)""") to Folder.Documents,
            Regex("""^(/private)?/var/mobile/Containers/Data/Application/[0-9A-Fa-f-]+/Documents(?=/|$)""") to Folder.Documents,
            Regex("""^(/private)?/var/mobile/Containers/Data/Application/[0-9A-Fa-f-]+/Library/Caches(?=/|$)""") to Folder.Caches,
        )
    }

    override fun readString(path: String): String? = resolve(path).takeIf { it.isFile }?.readText()

    override fun writeString(path: String, content: String) {
        resolve(path).apply { parentFile?.mkdirs() }.writeText(content)
    }

    override fun readBytes(path: String): ByteArray? = resolve(path).takeIf { it.isFile }?.readBytes()

    override fun writeBytes(path: String, bytes: ByteArray) {
        resolve(path).apply { parentFile?.mkdirs() }.writeBytes(bytes)
    }

    override fun exists(path: String): Boolean = resolve(path).exists()

    override fun isDirectory(path: String): Boolean = resolve(path).isDirectory

    override fun createDirectory(path: String, intermediate: Boolean) {
        val dir = resolve(path)
        val created = if (intermediate) dir.mkdirs() || dir.isDirectory else dir.mkdir() || dir.isDirectory
        if (!created) throw IOException("Could not create directory: $path")
    }

    override fun list(path: String): List<String> = resolve(path).list().orEmpty().sorted()

    override fun remove(path: String) {
        val file = resolve(path)
        if (file == root) throw SecurityException("The script sandbox itself cannot be removed")
        file.deleteRecursively()
    }

    override fun modificationDate(path: String): Long? = resolve(path).takeIf { it.exists() }?.lastModified()

    override fun size(path: String): Long? = resolve(path).takeIf { it.isFile }?.length()

    /**
     * Deletes files in `tmp` (shared files, camera photos, picked documents) not modified for
     * [olderThanMillis], and the folders left empty. `tmp` itself stays.
     */
    fun purgeTemporary(olderThanMillis: Long = TEMP_MAX_AGE_MS, now: Long = System.currentTimeMillis()) {
        val cutoff = now - olderThanMillis
        tmp.walkBottomUp().filter { it != tmp }.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) file.delete()
            else if (file.isDirectory && file.list().isNullOrEmpty()) file.delete()
        }
    }
}
