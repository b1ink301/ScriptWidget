package kr.b1ink.scriptwidget.data.android

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kr.b1ink.scriptwidget.data.files.SandboxedFileSystem
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidDocumentStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val root = File(context.cacheDir, "doc-store-test")
    private val files = SandboxedFileSystem(File(root, "sandbox"))
    private val store = AndroidDocumentStore(context, files)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun importsDocumentsIntoTheSandbox() {
        val source = File(root, "outside/notes.txt").apply { parentFile!!.mkdirs(); writeText("hello") }

        val path = store.importToSandbox(Uri.fromFile(source).toString())

        assertTrue(path.startsWith(files.temporaryDirectory), path)
        assertTrue(path.endsWith("notes.txt"), path)
        assertEquals("hello", files.readString(path))
    }

    @Test
    fun writesExportedBytes() {
        val target = File(root, "outside/out.json").apply { parentFile!!.mkdirs(); writeText("old content that is longer") }

        store.write(Uri.fromFile(target).toString(), "{}".toByteArray())

        assertEquals("{}", target.readText())
    }
}
