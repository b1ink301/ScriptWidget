package kr.b1ink.scriptwidget.domain.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kr.b1ink.scriptwidget.domain.fake.FakeArchiveCodec
import kr.b1ink.scriptwidget.domain.fake.FakeScriptDocuments
import kr.b1ink.scriptwidget.domain.fake.FakeScriptRepository
import kr.b1ink.scriptwidget.domain.model.Catalog
import kr.b1ink.scriptwidget.domain.model.CatalogEntry
import kr.b1ink.scriptwidget.domain.model.CatalogValidator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExportForGalleryUseCaseTest {
    private val code = "await Location.current();\nconst r = await new Request(u).loadJSON();\nScript.setWidget(w);"
    private val scripts = FakeScriptRepository(mapOf("Morning card" to code, "아침 카드" to "Script.complete()"))
    private val codec = FakeArchiveCodec()
    private val documents = FakeScriptDocuments()
    private val export = ExportForGalleryUseCase(scripts, codec, documents)

    @Test
    fun `a script is packed with a ready catalog entry that passes the checks`() = runTest {
        assertTrue(export("Morning card", "content://out"))

        val files = codec.decode(documents.bytes["content://out"]!!)
        assertEquals(setOf("morning_card.js", "entry.json"), files.keys)
        assertEquals(code, files["morning_card.js"])
        val entry = Json.decodeFromString(CatalogEntry.serializer(), files.getValue("entry.json"))
        assertEquals(
            CatalogEntry(
                id = "morning_card", name = "Morning card", title = "Morning card", category = entry.category, description = entry.description,
                script = "morning_card.js", sizes = listOf("small", "medium", "large"), permissions = listOf("location", "network"),
            ),
            entry,
        )
        assertEquals(emptyList(), CatalogValidator.validate(Catalog("t", listOf(entry))) { code }.filterIsInstance<kr.b1ink.scriptwidget.domain.model.CatalogProblem.Error>())
    }

    @Test
    fun `names without latin letters still get a file-safe id`() = runTest {
        export("아침 카드", "content://out")

        val names = codec.decode(documents.bytes["content://out"]!!).keys
        assertTrue(names.single { it != "entry.json" }.matches(Regex("design_[0-9a-f]{6}\\.js")), names.toString())
        assertEquals(ExportForGalleryUseCase.fileName("아침 카드").removeSuffix("-gallery.zip") + ".js", names.single { it != "entry.json" })
    }

    @Test
    fun `a missing script exports nothing`() = runTest {
        assertFalse(export("gone", "content://out"))
        assertEquals(null, documents.bytes["content://out"])
    }
}
