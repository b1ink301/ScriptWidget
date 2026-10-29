package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.domain.fake.FakeScriptDocuments
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.model.ImportOutcome
import kr.b1ink.scriptwidget.domain.model.ScriptFile
import kr.b1ink.scriptwidget.domain.model.ScriptName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScriptTransferTest {
    private val scripts = FakeScriptRepository(mapOf("weather" to "old"))

    private suspend fun import(vararg files: Pair<String, ScriptFile>): List<ImportOutcome> {
        val docs = FakeScriptDocuments(*files)
        return ImportScriptsUseCase(scripts, docs)(files.map { it.first })
    }

    // --- import ---

    @Test
    fun `a js file becomes a script named after the file`() = runTest {
        val result = import("u1" to ScriptFile("clock.js", "Script.complete()"))

        assertEquals(listOf(ImportOutcome.Imported("clock")), result)
        assertEquals("Script.complete()", scripts.files["clock"])
    }

    @Test
    fun `an existing name is never overwritten`() = runTest {
        val result = import(
            "u1" to ScriptFile("weather.js", "new"),
            "u2" to ScriptFile("weather.js", "newer"),
        )

        assertEquals(listOf(ImportOutcome.Imported("weather 2"), ImportOutcome.Imported("weather 3")), result)
        assertEquals("old", scripts.files["weather"])
        assertEquals("newer", scripts.files["weather 3"])
    }

    @Test
    fun `scriptable export files are unpacked`() = runTest {
        val scriptable = """{"always_run_in_app":false,"icon":{"color":"blue","glyph":"cloud"},"name":"Rain Radar","script":"log(1)\n","share_sheet_inputs":[]}"""

        val result = import("u1" to ScriptFile("Rain Radar.scriptable", scriptable))

        assertEquals(listOf(ImportOutcome.Imported("Rain Radar")), result)
        // The code comes out unchanged, below the icon header taken from the export.
        assertTrue(scripts.files["Rain Radar"]!!.endsWith("\nlog(1)\n"))
    }

    @Test
    fun `the icon of a scriptable export becomes the script's icon header`() = runTest {
        val scriptable = """{"always_run_in_app":false,"icon":{"color":"deep-purple","glyph":"credit-card"},"name":"sub-calendar","script":"log(1)\n","share_sheet_inputs":[]}"""

        import("u1" to ScriptFile("sub-calendar.scriptable", scriptable))

        val code = scripts.files["sub-calendar"]!!
        assertEquals(kr.b1ink.scriptwidget.domain.model.ScriptAppearance("deep-purple", "credit-card"), kr.b1ink.scriptwidget.domain.model.ScriptAppearance.parse(code))
        assertTrue(code.endsWith("log(1)\n"))
    }

    @Test
    fun `an icon header already in the script is kept`() = runTest {
        val body = "// icon-color: red; icon-glyph: bolt;\\nlog(1)"
        val scriptable = """{"icon":{"color":"blue","glyph":"cloud"},"name":"k","script":"$body"}"""

        import("u1" to ScriptFile("k.scriptable", scriptable))

        assertEquals(kr.b1ink.scriptwidget.domain.model.ScriptAppearance("red", "bolt"), kr.b1ink.scriptwidget.domain.model.ScriptAppearance.parse(scripts.files["k"]!!))
    }

    @Test
    fun `unsafe file names are turned into valid script names`() = runTest {
        val result = import(
            "u1" to ScriptFile("../.hidden/evil.js", "a"),
            "u2" to ScriptFile("   .js", "b"),
            "u3" to ScriptFile("x".repeat(100) + ".js", "c"),
        )

        val names = result.map { (it as ImportOutcome.Imported).name }
        assertTrue(names.all(ScriptName::isValid), names.toString())
        assertEquals("imported", names[1])
        assertEquals(ScriptName.MAX_LENGTH, names[2].length)
    }

    @Test
    fun `binary, empty and unreadable files are rejected without stopping the others`() = runTest {
        val docs = FakeScriptDocuments(
            "png" to ScriptFile("photo.png", "\u0089PNG\u0000\u0000"),
            "empty" to ScriptFile("empty.js", "  \n"),
            "ok" to ScriptFile("ok.js", "1"),
        )

        val result = ImportScriptsUseCase(scripts, docs)(listOf("png", "empty", "missing", "ok"))

        assertEquals(
            listOf(
                ImportOutcome.Rejected("photo.png", ImportOutcome.Reason.NotAScript),
                ImportOutcome.Rejected("empty.js", ImportOutcome.Reason.NotAScript),
                ImportOutcome.Rejected("missing", ImportOutcome.Reason.Unreadable),
                ImportOutcome.Imported("ok"),
            ),
            result,
        )
    }

    // --- export ---

    @Test
    fun `export file name keeps the script name`() {
        assertEquals("Rain Radar.js", ExportScriptUseCase.fileNameFor("Rain Radar"))
    }

    @Test
    fun `export writes the script content to the chosen document`() = runTest {
        val docs = FakeScriptDocuments()

        assertTrue(ExportScriptUseCase(scripts, docs)("weather", "content://out"))
        assertEquals("old", docs.written["content://out"])
    }

    @Test
    fun `exporting a missing script writes nothing`() = runTest {
        val docs = FakeScriptDocuments()

        assertFalse(ExportScriptUseCase(scripts, docs)("nope", "content://out"))
        assertTrue(docs.written.isEmpty())
    }
}
