package kr.b1ink.scriptwidget.data.engine

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import kr.b1ink.scriptwidget.domain.model.ImageNode
import kr.b1ink.scriptwidget.domain.model.ScriptEnvironment
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Scriptable's FileManager and Keychain APIs on real QuickJS. */
class FileManagerKeychainContractTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val images = FakeImageStore()
    private val sandbox by lazy { tmp.newFolder("sandbox") }
    private val files by lazy { SandboxedFileSystem(sandbox) }
    private val secrets = InMemorySecretStore()
    private val engine by lazy {
        QuickJsScriptEngine(Prelude.load(), fakeServices(images = images, files = files, secrets = secrets), timeoutMillis = 2_000)
    }

    private suspend fun run(script: String) = engine.execute(script, ScriptEnvironment(runsInWidget = false), "fm")
    private suspend fun logOf(script: String): String {
        val result = run(script)
        assertNull(result.error, result.error)
        return result.logs.joinToString("\n") { it.message }
    }

    @Test
    fun `writes, reads and lists files`() = runTest {
        val out = logOf(
            """
            const fm = FileManager.local();
            const dir = fm.joinPath(fm.documentsDirectory(), "cache-data");
            fm.createDirectory(dir);
            const file = fm.joinPath(dir, "last.json");
            fm.writeString(file, JSON.stringify({v: 1}));
            console.log(fm.fileExists(file), fm.isDirectory(dir), JSON.parse(fm.readString(file)).v);
            console.log(fm.listContents(dir).join(","), fm.fileName(file), fm.fileName(file, true), fm.fileExtension(file));
            console.log(fm.modificationDate(file) instanceof Date);
            fm.remove(file);
            console.log(fm.fileExists(file), fm.readString(file));
            """
        )
        assertEquals("true true 1\nlast.json last last.json json\ntrue\nfalse null", out)
    }

    // --- file bookmarks ---

    @Test
    fun `bookmarked files are found by name`() = runTest {
        files // creates the sandbox folders
        java.io.File(sandbox, "Bookmarks").mkdirs()
        java.io.File(sandbox, "Bookmarks/still").writeBytes(byteArrayOf(1, 2, 3))

        val out = logOf(
            """
            const fm = FileManager.iCloud();
            console.log(fm.bookmarkExists("still"), fm.bookmarkExists("nope"));
            console.log(JSON.stringify(fm.allFileBookmarks()));
            const img = fm.readImage(fm.bookmarkedPath("still"));
            console.log(img instanceof Image);
            """
        )

        assertEquals("true false\n[{\"name\":\"still\",\"source\":\"host\"}]\ntrue", out)
    }

    @Test
    fun `a missing bookmark explains where to add it`() = runTest {
        val result = run("""FileManager.local().bookmarkedPath("still");""")

        val error = assertNotNull(result.error)
        assertContains(error, "still")
        assertContains(error, "파일 북마크")
    }

    @Test
    fun `documents persist between runs`() = runTest {
        logOf("""const fm = FileManager.local(); fm.writeString(fm.joinPath(fm.documentsDirectory(), "count.txt"), "41");""")

        val out = logOf("""const fm = FileManager.iCloud(); console.log(Number(fm.readString(fm.joinPath(fm.documentsDirectory(), "count.txt"))) + 1);""")

        assertEquals("42", out)
    }

    @Test
    fun `directories are distinct and inside the sandbox`() = runTest {
        val out = logOf(
            """
            const fm = FileManager.local();
            console.log([fm.documentsDirectory(), fm.cacheDirectory(), fm.temporaryDirectory()].join("|"));
            """
        )
        val dirs = out.split("|")
        assertEquals(listOf(files.documentsDirectory, files.cacheDirectory, files.temporaryDirectory), dirs)
    }

    @Test
    fun `escaping the sandbox throws a catchable error`() = runTest {
        val out = logOf("""try { FileManager.local().readString("/etc/hosts"); } catch (e) { console.log("blocked:", e.message); }""")
        assertContains(out, "blocked:")
        assertContains(out, "sandbox")
    }

    @Test
    fun `images can be written to and read from files`() = runTest {
        val result = run(
            """
            const fm = FileManager.local();
            const path = fm.joinPath(fm.documentsDirectory(), "logo.png");
            const downloaded = await new Request("https://img").loadImage();
            fm.writeImage(path, downloaded);
            const img = fm.readImage(path);
            const w = new ListWidget();
            w.addImage(img);
            console.log(fm.readImage(fm.joinPath(fm.documentsDirectory(), "missing.png")));
            Script.setWidget(w);
            """
        )
        assertNull(result.error, result.error)
        assertContentEquals(FakeImageStore.PNG_BYTES, files.readBytes("${files.documentsDirectory}/logo.png"))
        assertEquals(2, images.stored.size)
        assertEquals(images.stored[1].size, FakeImageStore.PNG_BYTES.size)
        assertEquals("/cache/${images.stored[1].runKey}/img_1.png", (result.widget!!.children.single() as ImageNode).src)
        assertEquals("null", result.logs.single().message)
    }

    @Test
    fun `keychain stores secrets across runs`() = runTest {
        logOf("""Keychain.set("apiKey", "s3cr3t");""")

        val out = logOf(
            """
            console.log(Keychain.contains("apiKey"), Keychain.get("apiKey"), Keychain.contains("other"));
            Keychain.remove("apiKey");
            console.log(Keychain.contains("apiKey"));
            """
        )
        assertEquals("true s3cr3t false\nfalse", out)
        assertEquals(emptyMap(), secrets.values)
    }

    @Test
    fun `keychain get of a missing key throws like Scriptable`() = runTest {
        assertContains(assertNotNull(run("""Keychain.get("missing");""").error), "missing")
    }

    @Test
    fun `keychain only stores strings`() = runTest {
        logOf("""Keychain.set("n", 42);""")
        assertEquals("42", secrets.values["n"])
    }
}
