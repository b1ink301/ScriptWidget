package kr.b1ink.scriptwidget.data.backup

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ZipScriptArchiveCodecTest {
    private val codec = ZipScriptArchiveCodec()

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() } }
    }.toByteArray()

    @Test
    fun `round trip keeps names and UTF-8 text`() {
        val files = mapOf("날씨.js" to "log(\"안녕\")", "b.js" to "")

        assertEquals(files, codec.decode(codec.encode(files)))
    }

    @Test
    fun `folders are flattened so entries can't escape the backup`() {
        val bytes = zip("scripts/a.js" to "A".toByteArray(), "../../evil.js" to "E".toByteArray(), "dir/" to ByteArray(0))

        assertEquals(mapOf("a.js" to "A", "evil.js" to "E"), codec.decode(bytes))
    }

    @Test
    fun `not a zip file`() {
        assertFailsWith<java.io.IOException> { codec.decode("hello".toByteArray()) }
    }

    @Test
    fun `oversized entries are refused`() {
        val bytes = zip("big.js" to ByteArray(ZipScriptArchiveCodec.MAX_ENTRY_BYTES + 1) { 'a'.code.toByte() })

        assertFailsWith<java.io.IOException> { codec.decode(bytes) }
    }
}
