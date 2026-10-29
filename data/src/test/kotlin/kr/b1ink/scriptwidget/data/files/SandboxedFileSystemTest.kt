package kr.b1ink.scriptwidget.data.files

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SandboxedFileSystemTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val root by lazy { tmp.newFolder("sandbox") }
    private val fs by lazy { SandboxedFileSystem(root) }

    @Test
    fun `standard directories live inside the sandbox and exist`() {
        listOf(fs.documentsDirectory, fs.cacheDirectory, fs.temporaryDirectory).forEach {
            assertTrue(it.startsWith(root.canonicalPath), it)
            assertTrue(File(it).isDirectory, it)
        }
        assertEquals(3, setOf(fs.documentsDirectory, fs.cacheDirectory, fs.temporaryDirectory).size)
    }

    @Test
    fun `writes and reads strings and bytes`() {
        val path = "${fs.documentsDirectory}/notes/today.txt"
        fs.createDirectory("${fs.documentsDirectory}/notes", intermediate = false)
        fs.writeString(path, "hello")
        fs.writeBytes("${fs.documentsDirectory}/b.bin", byteArrayOf(1, 2))

        assertEquals("hello", fs.readString(path))
        assertContentEquals(byteArrayOf(1, 2), fs.readBytes("${fs.documentsDirectory}/b.bin"))
        assertTrue(fs.exists(path))
        assertFalse(fs.isDirectory(path))
        assertTrue(fs.isDirectory("${fs.documentsDirectory}/notes"))
        assertNotNull(fs.modificationDate(path))
    }

    @Test
    fun `missing files read as null`() {
        assertNull(fs.readString("${fs.documentsDirectory}/nope.txt"))
        assertNull(fs.readBytes("${fs.documentsDirectory}/nope.bin"))
        assertNull(fs.modificationDate("${fs.documentsDirectory}/nope.bin"))
    }

    @Test
    fun `relative paths resolve against documents`() {
        fs.writeString("rel.txt", "x")

        assertEquals("x", fs.readString("${fs.documentsDirectory}/rel.txt"))
    }

    @Test
    fun `lists directory contents sorted and removes recursively`() {
        fs.writeString("b.txt", "")
        fs.writeString("a.txt", "")
        fs.createDirectory("${fs.documentsDirectory}/x/y", intermediate = true)
        fs.writeString("${fs.documentsDirectory}/x/y/z.txt", "")

        assertEquals(listOf("a.txt", "b.txt", "x"), fs.list(fs.documentsDirectory))

        fs.remove("${fs.documentsDirectory}/x")
        assertFalse(fs.exists("${fs.documentsDirectory}/x/y/z.txt"))
    }

    @Test
    fun `createDirectory without intermediate fails when the parent is missing`() {
        assertFailsWith<java.io.IOException> { fs.createDirectory("${fs.documentsDirectory}/p/q", intermediate = false) }
    }

    @Test
    fun `paths outside the sandbox are rejected`() {
        val outside = tmp.newFile("secret.txt").apply { writeText("s") }
        listOf(
            outside.absolutePath,
            "${fs.documentsDirectory}/../../secret.txt",
            "../../secret.txt",
            "/etc/hosts",
        ).forEach { path ->
            assertFailsWith<SecurityException>(path) { fs.readString(path) }
            assertFailsWith<SecurityException>(path) { fs.writeString(path, "x") }
        }
        assertEquals("s", outside.readText())
    }

    @Test
    fun `the sandbox root itself cannot be removed`() {
        assertFailsWith<SecurityException> { fs.remove(root.absolutePath) }
        assertTrue(root.exists())
    }

    // --- iOS Scriptable paths written into scripts ---

    @Test
    fun `Scriptable's iCloud documents path maps to Documents`() {
        fs.writeString("${fs.documentsDirectory}/PX3/a.txt", "hi")

        assertEquals("hi", fs.readString("/var/mobile/Library/Mobile Documents/iCloud~dk~simonbs~Scriptable/Documents/PX3/a.txt"))
        assertTrue(fs.exists("/private/var/mobile/Library/Mobile Documents/iCloud~dk~simonbs~Scriptable/Documents/PX3"))
    }

    @Test
    fun `Scriptable's local app documents and caches map too`() {
        val app = "/var/mobile/Containers/Data/Application/0A1B2C3D-0000-4000-8000-123456789ABC"
        fs.writeString("$app/Documents/x.json", "{}")
        fs.writeString("$app/Library/Caches/c.txt", "cached")

        assertEquals("{}", fs.readString("${fs.documentsDirectory}/x.json"))
        assertEquals("cached", fs.readString("${fs.cacheDirectory}/c.txt"))
    }

    @Test
    fun `other absolute paths are still refused`() {
        assertFailsWith<SecurityException> { fs.readString("/var/mobile/Library/Preferences/x.plist") }
        assertFailsWith<SecurityException> { fs.readString("/var/mobile/Library/Mobile Documents/iCloud~dk~simonbs~Scriptable/Documents/../../../etc/passwd") }
    }

    // --- storage: temporary files don't pile up ---

    @Test
    fun `old temporary files are purged, recent ones kept`() {
        val root = tmp.newFolder("purge")
        val fs = SandboxedFileSystem(root)
        val now = 10L * 24 * 3_600_000
        val old = java.io.File(fs.temporaryDirectory, "share/1/a.jpg").apply { parentFile.mkdirs(); writeText("x"); setLastModified(now - 2 * 24 * 3_600_000) }
        val fresh = java.io.File(fs.temporaryDirectory, "camera/b.jpg").apply { parentFile.mkdirs(); writeText("y"); setLastModified(now - 60_000) }
        val doc = java.io.File(fs.documentsDirectory, "keep.txt").apply { writeText("z"); setLastModified(0) }

        fs.purgeTemporary(olderThanMillis = 24 * 3_600_000L, now = now)

        kotlin.test.assertFalse(old.exists())
        kotlin.test.assertFalse(old.parentFile.exists(), "emptied folders go too")
        kotlin.test.assertTrue(fresh.exists())
        kotlin.test.assertTrue(doc.exists(), "only tmp is purged")
        kotlin.test.assertTrue(java.io.File(fs.temporaryDirectory).isDirectory)
    }
}
