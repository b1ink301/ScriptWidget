package kr.b1ink.scriptwidget.data.module

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileModuleSourceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `loads scripts by name and rejects path tricks`() {
        val dir = tmp.newFolder("scripts")
        File(dir, "lib.js").writeText("module.exports = 1;")
        tmp.newFile("outside.js").writeText("secret")
        val source = FileModuleSource(dir)

        assertEquals("module.exports = 1;", source.load("lib"))
        assertNull(source.load("missing"))
        assertNull(source.load("../outside"))
        assertNull(source.load(""))
    }
}
