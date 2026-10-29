package kr.b1ink.scriptwidget.data.android

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class AndroidScriptDocumentsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dir = File(context.filesDir, "script_storage/transfer-test").apply { mkdirs() }
    private val documents = AndroidScriptDocuments(context)

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun uriOf(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.scriptfiles", file).toString()

    @Test
    fun readsDisplayNameAndUtf8Text() = runBlocking {
        val file = File(dir, "날씨 위젯.js").apply { writeText("log(\"안녕\")") }

        val read = documents.read(uriOf(file))

        assertEquals("날씨 위젯.js", read.fileName)
        assertEquals("log(\"안녕\")", read.content)
    }

    @Test
    fun overwritesTheChosenDocument() = runBlocking {
        val file = File(dir, "out.js").apply { writeText("a much longer previous content") }

        documents.write(uriOf(file), "short")

        assertEquals("short", file.readText())
    }

    @Test
    fun refusesHugeFiles() {
        val file = File(dir, "huge.js").apply { writeBytes(ByteArray(AndroidScriptDocuments.MAX_BYTES + 1) { 'a'.code.toByte() }) }

        assertFailsWith<java.io.IOException> { runBlocking { documents.read(uriOf(file)) } }
    }
}
