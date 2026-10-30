package kr.b1ink.scriptwidget.data.files

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FontStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `fonts are files in the fonts folder, listed by name`() = runTest {
        val dir = tmp.root.resolve("Documents/fonts")
        val store = FontStore(dir)
        assertEquals(emptyList(), store.fonts.first())

        store.save("Pixel.ttf", byteArrayOf(1))
        store.save("Arial Rounded.otf", byteArrayOf(2))
        dir.resolve("notes.txt").writeText("x")

        assertEquals(listOf("Arial Rounded", "Pixel"), store.fonts.first())
        assertEquals(listOf<Byte>(1), dir.resolve("Pixel.ttf").readBytes().toList())
    }

    @Test
    fun `file names cannot leave the folder`() = runTest {
        assertFailsWith<IllegalArgumentException> { FontStore(tmp.root).save("../x.ttf", byteArrayOf(1)) }
    }
}
